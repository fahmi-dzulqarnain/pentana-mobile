# Shared LunchStore Pilot — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the Lunch screen's presentation logic into a shared `LunchStore` in `commonMain`, consumed by Compose (Android) and SwiftUI via SKIE (iOS), so the load/action/error/derived logic lives — and is tested — once.

**Architecture:** A plain-Kotlin `LunchStore` owns a coroutine scope and exposes `StateFlow` state + intent functions; derived display rules are shared pure functions. Android's `LunchViewModel` shrinks to a wrapper; iOS's `LunchView` observes the flow through SKIE. Only the Lunch screen changes.

**Tech Stack:** Kotlin 2.1.21, kotlinx-coroutines (StateFlow), Ktor MockEngine (tests), SKIE 0.10.x (Kotlin↔Swift interop), Compose, SwiftUI.

**Spec:** `docs/superpowers/specs/2026-07-08-shared-lunch-store-pilot-design.md`

---

## Task 1: Shared LunchStore + derived logic (+ tests, TDD)

**Files:**
- Create: `shared/src/commonMain/kotlin/my/silentmode/pentana/shared/presentation/LunchStore.kt`
- Create: `shared/src/commonMain/kotlin/my/silentmode/pentana/shared/presentation/LunchDisplay.kt`
- Test: `shared/src/commonTest/kotlin/my/silentmode/pentana/shared/LunchStoreTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package my.silentmode.pentana.shared

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.fullPath
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import my.silentmode.pentana.shared.model.LunchDto
import my.silentmode.pentana.shared.model.LunchOptionDto
import my.silentmode.pentana.shared.presentation.LunchStatus
import my.silentmode.pentana.shared.presentation.LunchStore
import my.silentmode.pentana.shared.presentation.LunchUiState
import my.silentmode.pentana.shared.presentation.lunchClosedSummary
import my.silentmode.pentana.shared.presentation.lunchStatus
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LunchStoreTest {
    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private val openLunchJson = """
        {"data":[{"id":1,"date":"2026-07-13","caterer":"Dapur Selera","menu":"Nasi Lemak Royale",
        "deadline":"2026-07-12T18:00:00+08:00","is_open":true,
        "options":[{"meal_option_id":10,"name":"Chicken"},{"meal_option_id":11,"name":"Beef"}],
        "responded":false,"my_meal_option_id":null}]}
    """.trimIndent()

    private val chosenLunchJson = """
        {"data":{"id":1,"date":"2026-07-13","caterer":"Dapur Selera","menu":"Nasi Lemak Royale",
        "deadline":"2026-07-12T18:00:00+08:00","is_open":true,
        "options":[{"meal_option_id":10,"name":"Chicken"},{"meal_option_id":11,"name":"Beef"}],
        "responded":true,"my_meal_option_id":10}}
    """.trimIndent()

    private fun store(handler: (String) -> Pair<HttpStatusCode, String>): LunchStore {
        val engine = MockEngine { request ->
            val (status, body) = handler(request.url.fullPath)
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return LunchStore(LunchRepository(ApiClient("https://x/api/v1", InMemoryTokenStore("t"), engine)))
    }

    @Test fun load_emits_content() = runTest {
        val s = store { HttpStatusCode.OK to openLunchJson }
        val state = s.state.value
        assertIs<LunchUiState.Content>(state)
        assertEquals(1, state.lunches.size)
        assertEquals("Nasi Lemak Royale", state.lunches.first().menu)
    }

    @Test fun choose_updates_the_lunch() = runTest {
        val s = store { path -> if (path.endsWith("/respond")) HttpStatusCode.OK to chosenLunchJson else HttpStatusCode.OK to openLunchJson }
        s.choose(lunchId = 1, mealOptionId = 10)
        val state = s.state.value
        assertIs<LunchUiState.Content>(state)
        assertTrue(state.lunches.first().responded)
        assertEquals(10L, state.lunches.first().myMealOptionId)
    }

    @Test fun error_emits_error() = runTest {
        val s = store { HttpStatusCode.InternalServerError to "{}" }
        assertIs<LunchUiState.Error>(s.state.value)
    }

    @Test fun status_is_vote_now_when_open_and_not_responded() {
        val lunch = lunch(isOpen = true, responded = false)
        assertEquals(LunchStatus.VoteNow, lunchStatus(lunch))
    }

    @Test fun closed_summary_names_the_ordered_option() {
        val lunch = lunch(isOpen = false, responded = true, myMealOptionId = 11)
        assertEquals("Ordering closed — you ordered Beef.", lunchClosedSummary(lunch))
    }

    private fun lunch(isOpen: Boolean, responded: Boolean, myMealOptionId: Long? = null) = LunchDto(
        id = 1, date = "2026-07-13", caterer = "Dapur Selera", menu = "Nasi Lemak Royale",
        deadline = null, isOpen = isOpen,
        options = listOf(LunchOptionDto(10, "Chicken"), LunchOptionDto(11, "Beef")),
        responded = responded, myMealOptionId = myMealOptionId,
    )
}
```

- [ ] **Step 2: Run — expect FAIL** (unresolved `LunchStore`/`LunchUiState`/`lunchStatus`).

Run: `./gradlew :shared:allTests`
Expected: compile failure, unresolved references in `presentation`.

- [ ] **Step 3: Implement `LunchDisplay.kt`**

```kotlin
package my.silentmode.pentana.shared.presentation

import my.silentmode.pentana.shared.model.LunchDto

/** Shared decision — each platform maps this to its own chip styling. */
enum class LunchStatus { VoteNow, Responded, Closed }

fun lunchStatus(lunch: LunchDto): LunchStatus = when {
    lunch.isOpen && !lunch.responded -> LunchStatus.VoteNow
    lunch.isOpen && lunch.responded -> LunchStatus.Responded
    else -> LunchStatus.Closed
}

fun lunchClosedSummary(lunch: LunchDto): String {
    if (!lunch.responded) return "Ordering closed — no order placed."
    if (lunch.myMealOptionId == null) return "Ordering closed — you marked not attending."
    val name = lunch.options.firstOrNull { it.mealOptionId == lunch.myMealOptionId }?.name
    return if (name != null) "Ordering closed — you ordered $name." else "Ordering closed — order placed."
}
```

- [ ] **Step 4: Implement `LunchStore.kt`**

```kotlin
package my.silentmode.pentana.shared.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import my.silentmode.pentana.shared.LunchRepository
import my.silentmode.pentana.shared.model.LunchDto

sealed interface LunchUiState {
    data object Loading : LunchUiState
    data class Error(val message: String) : LunchUiState
    data class Content(val lunches: List<LunchDto>) : LunchUiState
}

/**
 * Shared presentation logic for the Lunch screen. Owns its coroutine scope; each platform
 * calls [clear] on teardown (Android onCleared, iOS onDisappear).
 */
class LunchStore(private val repo: LunchRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow<LunchUiState>(LunchUiState.Loading)
    val state: StateFlow<LunchUiState> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init { load() }

    fun load() {
        scope.launch {
            _state.value = LunchUiState.Loading
            fetch()
        }
    }

    fun refresh() {
        scope.launch {
            _refreshing.value = true
            fetch()
            _refreshing.value = false
        }
    }

    private suspend fun fetch() {
        _state.value = try {
            LunchUiState.Content(repo.lunches())
        } catch (_: Exception) {
            LunchUiState.Error("Something went wrong. Pull down to refresh.")
        }
    }

    fun choose(lunchId: Long, mealOptionId: Long) {
        scope.launch { runCatching { replace(repo.chooseOption(lunchId, mealOptionId)) } }
    }

    fun notAttending(lunchId: Long) {
        scope.launch { runCatching { replace(repo.markNotAttending(lunchId)) } }
    }

    private fun replace(updated: LunchDto) {
        val current = (_state.value as? LunchUiState.Content)?.lunches ?: return
        _state.value = LunchUiState.Content(current.map { if (it.id == updated.id) updated else it })
    }

    fun clear() { scope.cancel() }
}
```

- [ ] **Step 5: Run — expect PASS**

Run: `./gradlew :shared:allTests`
Expected: all tests pass (the `init { load() }` runs under the `UnconfinedTestDispatcher` Main).

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/my/silentmode/pentana/shared/presentation shared/src/commonTest
git commit -m "feat(shared): LunchStore + derived lunch display logic (pilot) + tests"
```

---

## Task 2: Add SKIE + verify the iOS framework still links

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `shared/build.gradle.kts`

- [ ] **Step 1: Version catalog** — under `[versions]` add `skie = "0.10.13"`; under `[plugins]` add:
```toml
skie = { id = "co.touchlab.skie", version.ref = "skie" }
```

- [ ] **Step 2: Apply SKIE to `:shared`** — add to the `plugins { }` block in `shared/build.gradle.kts`:
```kotlin
alias(libs.plugins.skie)
```

- [ ] **Step 3: Link the iOS framework to validate SKIE against Kotlin 2.1.21**

Run: `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`
Expected: `BUILD SUCCESSFUL`, `Shared.framework` produced.
**If it fails on a Kotlin/SKIE version mismatch:** bump `skie` to the release whose compatibility matrix lists Kotlin 2.1.21 (SKIE is a compiler plugin; the error names the supported Kotlin version), then re-run.

- [ ] **Step 4: Confirm shared tests still green**

Run: `./gradlew :shared:allTests`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml shared/build.gradle.kts
git commit -m "build(shared): add SKIE for Kotlin↔Swift interop (Flows, sealed enums)"
```

---

## Task 3: Rewire Android Lunch to the shared store

**Files:**
- Modify: `androidApp/src/main/kotlin/my/silentmode/pentana/feature/lunch/LunchViewModel.kt`
- Modify: `androidApp/src/main/kotlin/my/silentmode/pentana/feature/lunch/LunchScreen.kt`

- [ ] **Step 1: Slim `LunchViewModel.kt` to wrap the shared store**

Replace the whole file with:
```kotlin
package my.silentmode.pentana.feature.lunch

import androidx.lifecycle.ViewModel
import my.silentmode.pentana.shared.LunchRepository
import my.silentmode.pentana.shared.presentation.LunchStore

class LunchViewModel(repo: LunchRepository) : ViewModel() {
    val store = LunchStore(repo)
    override fun onCleared() { store.clear() }
}
```
(The old `LunchUiState` sealed interface and `refreshing` state are gone — they now come from the shared store.)

- [ ] **Step 2: Update `LunchScreen.kt`** — consume the store, map the shared status → `ChipKind`, delete the duplicated logic.
  - Change imports: remove the local `LunchUiState`; add
    `import my.silentmode.pentana.shared.presentation.LunchUiState`,
    `import my.silentmode.pentana.shared.presentation.LunchStatus`,
    `import my.silentmode.pentana.shared.presentation.lunchStatus`,
    `import my.silentmode.pentana.shared.presentation.lunchClosedSummary`.
  - The composable body:
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LunchScreen() {
    val vm = appViewModel { LunchViewModel(it.lunch) }
    val state by vm.store.state.collectAsStateWithLifecycle()
    val refreshing by vm.store.refreshing.collectAsStateWithLifecycle()
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm.store::refresh, modifier = Modifier.fillMaxSize()) {
        when (val s = state) {
            is LunchUiState.Loading -> LoadingState()
            is LunchUiState.Error -> ErrorState(s.message, vm.store::load)
            is LunchUiState.Content -> if (s.lunches.isEmpty()) LunchEmpty() else LunchList(s.lunches, vm.store)
        }
    }
}
```
  - `LunchList(lunches, store)` calls `store.choose(lunch.id, optionId)` / `store.notAttending(lunch.id)`.
  - Replace the local `lunchChip(lunch)` with a `LunchStatus → ChipKind` map, and call `lunchClosedSummary(lunch)` from shared:
```kotlin
private fun chipKind(status: LunchStatus): ChipKind = when (status) {
    LunchStatus.VoteNow -> ChipKind.VoteNow
    LunchStatus.Responded -> ChipKind.Responded
    LunchStatus.Closed -> ChipKind.Closed
}
// in LunchCard: StatusChip(chipKind(lunchStatus(lunch)))  and  Text(lunchClosedSummary(lunch))
```
  - **Delete** the old private `lunchChip(...)` and `closedSummary(...)` from this file.

- [ ] **Step 3: Build**

Run: `./gradlew :androidApp:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add androidApp/src/main/kotlin/my/silentmode/pentana/feature/lunch
git commit -m "refactor(android): Lunch screen consumes shared LunchStore"
```

---

## Task 4: Rewire iOS Lunch to the shared store via SKIE

**Files:**
- Modify: `iosApp/iosApp/SessionStore.swift`
- Modify: `iosApp/iosApp/LunchView.swift`

- [ ] **Step 1: Vend the store from `SessionStore`** — add:
```swift
func makeLunchStore() -> LunchStore { LunchStore(repo: lunch) }
```

- [ ] **Step 2: Rewrite `LunchView` to observe the store via SKIE**

```swift
struct LunchView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var store: LunchStore?
    @State private var state: LunchUiState = LunchUiStateLoading.shared
    @State private var refreshing = false

    var body: some View {
        content
            .task {
                let s = store ?? session.makeLunchStore()
                store = s
                Task { for await value in s.state { state = value } }
                Task { for await r in s.refreshing { refreshing = r.boolValue } }
            }
            .onDisappear { store?.clear() }
    }

    @ViewBuilder private var content: some View {
        switch onEnum(of: state) {
        case .loading:
            ProgressView()
        case .error(let e):
            // existing error/empty treatment, retry -> store?.load()
            ErrorStateView(message: e.message) { store?.load() }
        case .content(let c):
            List(c.lunches, id: \.id) { lunch in
                LunchCard(
                    lunch: lunch,
                    choose: { opt in store?.choose(lunchId: lunch.id, mealOptionId: opt) },
                    notAttending: { store?.notAttending(lunchId: lunch.id) }
                )
            }
            .refreshable { store?.refresh() }
        }
    }
}
```
  - `LunchCard` uses the shared helpers: `lunchStatus(lunch:)` → its SwiftUI chip, and `lunchClosedSummary(lunch:)` for the closed text.
  - **Delete** `LunchView`'s `@State lunches/isLoading`, `load()`, `update()`, and the local status/summary code.

> SKIE notes: `StateFlow` becomes an `AsyncSequence` (`for await … in s.state`) with `.value` for the current value; the sealed `LunchUiState` becomes a Swift enum consumed via `onEnum(of:)`; non-suspend intents (`load`/`refresh`/`choose`) are plain synchronous calls. `KotlinBoolean` from the `refreshing` flow uses `.boolValue`.

- [ ] **Step 3: Build the iOS app**

Run: `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 16' build`
Expected: `BUILD SUCCEEDED` (SKIE generates the Swift interop during the shared framework build).

- [ ] **Step 4: Commit**

```bash
git add iosApp/iosApp/SessionStore.swift iosApp/iosApp/LunchView.swift
git commit -m "refactor(ios): Lunch screen consumes shared LunchStore via SKIE"
```

---

## Task 5: Full verification + push

- [ ] **Step 1: Everything green**

Run:
```bash
./gradlew :shared:allTests :androidApp:assembleDebug
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 16' build
```
Expected: shared tests pass; both apps build.

- [ ] **Step 2: On-device eyeball (both platforms)** — Lunch loads, voting updates the chip, closed lunch shows the summary, pull-to-refresh works — identical behaviour to before.

- [ ] **Step 3: Push**

```bash
git push origin main
```

---

## Self-review notes
- **Spec coverage:** shared `LunchStore` + `LunchUiState` (T1) · shared `LunchStatus`/`lunchStatus`/`lunchClosedSummary` (T1) · shared tests (T1) · SKIE + framework link de-risk (T2) · Android thin VM + screen rewire, dup logic deleted (T3) · iOS SKIE consumption + `makeLunchStore`, dup logic deleted (T4) · full verify + push (T5). All spec sections covered.
- **Types** taken verbatim from `:shared`: `LunchRepository.lunches()/chooseOption()/markNotAttending()`, `LunchDto`/`LunchOptionDto` fields. No invented members.
- **Out of scope** (other screens, session) untouched — no tasks, by design.
