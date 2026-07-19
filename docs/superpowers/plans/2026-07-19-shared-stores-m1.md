# Shared Stores Milestone 1 — actionError + Home + Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the `actionError` surfacing pattern to `LunchStore` (both UIs wired), then extract shared `HomeStore` and `NotificationsStore` with derived display logic, consumed by Compose and SwiftUI via SKIE.

**Architecture:** Plain-Kotlin stores in `commonMain` owning `CoroutineScope(SupervisorJob() + Dispatchers.Main)`, exposing `StateFlow`s; derived display rules are pure top-level functions returning shared enums. Android wraps each store in a thin ViewModel (`clear()` in `onCleared()`); iOS holds stores in `@State`, reuses across reappearance, never clears on disappear, and collects flows as structured `async let` children under `.task`.

**Tech Stack:** Kotlin 2.1.21, kotlinx-coroutines StateFlow, Ktor MockEngine (tests), SKIE 0.10.13, Compose, SwiftUI.

**Spec:** `docs/superpowers/specs/2026-07-19-shared-presentation-rollout-design.md`
**Branch:** `feat/shared-stores-m1` (already created; spec committed on it)

**Build notes for every task:**
- Gradle and xcodebuild have no network inside the sandbox — run them with the sandbox disabled.
- iOS builds MUST use an arm64 simulator destination (`:shared` has no iosX64 target):
  `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build`
- NEVER stage `androidApp/src/main/kotlin/my/silentmode/pentana/core/AppConfig.kt` (uncommitted local LAN-IP edit). Always `git add` explicit paths.
- Do not push or merge — Fahmi merges locally after review.

---

## Task 1: `actionError` on LunchStore (shared, TDD)

**Files:**
- Modify: `shared/src/commonMain/kotlin/my/silentmode/pentana/shared/presentation/LunchStore.kt`
- Test: `shared/src/commonTest/kotlin/my/silentmode/pentana/shared/LunchStoreTest.kt`

- [x] **Step 1: Write the failing tests**

Add to `LunchStoreTest` (imports of `assertNotNull` from `kotlin.test` needed):

```kotlin
@Test fun choose_failure_sets_action_error() = runTest {
    val s = store { path -> if (path.endsWith("/respond")) HttpStatusCode.InternalServerError to "{}" else HttpStatusCode.OK to openLunchJson }
    s.state.first { it is LunchUiState.Content }
    assertNull(s.actionError.value)
    s.choose(lunchId = 1, mealOptionId = 10)
    s.inFlight.first { it.isEmpty() } // action settled
    assertEquals("Couldn't save your choice. Please try again.", s.actionError.value)
}

@Test fun dismiss_clears_action_error() = runTest {
    val s = store { path -> if (path.endsWith("/respond")) HttpStatusCode.InternalServerError to "{}" else HttpStatusCode.OK to openLunchJson }
    s.state.first { it is LunchUiState.Content }
    s.notAttending(lunchId = 1)
    s.inFlight.first { it.isEmpty() }
    assertNotNull(s.actionError.value)
    s.dismissActionError()
    assertNull(s.actionError.value)
}

@Test fun next_action_attempt_clears_previous_error() = runTest {
    var failNext = true
    val s = store { path ->
        if (path.endsWith("/respond")) {
            if (failNext) { failNext = false; HttpStatusCode.InternalServerError to "{}" } else HttpStatusCode.OK to chosenLunchJson
        } else HttpStatusCode.OK to openLunchJson
    }
    s.state.first { it is LunchUiState.Content }
    s.choose(lunchId = 1, mealOptionId = 10)
    s.inFlight.first { it.isEmpty() }
    assertNotNull(s.actionError.value)
    s.choose(lunchId = 1, mealOptionId = 10) // retry clears the stale error as it starts
    s.inFlight.first { it.isEmpty() }
    assertNull(s.actionError.value)
}

@Test fun successful_action_never_sets_error() = runTest {
    val s = store { path -> if (path.endsWith("/respond")) HttpStatusCode.OK to chosenLunchJson else HttpStatusCode.OK to openLunchJson }
    s.state.first { it is LunchUiState.Content }
    s.choose(lunchId = 1, mealOptionId = 10)
    s.inFlight.first { it.isEmpty() }
    assertNull(s.actionError.value)
}
```

- [x] **Step 2: Run — expect FAIL** (unresolved `actionError` / `dismissActionError`)

Run: `./gradlew :shared:allTests`
Expected: compile failure, unresolved reference `actionError`.

- [x] **Step 3: Implement in `LunchStore.kt`**

Add fields after the `inFlight` declaration:

```kotlin
/** Set when a fire-and-forget action fails; the UI shows it natively (Snackbar / alert). */
private val _actionError = MutableStateFlow<String?>(null)
val actionError: StateFlow<String?> = _actionError.asStateFlow()

fun dismissActionError() { _actionError.value = null }
```

Update `choose` and `notAttending` — clear the error after passing the guard (a guarded duplicate must not wipe an error the user is reading), set it on failure:

```kotlin
fun choose(lunchId: Long, mealOptionId: Long) {
    if (lunchId in _inFlight.value) return
    _inFlight.value = _inFlight.value + lunchId // synchronous check-and-set: no dispatch gap before the guard arms
    _actionError.value = null
    scope.launch {
        try {
            replace(repo.chooseOption(lunchId, mealOptionId))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _actionError.value = "Couldn't save your choice. Please try again."
        } finally {
            _inFlight.value = _inFlight.value - lunchId
        }
    }
}

fun notAttending(lunchId: Long) {
    if (lunchId in _inFlight.value) return
    _inFlight.value = _inFlight.value + lunchId // synchronous check-and-set: no dispatch gap before the guard arms
    _actionError.value = null
    scope.launch {
        try {
            replace(repo.markNotAttending(lunchId))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _actionError.value = "Couldn't save your choice. Please try again."
        } finally {
            _inFlight.value = _inFlight.value - lunchId
        }
    }
}
```

- [x] **Step 4: Run — expect PASS**

Run: `./gradlew :shared:allTests`
Expected: all tests pass (existing + 4 new).

- [x] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/my/silentmode/pentana/shared/presentation/LunchStore.kt shared/src/commonTest/kotlin/my/silentmode/pentana/shared/LunchStoreTest.kt
git commit -m "feat(shared): actionError surfacing on LunchStore fire-and-forget actions"
```

---

## Task 2: Android Lunch — Snackbar for actionError

**Files:**
- Modify: `androidApp/src/main/kotlin/my/silentmode/pentana/feature/lunch/LunchScreen.kt`

- [x] **Step 1: Wire the Snackbar**

In `LunchScreen()`, collect the error and host a Snackbar inside the `PullToRefreshBox` (its content is a `BoxScope`). New imports:

```kotlin
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
```

Replace the `LunchScreen` composable body:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LunchScreen() {
    val vm = appViewModel { LunchViewModel(it.lunch) }
    val state by vm.store.state.collectAsStateWithLifecycle()
    val refreshing by vm.store.refreshing.collectAsStateWithLifecycle()
    val inFlight by vm.store.inFlight.collectAsStateWithLifecycle()
    val actionError by vm.store.actionError.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(actionError) {
        actionError?.let { message ->
            snackbarHostState.showSnackbar(message)
            vm.store.dismissActionError()
        }
    }
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm.store::refresh, modifier = Modifier.fillMaxSize()) {
        when (val s = state) {
            is LunchUiState.Loading -> LoadingState()
            is LunchUiState.Error -> ErrorState(s.message, vm.store::load)
            is LunchUiState.Content -> if (s.lunches.isEmpty()) LunchEmpty() else LunchList(s.lunches, inFlight, vm.store)
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}
```

(`Alignment` is already imported in this file.)

- [x] **Step 2: Build**

Run: `./gradlew :androidApp:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [x] **Step 3: Commit**

```bash
git add androidApp/src/main/kotlin/my/silentmode/pentana/feature/lunch/LunchScreen.kt
git commit -m "feat(android): Snackbar for shared Lunch actionError"
```

---

## Task 3: iOS Lunch — alert for actionError

**Files:**
- Modify: `iosApp/iosApp/LunchView.swift`

- [x] **Step 1: Collect the flow + present an alert**

In `LunchView`, add state:

```swift
@State private var actionError: String?
```

Add a fourth structured child to the `.task` block and include it in the awaited tuple (SKIE surfaces the nullable `StateFlow<String?>` as an AsyncSequence of `String?`):

```swift
async let errors: Void = { for await e in s.actionError { await MainActor.run { actionError = e } } }()
_ = await (states, refreshes, flights, errors)
```

Attach the alert to `content` (after `.task`):

```swift
.alert(
    "Something went wrong",
    isPresented: Binding(get: { actionError != nil }, set: { if !$0 { actionError = nil; store?.dismissActionError() } })
) {
    Button("OK", role: .cancel) {}
} message: {
    Text(actionError ?? "")
}
```

The setter clears the local state synchronously — waiting for the store's nil to round-trip
Kotlin → SKIE → MainActor leaves a window where a body re-evaluation re-presents the alert.
The message copy is shared (from the store); only the alert chrome/title is native.

- [x] **Step 2: Build**

Run: `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build`
Expected: `BUILD SUCCEEDED`.

- [x] **Step 3: Commit**

```bash
git add iosApp/iosApp/LunchView.swift
git commit -m "feat(ios): alert for shared Lunch actionError"
```

---

## Task 4: Shared HomeStore + HomeDisplay (TDD)

**Files:**
- Create: `shared/src/commonMain/kotlin/my/silentmode/pentana/shared/presentation/HomeStore.kt`
- Create: `shared/src/commonMain/kotlin/my/silentmode/pentana/shared/presentation/HomeDisplay.kt`
- Test: `shared/src/commonTest/kotlin/my/silentmode/pentana/shared/HomeStoreTest.kt`

- [x] **Step 1: Write the failing tests**

```kotlin
package my.silentmode.pentana.shared

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import my.silentmode.pentana.shared.model.DashboardActivityDto
import my.silentmode.pentana.shared.model.DashboardBillsDto
import my.silentmode.pentana.shared.model.DashboardDto
import my.silentmode.pentana.shared.model.DashboardLunchDto
import my.silentmode.pentana.shared.presentation.DashboardActivityStatus
import my.silentmode.pentana.shared.presentation.HomeStore
import my.silentmode.pentana.shared.presentation.HomeUiState
import my.silentmode.pentana.shared.presentation.LunchStatus
import my.silentmode.pentana.shared.presentation.dashboardActivityStatus
import my.silentmode.pentana.shared.presentation.dashboardLunchStatus
import my.silentmode.pentana.shared.presentation.duesCleared
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HomeStoreTest {
    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private val dashboardJson = """
        {"data":{
        "bills":{"total_outstanding":"120.00","available_credit":"15.00","unpaid_count":2},
        "next_lunch":{"id":1,"date":"2026-07-20","caterer":"Dapur Selera","menu":"Nasi Lemak","is_open":true,"responded":false},
        "next_activity":{"id":5,"title":"Beach Cleanup","starts_at":"2026-07-25T08:00:00+08:00","my_status":"registered"},
        "open_activities_count":3,
        "pending_proofs_count":1}}
    """.trimIndent()

    private fun store(handler: () -> Pair<HttpStatusCode, String>): HomeStore {
        val engine = MockEngine { _ ->
            val (status, body) = handler()
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return HomeStore(DashboardRepository(ApiClient("https://x/api/v1", InMemoryTokenStore("t"), engine)))
    }

    @Test fun load_emits_content() = runTest {
        val s = store { HttpStatusCode.OK to dashboardJson }
        val state = s.state.first { it !is HomeUiState.Loading }
        assertIs<HomeUiState.Content>(state)
        assertEquals("120.00", state.data.bills.totalOutstanding)
        assertEquals("Beach Cleanup", state.data.nextActivity?.title)
    }

    @Test fun error_emits_error() = runTest {
        val s = store { HttpStatusCode.InternalServerError to "{}" }
        val state = s.state.first { it !is HomeUiState.Loading }
        assertIs<HomeUiState.Error>(state)
        assertEquals("Couldn't load your summary. Pull to refresh.", state.message)
    }

    @Test fun refresh_toggles_refreshing_and_updates_content() = runTest {
        var outstanding = "120.00"
        val s = store { HttpStatusCode.OK to dashboardJson.replace("120.00", outstanding) }
        s.state.first { it is HomeUiState.Content }
        outstanding = "0.00"
        s.refresh()
        val state = s.state.first { it is HomeUiState.Content && it.data.bills.totalOutstanding == "0.00" }
        assertIs<HomeUiState.Content>(state)
        assertFalse(s.refreshing.value)
    }

    // Derived display

    @Test fun dues_cleared_when_no_outstanding_and_no_pending_proofs() {
        assertTrue(duesCleared(dashboard(outstanding = "0.00", pendingProofs = 0)))
        assertFalse(duesCleared(dashboard(outstanding = "0.00", pendingProofs = 1)))
        assertFalse(duesCleared(dashboard(outstanding = "45.00", pendingProofs = 0)))
    }

    @Test fun dashboard_lunch_status_vote_now_when_open_not_responded() {
        assertEquals(LunchStatus.VoteNow, dashboardLunchStatus(dashLunch(isOpen = true, responded = false)))
    }

    @Test fun dashboard_lunch_status_responded_wins_over_closed() {
        assertEquals(LunchStatus.Responded, dashboardLunchStatus(dashLunch(isOpen = true, responded = true)))
        assertEquals(LunchStatus.Responded, dashboardLunchStatus(dashLunch(isOpen = false, responded = true)))
    }

    @Test fun dashboard_lunch_status_closed_when_not_open_not_responded() {
        assertEquals(LunchStatus.Closed, dashboardLunchStatus(dashLunch(isOpen = false, responded = false)))
    }

    @Test fun dashboard_activity_status_maps_my_status() {
        assertEquals(DashboardActivityStatus.Registered, dashboardActivityStatus(dashActivity("registered")))
        assertEquals(DashboardActivityStatus.Waitlisted, dashboardActivityStatus(dashActivity("waitlisted")))
        assertEquals(DashboardActivityStatus.None, dashboardActivityStatus(dashActivity("none")))
    }

    private fun dashboard(outstanding: String, pendingProofs: Int) = DashboardDto(
        bills = DashboardBillsDto(totalOutstanding = outstanding, availableCredit = "0.00", unpaidCount = 0),
        nextLunch = null, nextActivity = null, openActivitiesCount = 0, pendingProofsCount = pendingProofs,
    )
    private fun dashLunch(isOpen: Boolean, responded: Boolean) =
        DashboardLunchDto(id = 1, date = "2026-07-20", caterer = null, menu = null, isOpen = isOpen, responded = responded)
    private fun dashActivity(myStatus: String) =
        DashboardActivityDto(id = 5, title = "Beach Cleanup", startsAt = null, myStatus = myStatus)
}
```

- [x] **Step 2: Run — expect FAIL** (unresolved `HomeStore`/`HomeUiState`/`duesCleared`/…)

Run: `./gradlew :shared:allTests`
Expected: compile failure, unresolved references.

- [x] **Step 3: Implement `HomeDisplay.kt`**

```kotlin
package my.silentmode.pentana.shared.presentation

import my.silentmode.pentana.shared.model.DashboardActivityDto
import my.silentmode.pentana.shared.model.DashboardDto
import my.silentmode.pentana.shared.model.DashboardLunchDto

/** Shared decision — each platform maps this to its own chip/pill styling. */
enum class DashboardActivityStatus { Registered, Waitlisted, None }

/** "All clear" celebration card instead of the dues card. */
fun duesCleared(d: DashboardDto): Boolean =
    d.bills.totalOutstanding == "0.00" && d.pendingProofsCount == 0

/**
 * Status chip for the next-lunch card. Both platforms agree `responded` wins over closed
 * (unlike the Lunch screen's [lunchStatus], where closed wins).
 */
fun dashboardLunchStatus(lunch: DashboardLunchDto): LunchStatus = when {
    lunch.isOpen && !lunch.responded -> LunchStatus.VoteNow
    lunch.responded -> LunchStatus.Responded
    else -> LunchStatus.Closed
}

fun dashboardActivityStatus(activity: DashboardActivityDto): DashboardActivityStatus = when (activity.myStatus) {
    "registered" -> DashboardActivityStatus.Registered
    "waitlisted" -> DashboardActivityStatus.Waitlisted
    else -> DashboardActivityStatus.None
}
```

- [x] **Step 4: Implement `HomeStore.kt`**

```kotlin
package my.silentmode.pentana.shared.presentation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import my.silentmode.pentana.shared.DashboardRepository
import my.silentmode.pentana.shared.model.DashboardDto

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Error(val message: String) : HomeUiState
    data class Content(val data: DashboardDto) : HomeUiState
}

/**
 * Shared presentation logic for the Home (dashboard) screen. Owns its coroutine scope; the host
 * controls teardown. Android calls [clear] from ViewModel.onCleared(); iOS reuses the store across
 * view reappearance and lets it release with the view, so it does not call [clear] on disappear.
 */
class HomeStore(private val repo: DashboardRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init { load() }

    fun load() {
        scope.launch {
            _state.value = HomeUiState.Loading
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
            HomeUiState.Content(repo.dashboard())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            HomeUiState.Error("Couldn't load your summary. Pull to refresh.")
        }
    }

    fun clear() { scope.cancel() }
}
```

- [x] **Step 5: Run — expect PASS**

Run: `./gradlew :shared:allTests`
Expected: all tests pass.

- [x] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/my/silentmode/pentana/shared/presentation/HomeStore.kt shared/src/commonMain/kotlin/my/silentmode/pentana/shared/presentation/HomeDisplay.kt shared/src/commonTest/kotlin/my/silentmode/pentana/shared/HomeStoreTest.kt
git commit -m "feat(shared): HomeStore + dashboard display decisions + tests"
```

---

## Task 5: Rewire Android Home to the shared store

**Files:**
- Modify: `androidApp/src/main/kotlin/my/silentmode/pentana/feature/home/HomeViewModel.kt`
- Modify: `androidApp/src/main/kotlin/my/silentmode/pentana/feature/home/HomeScreen.kt`

- [x] **Step 1: Slim `HomeViewModel.kt`**

Replace the whole file with:

```kotlin
package my.silentmode.pentana.feature.home

import androidx.lifecycle.ViewModel
import my.silentmode.pentana.shared.DashboardRepository
import my.silentmode.pentana.shared.presentation.HomeStore

class HomeViewModel(repo: DashboardRepository) : ViewModel() {
    val store = HomeStore(repo)
    override fun onCleared() { store.clear() }
}
```

(The local `HomeUiState` sealed interface and `refreshing` mutableState are gone — they come from the shared store.)

- [x] **Step 2: Update `HomeScreen.kt`**

Import changes — remove nothing else; add:

```kotlin
import my.silentmode.pentana.shared.presentation.DashboardActivityStatus
import my.silentmode.pentana.shared.presentation.HomeUiState
import my.silentmode.pentana.shared.presentation.LunchStatus
import my.silentmode.pentana.shared.presentation.dashboardActivityStatus
import my.silentmode.pentana.shared.presentation.dashboardLunchStatus
import my.silentmode.pentana.shared.presentation.duesCleared
```

`HomeScreen` composable:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(userName: String, onSwitchTab: (NavDest) -> Unit) {
    val vm = appViewModel { HomeViewModel(it.dashboard) }
    val state by vm.store.state.collectAsStateWithLifecycle()
    val refreshing by vm.store.refreshing.collectAsStateWithLifecycle()
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm.store::refresh, modifier = Modifier.fillMaxSize()) {
        when (val s = state) {
            is HomeUiState.Loading -> LoadingState()
            is HomeUiState.Error -> ErrorState(s.message, vm.store::load)
            is HomeUiState.Content -> HomeContent(userName, s.data, onSwitchTab)
        }
    }
}
```

In `HomeContent`, replace the local all-clear decision:

```kotlin
val allClear = duesCleared(d)
```

In `LunchSummary`, replace the `when { lunch.isOpen && !lunch.responded -> … }` block with the shared decision (rendering unchanged: `Closed` keeps the "Voting closed" text treatment):

```kotlin
when (dashboardLunchStatus(lunch)) {
    LunchStatus.VoteNow -> StatusChip(ChipKind.VoteNow)
    LunchStatus.Responded -> StatusChip(ChipKind.Responded)
    LunchStatus.Closed -> Text("Voting closed", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
```

In `ActivitySummary`, replace the `when (activity.myStatus)` chip switch and the follow-up string-comparison with the shared decision:

```kotlin
val status = dashboardActivityStatus(activity)
Row(verticalAlignment = Alignment.CenterVertically) {
    when (status) {
        DashboardActivityStatus.Registered -> StatusChip(ChipKind.Registered)
        DashboardActivityStatus.Waitlisted -> StatusChip(ChipKind.Waitlisted)
        DashboardActivityStatus.None -> {}
    }
    Text(
        (if (status != DashboardActivityStatus.None) " · " else "") + "$openCount open",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp),
    )
}
```

- [x] **Step 3: Build**

Run: `./gradlew :androidApp:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [x] **Step 4: Commit**

```bash
git add androidApp/src/main/kotlin/my/silentmode/pentana/feature/home
git commit -m "refactor(android): Home consumes shared HomeStore + display decisions"
```

---

## Task 6: Rewire iOS Home to the shared store via SKIE

**Files:**
- Modify: `iosApp/iosApp/SessionStore.swift`
- Modify: `iosApp/iosApp/HomeView.swift`

- [x] **Step 1: Vend the store from `SessionStore`** — next to `makeLunchStore()`:

```swift
/// Vend a shared Home presentation store, backed by the shared `DashboardRepository`.
/// Same lifecycle contract as makeLunchStore(): held in @State, reused across reappear, not cleared.
func makeHomeStore() -> HomeStore { HomeStore(repo: dashboard) }
```

- [x] **Step 2: Rewrite `HomeView`'s state handling**

Change the import to `@preconcurrency import Shared`. Replace the `@State` vars, `body`, and delete the `load()` func:

```swift
struct HomeView: View {
    @EnvironmentObject private var session: SessionStore
    @Binding var selection: Int
    @State private var store: HomeStore?
    @State private var state: HomeUiState = HomeUiStateLoading.shared

    var body: some View {
        content
            .task {
                let s = store ?? session.makeHomeStore()
                store = s
                async let states: Void = { for await value in s.state { await MainActor.run { state = value } } }()
                _ = await states
            }
    }

    @ViewBuilder private var content: some View {
        switch onEnum(of: state) {
        case .loading:
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        case .error(let e):
            ScrollView {
                EmptyStateView(symbol: "icloud.slash", tint: Pent.bad, bg: Pent.badBg,
                               title: "Couldn't load", message: e.message)
                    .containerRelativeFrame(.vertical, alignment: .center)
            }
            .refreshable { store?.refresh() }
        case .content(let c):
            ScrollView {
                VStack(alignment: .leading, spacing: 11) {
                    Text(todayString)
                        .font(.pentSub).foregroundStyle(Pent.label2)
                        .padding(.horizontal, 4).padding(.bottom, 2)

                    duesCard(c.data)
                    lunchCard(c.data.nextLunch)
                    activityCard(c.data.nextActivity, openCount: Int(c.data.openActivitiesCount))
                    proofsCard(pending: Int(c.data.pendingProofsCount))
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 28)
            }
            .refreshable { store?.refresh() }
        }
    }
    // duesCard/lunchCard/activityCard/proofsCard and todayString/lunchLine stay (edited below); load() is deleted.
}
```

In `duesCard`, replace the local decision:

```swift
let cleared = duesCleared(d: d)
```

In `lunchCard`, replace the pill if/else chain with the shared decision:

```swift
switch dashboardLunchStatus(lunch: lunch) {
case .voteNow: StatusPill(.voteNow).padding(.top, 4)
case .responded: StatusPill(.responded).padding(.top, 4)
case .closed: StatusPill(.closed).padding(.top, 4)
}
```

In `activityCard`, replace the `myStatus == "waitlisted"` ternary:

```swift
StatusPill(dashboardActivityStatus(activity: activity) == .waitlisted ? .waitlisted : .registered)
```

> SKIE notes: shared top-level functions are GLOBAL Swift functions with labelled params (`duesCleared(d:)`, `dashboardLunchStatus(lunch:)`, `dashboardActivityStatus(activity:)`); sealed `HomeUiState` → `onEnum(of:)` with `HomeUiStateLoading.shared` for the `data object`; enum cases lower-camelCased. `openActivitiesCount`/`pendingProofsCount` are Kotlin `Int` — they arrive as `Int32`, so the existing `Int(...)` conversions still apply.

- [x] **Step 3: Build**

Run: `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build`
Expected: `BUILD SUCCEEDED`.

- [x] **Step 4: Commit**

```bash
git add iosApp/iosApp/SessionStore.swift iosApp/iosApp/HomeView.swift
git commit -m "refactor(ios): Home consumes shared HomeStore via SKIE"
```

---

## Task 7: Shared NotificationsStore + NotificationsDisplay (TDD)

**Files:**
- Create: `shared/src/commonMain/kotlin/my/silentmode/pentana/shared/presentation/NotificationsStore.kt`
- Create: `shared/src/commonMain/kotlin/my/silentmode/pentana/shared/presentation/NotificationsDisplay.kt`
- Test: `shared/src/commonTest/kotlin/my/silentmode/pentana/shared/NotificationsStoreTest.kt`

- [x] **Step 1: Write the failing tests**

```kotlin
package my.silentmode.pentana.shared

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import my.silentmode.pentana.shared.presentation.NotifUiState
import my.silentmode.pentana.shared.presentation.NotificationKind
import my.silentmode.pentana.shared.presentation.NotificationsStore
import my.silentmode.pentana.shared.presentation.notificationKind
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class NotificationsStoreTest {
    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private val pageJson = """
        {"data":[
        {"id":"a1","title":"Lunch voting is open","body":"Vote by Friday","read":false,"created_at":"2026-07-18T09:00:00+08:00"},
        {"id":"a2","title":"Payment received","body":null,"read":true,"created_at":"2026-07-17T12:00:00+08:00"}
        ],"unread_count":1}
    """.trimIndent()

    private fun store(handler: () -> Pair<HttpStatusCode, String>): NotificationsStore {
        val engine = MockEngine { _ ->
            val (status, body) = handler()
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return NotificationsStore(NotificationsRepository(ApiClient("https://x/api/v1", InMemoryTokenStore("t"), engine)))
    }

    @Test fun load_emits_content() = runTest {
        val s = store { HttpStatusCode.OK to pageJson }
        val state = s.state.first { it !is NotifUiState.Loading }
        assertIs<NotifUiState.Content>(state)
        assertEquals(2, state.items.size)
        assertEquals("Lunch voting is open", state.items.first().title)
    }

    @Test fun error_emits_error() = runTest {
        val s = store { HttpStatusCode.InternalServerError to "{}" }
        val state = s.state.first { it !is NotifUiState.Loading }
        assertIs<NotifUiState.Error>(state)
        assertEquals("Couldn't load notifications.", state.message)
    }

    // Keyword → kind mapping (union of both platforms' lists, order preserved)

    @Test fun kind_lunch() {
        assertEquals(NotificationKind.Lunch, notificationKind("Lunch voting is open"))
    }

    @Test fun kind_cancelled() {
        assertEquals(NotificationKind.Cancelled, notificationKind("Hike cancelled this weekend"))
    }

    @Test fun kind_payment_covers_proof_payment_and_dues() {
        assertEquals(NotificationKind.Payment, notificationKind("Payment proof approved"))
        assertEquals(NotificationKind.Payment, notificationKind("Payment received"))
        assertEquals(NotificationKind.Payment, notificationKind("July dues issued"))
    }

    @Test fun kind_activity_joined_covers_youre_in_promoted_waitlist() {
        assertEquals(NotificationKind.ActivityJoined, notificationKind("You're in! See you Saturday"))
        assertEquals(NotificationKind.ActivityJoined, notificationKind("Promoted from the waitlist"))
        assertEquals(NotificationKind.ActivityJoined, notificationKind("Waitlist update"))
    }

    @Test fun kind_activity_covers_union_keywords() {
        assertEquals(NotificationKind.Activity, notificationKind("New activity posted"))
        assertEquals(NotificationKind.Activity, notificationKind("A spot opened up"))
        assertEquals(NotificationKind.Activity, notificationKind("Event reminder"))
        assertEquals(NotificationKind.Activity, notificationKind("Hiking trip on Saturday"))
        assertEquals(NotificationKind.Activity, notificationKind("Beach cleanup this weekend"))
        assertEquals(NotificationKind.Activity, notificationKind("Workshop: intro to sourdough"))
    }

    @Test fun kind_general_fallback() {
        assertEquals(NotificationKind.General, notificationKind("Welcome to Pentana"))
    }

    @Test fun kind_cancel_beats_activity_keywords() {
        // "cancel" is checked before "activity"/"hik" — a cancelled activity reads as a cancellation.
        assertEquals(NotificationKind.Cancelled, notificationKind("Activity cancelled: hiking trip"))
    }
}
```

- [x] **Step 2: Run — expect FAIL** (unresolved references)

Run: `./gradlew :shared:allTests`
Expected: compile failure.

- [x] **Step 3: Implement `NotificationsDisplay.kt`**

```kotlin
package my.silentmode.pentana.shared.presentation

/** Shared decision — each platform maps this to its own icon + colours. */
enum class NotificationKind { Lunch, Cancelled, Payment, ActivityJoined, Activity, General }

/**
 * NotificationDto has no type field — infer the kind from the title text.
 * Keyword lists are the union of what Android and iOS matched before sharing; check order matters
 * (e.g. "Activity cancelled" must read as Cancelled).
 */
fun notificationKind(title: String): NotificationKind {
    val t = title.lowercase()
    return when {
        "lunch" in t -> NotificationKind.Lunch
        "cancel" in t -> NotificationKind.Cancelled
        "proof" in t || "payment" in t || "dues" in t -> NotificationKind.Payment
        "you're in" in t || "promoted" in t || "waitlist" in t -> NotificationKind.ActivityJoined
        "activity" in t || "spot" in t || "event" in t || "hik" in t || "clean" in t || "workshop" in t -> NotificationKind.Activity
        else -> NotificationKind.General
    }
}
```

- [x] **Step 4: Implement `NotificationsStore.kt`**

```kotlin
package my.silentmode.pentana.shared.presentation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import my.silentmode.pentana.shared.NotificationsRepository
import my.silentmode.pentana.shared.model.NotificationDto

sealed interface NotifUiState {
    data object Loading : NotifUiState
    data class Error(val message: String) : NotifUiState
    data class Content(val items: List<NotificationDto>) : NotifUiState
}

/**
 * Shared presentation logic for the notifications list. Owns its coroutine scope; the host
 * controls teardown (Android ViewModel.onCleared; iOS releases with the sheet).
 * Badge count + mark-all-read stay in the platform session layers until the shared
 * SessionManager lands (M4).
 */
class NotificationsStore(private val repo: NotificationsRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow<NotifUiState>(NotifUiState.Loading)
    val state: StateFlow<NotifUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        scope.launch {
            _state.value = NotifUiState.Loading
            _state.value = try {
                NotifUiState.Content(repo.notifications().data)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                NotifUiState.Error("Couldn't load notifications.")
            }
        }
    }

    fun clear() { scope.cancel() }
}
```

- [x] **Step 5: Run — expect PASS**

Run: `./gradlew :shared:allTests`
Expected: all tests pass.

- [x] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/my/silentmode/pentana/shared/presentation/NotificationsStore.kt shared/src/commonMain/kotlin/my/silentmode/pentana/shared/presentation/NotificationsDisplay.kt shared/src/commonTest/kotlin/my/silentmode/pentana/shared/NotificationsStoreTest.kt
git commit -m "feat(shared): NotificationsStore + notification kind mapping + tests"
```

---

## Task 8: Rewire Android Notifications to the shared store

**Files:**
- Modify: `androidApp/src/main/kotlin/my/silentmode/pentana/feature/notifications/NotificationsViewModel.kt`
- Modify: `androidApp/src/main/kotlin/my/silentmode/pentana/feature/notifications/NotificationsSheet.kt`

- [x] **Step 1: Slim `NotificationsViewModel.kt`**

Replace the whole file with:

```kotlin
package my.silentmode.pentana.feature.notifications

import androidx.lifecycle.ViewModel
import my.silentmode.pentana.shared.NotificationsRepository
import my.silentmode.pentana.shared.presentation.NotificationsStore

class NotificationsViewModel(repo: NotificationsRepository) : ViewModel() {
    val store = NotificationsStore(repo)
    override fun onCleared() { store.clear() }
}
```

- [x] **Step 2: Update `NotificationsSheet.kt`**

Import changes — remove the (now deleted) local `NotifUiState` reference by importing the shared ones:

```kotlin
import my.silentmode.pentana.shared.presentation.NotifUiState
import my.silentmode.pentana.shared.presentation.NotificationKind
import my.silentmode.pentana.shared.presentation.notificationKind
```

State collection becomes `val state by vm.store.state.collectAsStateWithLifecycle()` (the `when (val s = state)` branches are unchanged — same shape, shared type). The `onMarkAllRead` callback wiring is untouched (badge stays in the session layer until M4).

Replace `notifVisual` with a mapper over the shared kind (the keyword heuristics are deleted from this file):

```kotlin
/** Map the shared kind decision to this platform's icon + colours. */
@Composable
private fun notifVisual(title: String): Triple<ImageVector, Color, Color> {
    val pc = LocalPentanaColors.current
    return when (notificationKind(title)) {
        NotificationKind.Lunch -> Triple(Icons.Filled.Restaurant, pc.lunch.container, pc.lunch.color)
        NotificationKind.Cancelled -> Triple(Icons.Filled.Cancel, pc.bad.container, pc.bad.color)
        NotificationKind.Payment -> Triple(Icons.Filled.Description, pc.proof.container, pc.proof.color)
        NotificationKind.ActivityJoined -> Triple(Icons.Filled.Celebration, pc.activ.container, pc.activ.color)
        NotificationKind.Activity -> Triple(Icons.Filled.CalendarMonth, pc.activ.container, pc.activ.color)
        NotificationKind.General -> Triple(Icons.Filled.Notifications, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
    }
}
```

- [x] **Step 3: Build**

Run: `./gradlew :androidApp:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [x] **Step 4: Commit**

```bash
git add androidApp/src/main/kotlin/my/silentmode/pentana/feature/notifications
git commit -m "refactor(android): Notifications consumes shared store + kind mapping"
```

---

## Task 9: Rewire iOS Notifications to the shared store via SKIE

**Files:**
- Modify: `iosApp/iosApp/SessionStore.swift`
- Modify: `iosApp/iosApp/NotificationsView.swift`

- [x] **Step 1: Vend the store from `SessionStore`** — next to the other factories:

```swift
/// Vend a shared Notifications presentation store for the bell sheet.
func makeNotificationsStore() -> NotificationsStore { NotificationsStore(repo: notifications) }
```

- [x] **Step 2: Rewrite `NotificationsView`'s state handling**

Change the import to `@preconcurrency import Shared`. Replace the `@State` vars, the loading/content plumbing, and delete `load()`:

```swift
struct NotificationsView: View {
    @EnvironmentObject private var session: SessionStore
    @Environment(\.dismiss) private var dismiss
    @State private var store: NotificationsStore?
    @State private var state: NotifUiState = NotifUiStateLoading.shared
    @State private var markedRead = false

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("Notifications")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Done") { dismiss() }.tint(Pent.accent)
                    }
                }
                .task {
                    let s = store ?? session.makeNotificationsStore()
                    store = s
                    async let states: Void = {
                        for await value in s.state {
                            await MainActor.run { state = value }
                            // Opening the sheet with unread items marks everything read (badge lives
                            // in the session layer until the shared SessionManager lands).
                            if case .content(let c) = onEnum(of: value),
                               c.items.contains(where: { !$0.read }), !markedRead {
                                await MainActor.run { markedRead = true }
                                await session.markNotificationsRead()
                            }
                        }
                    }()
                    _ = await states
                }
        }
    }

    @ViewBuilder private var content: some View {
        switch onEnum(of: state) {
        case .loading:
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        case .error(let e):
            ScrollView {
                EmptyStateView(symbol: "bell.fill", title: "Couldn't load", message: e.message)
                    .containerRelativeFrame(.vertical, alignment: .center)
            }
        case .content(let c):
            ScrollView {
                VStack(spacing: 0) {
                    if c.items.isEmpty {
                        EmptyStateView(symbol: "bell.fill", title: "No notifications yet",
                                       message: "Lunch, activity and payment updates will show up here.")
                            .containerRelativeFrame(.vertical, alignment: .center)
                    } else {
                        HStack {
                            Spacer()
                            Button("Mark all read") { Task { await session.markNotificationsRead() } }
                                .font(.pentFoot).tint(Pent.accent)
                        }
                        .padding(.bottom, 6)

                        InsetGroup {
                            ForEach(Array(c.items.enumerated()), id: \.element.id) { index, item in
                                NotifRow(item: item)
                                if index < c.items.count - 1 { PentHairline(leadingInset: 60) }
                            }
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 8)
            }
        }
    }
}
```

In `NotifRow`, replace the private `glyph` keyword matching with the shared kind (delete the old keyword heuristics):

```swift
private var glyph: (String, Color, Color) {
    switch notificationKind(title: item.title) {
    case .lunch: return ("fork.knife", Pent.lunch, Pent.lunchBg)
    case .cancelled: return ("xmark.circle.fill", Pent.bad, Pent.badBg)
    case .payment: return ("doc.text.fill", Pent.proof, Pent.proofBg)
    case .activityJoined: return ("party.popper.fill", Pent.activ, Pent.activBg)
    case .activity: return ("calendar", Pent.activ, Pent.activBg)
    case .general: return ("bell.fill", Pent.neutral, Pent.neutralBg)
    }
}
```

> SKIE notes: `notificationKind(title:)` is a global Swift function; the returned Kotlin enum is a Swift enum with lower-camelCased cases, switchable exhaustively. `NotifUiState` sealed interface → `onEnum(of:)`, `NotifUiStateLoading.shared` for the singleton. Behaviour note: mark-read now fires only when unread items exist (matches today's iOS `if page.unreadCount > 0` check, keyed off the items themselves).

- [x] **Step 3: Build**

Run: `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build`
Expected: `BUILD SUCCEEDED`.

- [x] **Step 4: Commit**

```bash
git add iosApp/iosApp/SessionStore.swift iosApp/iosApp/NotificationsView.swift
git commit -m "refactor(ios): Notifications consumes shared store via SKIE"
```

---

## Task 10: Full milestone verification

- [x] **Step 1: Everything green**

Run:
```bash
./gradlew :shared:allTests :androidApp:assembleDebug
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build
```
Expected: shared tests pass (JVM + iOS-sim); both apps build.

- [x] **Step 2: Confirm no duplicated logic remains**

- `androidApp/.../home/HomeViewModel.kt` and `notifications/NotificationsViewModel.kt` contain no state machines (thin wrappers only).
- No `total_outstanding == "0.00"` / `myStatus ==` / keyword-matching decisions left in `HomeScreen.kt`, `HomeView.swift`, `NotificationsSheet.kt`, `NotificationsView.swift` — grep:
```bash
grep -rn 'contains("lunch")\|"cancel" in\|t.contains' androidApp/src iosApp/iosApp || echo CLEAN
```
Expected: `CLEAN` (or only non-notification hits, to be judged).

- [x] **Step 3: Verify `AppConfig.kt` never staged**

Run: `git log --stat feat/shared-stores-m1 --oneline | grep -c AppConfig || echo NEVER-STAGED`
Expected: `NEVER-STAGED` (count 0).

- [x] **Step 4: STOP — hand to Fahmi**

Do not push, do not merge. Report: test summary, build results, and the commit list for review (requesting-code-review runs before Fahmi merges locally).

---

## Self-review notes

- **Spec coverage (M1 section):** actionError pattern + copy on LunchStore (T1) · Android Snackbar (T2) · iOS alert (T3) · HomeStore + HomeUiState + duesCleared/dashboardLunchStatus/dashboardActivityStatus (T4) · Android Home rewire, refreshing moved into store (T5) · iOS Home rewire incl. new Error treatment via existing EmptyStateView (T6) · NotificationsStore + NotifUiState + notificationKind union (T7) · Android sheet rewire, badge callback untouched (T8) · iOS sheet rewire, mark-read-on-open stays view-triggered (T9) · verification (T10).
- **Types verified against the repo:** `DashboardDto`/`DashboardBillsDto`/`DashboardLunchDto`/`DashboardActivityDto` fields, `NotificationDto`/`NotificationsPageDto`, `DashboardRepository.dashboard()`, `NotificationsRepository.notifications()/markAllRead()`, `ApiClient(baseUrl, tokenStore, engine)` + `InMemoryTokenStore` (from `PentanaApiTest`/`LunchStoreTest` usage). No invented members.
- **Out of scope:** Bills (M2), Activities (M3), SessionManager/badge unification (M4).
