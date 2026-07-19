# Shared Stores Milestone 2 — Store Helpers + Suspend Refresh + BillsStore Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract the store mechanics into internal shared helpers (fixing the refresh-overlap race), make `refresh()` awaitable everywhere (restoring the iOS pull-to-refresh spinner), close the curly-apostrophe gap in `notificationKind`, then build the shared `BillsStore` (load/refresh + `SubmitState` proof-upload machine) consumed by both platforms.

**Architecture:** Internal (non-exported) helpers in `commonMain` — `RefreshTracker` for overlap-safe refresh tracking and `runGuardedAction` for the guarded fire-and-forget shape — keep each store's public surface concrete for SKIE. `refresh()` becomes `suspend` on Lunch/Home/Bills (SKIE → Swift `async`, so `.refreshable` holds the system spinner); Android ViewModels gain a one-line `refresh()` launcher. `BillsStore` follows the canonical pattern plus a `submit: StateFlow<SubmitState>` machine; iOS gains upload error feedback it currently swallows.

**Tech Stack:** Kotlin 2.1.21, kotlinx-coroutines StateFlow, Ktor MockEngine (tests), SKIE 0.10.13, Compose, SwiftUI.

**Spec:** `docs/superpowers/specs/2026-07-19-shared-presentation-rollout-design.md` (Milestone 2 + brainstorm addenda 2026-07-19: internal helpers with concrete sealed states; `suspend refresh()` everywhere; apostrophe normalization)
**Branch:** `feat/shared-stores-m2` (already created off merged main)

**Standing rules for every task:**
- Gradle/xcodebuild have no network inside the sandbox — run them with the sandbox disabled.
- iOS builds MUST use an arm64 simulator destination: `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build`
- NEVER stage `androidApp/src/main/kotlin/my/silentmode/pentana/core/AppConfig.kt` (uncommitted local edit). `git add` explicit paths only.
- Do not push or merge — the maintainer reviews first.
- **Naming: descriptive variable names only — no single-letter locals/params anywhere, including tests and tight-scope pattern bindings** (owner mandate; overrides "match surrounding style").

---

## Task 1: Internal store helpers + suspend refresh on Lunch/Home (TDD)

**Files:**
- Create: `shared/src/commonMain/kotlin/my/silentmode/pentana/shared/presentation/StoreSupport.kt`
- Modify: `shared/src/commonMain/kotlin/my/silentmode/pentana/shared/presentation/LunchStore.kt`
- Modify: `shared/src/commonMain/kotlin/my/silentmode/pentana/shared/presentation/HomeStore.kt`
- Test: `shared/src/commonTest/kotlin/my/silentmode/pentana/shared/HomeStoreTest.kt`

- [ ] **Step 1: Write the failing test** — add to `HomeStoreTest` (imports to add if missing: `kotlinx.coroutines.CompletableDeferred`, `kotlinx.coroutines.launch`, `kotlinx.coroutines.test.advanceUntilIdle`, `io.ktor.client.engine.mock.MockEngine`, `kotlin.test.assertTrue`):

```kotlin
@Test fun overlapping_refreshes_keep_flag_until_last_completes() = runTest {
    val gate = CompletableDeferred<Unit>()
    var requestCount = 0
    val engine = MockEngine { _ ->
        requestCount += 1
        if (requestCount == 2) gate.await() // hold the FIRST refresh (request 2; request 1 is the init load)
        respond(dashboardJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }
    val store = HomeStore(DashboardRepository(ApiClient("https://x/api/v1", InMemoryTokenStore("t"), engine)))
    store.state.first { it is HomeUiState.Content }

    val heldRefresh = launch { store.refresh() }   // request 2 — parked on the gate
    store.refreshing.first { it }                   // flag is up
    store.refresh()                                 // request 3 — completes immediately
    advanceUntilIdle()                              // drain everything not blocked on the gate
    // The overlapping completed refresh must NOT drop the flag while the first is still in flight.
    assertTrue(store.refreshing.value)
    gate.complete(Unit)
    heldRefresh.join()
    store.refreshing.first { !it }                  // flag clears once the LAST refresh finishes
}
```

- [ ] **Step 2: Run — expect FAIL**

Run: `./gradlew :shared:allTests`
Expected: compile error — `store.refresh()` inside `launch { }` is fine today (non-suspend), but `assertTrue(store.refreshing.value)` fails at runtime: the completed second refresh clears the flag while the first is parked. (If instead it fails compiling after Step 3's suspend change, that's also the red step — the point is the test exists before the fix.)

- [ ] **Step 3: Create `StoreSupport.kt`**

```kotlin
package my.silentmode.pentana.shared.presentation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shared mechanics for the presentation stores. Internal on purpose — the stores expose only
 * concrete sealed states and plain members, keeping the SKIE-generated Swift surface simple.
 */

/**
 * Overlap-safe refresh tracking: the [refreshing] flag stays up until the LAST overlapping
 * refresh completes. Runs on Dispatchers.Main so the active-count is confined to one thread
 * (callers may be Swift-async threads via SKIE).
 */
internal class RefreshTracker(private val refreshing: MutableStateFlow<Boolean>) {
    private var activeCount = 0

    suspend fun run(fetch: suspend () -> Unit) = withContext(Dispatchers.Main) {
        activeCount += 1
        refreshing.value = true
        try {
            fetch()
        } finally {
            activeCount -= 1
            if (activeCount == 0) refreshing.value = false
        }
    }
}

/**
 * Guarded fire-and-forget per-id action: synchronous in-flight check-and-set BEFORE launching
 * (a plain Main dispatch would arm the guard a turn too late), failure surfaced via
 * [actionError], id always removed in finally. A guarded duplicate returns before touching
 * [actionError] — it must not wipe an error the user is reading.
 */
internal fun runGuardedAction(
    scope: CoroutineScope,
    inFlight: MutableStateFlow<Set<Long>>,
    actionError: MutableStateFlow<String?>,
    id: Long,
    errorMessage: String,
    action: suspend () -> Unit,
) {
    if (id in inFlight.value) return
    inFlight.value = inFlight.value + id
    actionError.value = null
    scope.launch {
        try {
            action()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            actionError.value = errorMessage
        } finally {
            inFlight.value = inFlight.value - id
        }
    }
}
```

- [ ] **Step 4: Refactor `LunchStore.kt`** — replace `refresh()`, `choose()`, `notAttending()`, add the tracker and the error-copy constant. The full new class body (state declarations, `load()`, `fetch()`, `replace()`, `clear()`, KDocs unchanged from current file — only the members shown here change):

```kotlin
    private val refreshTracker = RefreshTracker(_refreshing)

    /** Suspends until the fetch completes so iOS .refreshable can hold the system spinner. */
    suspend fun refresh() = refreshTracker.run { fetch() }

    fun choose(lunchId: Long, mealOptionId: Long) =
        runGuardedAction(scope, _inFlight, _actionError, lunchId, SAVE_CHOICE_ERROR) {
            replace(repo.chooseOption(lunchId, mealOptionId))
        }

    fun notAttending(lunchId: Long) =
        runGuardedAction(scope, _inFlight, _actionError, lunchId, SAVE_CHOICE_ERROR) {
            replace(repo.markNotAttending(lunchId))
        }
```

and at the bottom of the class:

```kotlin
    private companion object {
        const val SAVE_CHOICE_ERROR = "Couldn't save your choice. Please try again."
    }
```

Delete the old `refresh()` (the `scope.launch { _refreshing.value = true; ... }` version) and the two hand-rolled guarded bodies.

- [ ] **Step 5: Refactor `HomeStore.kt`** — same refresh change:

```kotlin
    private val refreshTracker = RefreshTracker(_refreshing)

    /** Suspends until the fetch completes so iOS .refreshable can hold the system spinner. */
    suspend fun refresh() = refreshTracker.run { fetch() }
```

(Old launch-based `refresh()` deleted. `NotificationsStore` has no refresh — untouched.)

- [ ] **Step 6: Run — expect PASS**

Run: `./gradlew :shared:allTests`
Expected: all suites green, including the new overlap test and every existing Lunch/Home test (they call `refresh()` from within `runTest`, so the suspend change compiles; the guarded-duplicate and actionError pin tests prove the helper preserved semantics).

- [ ] **Step 7: Commit**

```bash
git add shared/src/commonMain/kotlin/my/silentmode/pentana/shared/presentation/StoreSupport.kt shared/src/commonMain/kotlin/my/silentmode/pentana/shared/presentation/LunchStore.kt shared/src/commonMain/kotlin/my/silentmode/pentana/shared/presentation/HomeStore.kt shared/src/commonTest/kotlin/my/silentmode/pentana/shared/HomeStoreTest.kt
git commit -m "refactor(shared): store helpers (RefreshTracker, runGuardedAction) + suspend refresh; fix overlap race"
```

---

## Task 2: Android — launch suspend refresh from the ViewModels

**Files:**
- Modify: `androidApp/src/main/kotlin/my/silentmode/pentana/feature/lunch/LunchViewModel.kt`
- Modify: `androidApp/src/main/kotlin/my/silentmode/pentana/feature/home/HomeViewModel.kt`
- Modify: `androidApp/src/main/kotlin/my/silentmode/pentana/feature/lunch/LunchScreen.kt`
- Modify: `androidApp/src/main/kotlin/my/silentmode/pentana/feature/home/HomeScreen.kt`

- [ ] **Step 1: Add a refresh launcher to both ViewModels.** `LunchViewModel.kt` becomes:

```kotlin
package my.silentmode.pentana.feature.lunch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import my.silentmode.pentana.shared.LunchRepository
import my.silentmode.pentana.shared.presentation.LunchStore

class LunchViewModel(repo: LunchRepository) : ViewModel() {
    val store = LunchStore(repo)
    fun refresh() { viewModelScope.launch { store.refresh() } }
    override fun onCleared() { store.clear() }
}
```

`HomeViewModel.kt` gets the identical `refresh()` addition (imports `viewModelScope` + `launch`).

- [ ] **Step 2: Point the screens at the launcher.** In `LunchScreen.kt` and `HomeScreen.kt`, change the `PullToRefreshBox` argument `onRefresh = vm.store::refresh` → `onRefresh = vm::refresh`. Nothing else changes (the `refreshing` flow collection stays — it still drives the Compose indicator).

- [ ] **Step 3: Build**

Run: `./gradlew :androidApp:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add androidApp/src/main/kotlin/my/silentmode/pentana/feature/lunch androidApp/src/main/kotlin/my/silentmode/pentana/feature/home
git commit -m "refactor(android): launch suspend store refresh from ViewModels"
```

---

## Task 3: iOS — await refresh in .refreshable (spinner restored)

**Files:**
- Modify: `iosApp/iosApp/LunchView.swift`
- Modify: `iosApp/iosApp/HomeView.swift`

- [ ] **Step 1: Await the now-async refresh.** In both files, every `.refreshable { store?.refresh() }` becomes:

```swift
.refreshable { try? await store?.refresh() }
```

(`LunchView` has two — error and content cases; `HomeView` has two.) SKIE surfaces `suspend fun refresh()` as Swift `async throws`; `try?` is correct — a failed refresh already lands in the store's Error state, and cancellation (view dismissed mid-pull) needs no handling.

- [ ] **Step 2: Build**

Run: `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build`
Expected: `BUILD SUCCEEDED`.

- [ ] **Step 3: Commit**

```bash
git add iosApp/iosApp/LunchView.swift iosApp/iosApp/HomeView.swift
git commit -m "fix(ios): hold pull-to-refresh spinner by awaiting suspend refresh"
```

---

## Task 4: Apostrophe variant in notificationKind (TDD)

**Files:**
- Modify: `shared/src/commonMain/kotlin/my/silentmode/pentana/shared/presentation/NotificationsDisplay.kt`
- Test: `shared/src/commonTest/kotlin/my/silentmode/pentana/shared/NotificationsStoreTest.kt`

- [ ] **Step 1: Write the failing test** — add to `NotificationsStoreTest`:

```kotlin
@Test fun kind_activity_joined_matches_typographic_apostrophe() {
    // Backends often emit U+2019 ("you’re in") — must not fall through to General.
    assertEquals(NotificationKind.ActivityJoined, notificationKind("You’re in! See you Saturday"))
}
```

- [ ] **Step 2: Run — expect FAIL**

Run: `./gradlew :shared:allTests`
Expected: the new test fails (`General` != `ActivityJoined`).

- [ ] **Step 3: Implement** — in `NotificationsDisplay.kt`, extend the ActivityJoined branch with the typographic variant (no whole-title normalization; only this keyword contains an apostrophe):

```kotlin
        title.contains("you're in", ignoreCase = true) || title.contains("you’re in", ignoreCase = true) || title.contains("promoted", ignoreCase = true) || title.contains("waitlist", ignoreCase = true) -> NotificationKind.ActivityJoined
```

- [ ] **Step 4: Run — expect PASS**

Run: `./gradlew :shared:allTests`

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/my/silentmode/pentana/shared/presentation/NotificationsDisplay.kt shared/src/commonTest/kotlin/my/silentmode/pentana/shared/NotificationsStoreTest.kt
git commit -m "fix(shared): match typographic apostrophe in notificationKind"
```

---

## Task 5: Shared BillsStore + BillsDisplay (TDD)

**Files:**
- Create: `shared/src/commonMain/kotlin/my/silentmode/pentana/shared/presentation/BillsStore.kt`
- Create: `shared/src/commonMain/kotlin/my/silentmode/pentana/shared/presentation/BillsDisplay.kt`
- Test: `shared/src/commonTest/kotlin/my/silentmode/pentana/shared/BillsStoreTest.kt`

- [ ] **Step 1: Write the failing tests** — create `BillsStoreTest.kt`:

```kotlin
package my.silentmode.pentana.shared

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.fullPath
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import my.silentmode.pentana.shared.model.BillDto
import my.silentmode.pentana.shared.presentation.BillStatus
import my.silentmode.pentana.shared.presentation.BillsStore
import my.silentmode.pentana.shared.presentation.BillsUiState
import my.silentmode.pentana.shared.presentation.SubmitState
import my.silentmode.pentana.shared.presentation.billStatus
import my.silentmode.pentana.shared.presentation.canSubmitProof
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BillsStoreTest {
    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private val summaryJson = """
        {"data":{"total_outstanding":"120.00","available_credit":"15.00","unpaid_count":2,"bill_count":6}}
    """.trimIndent()

    private val billsJson = """
        {"data":[
        {"id":1,"month":"2026-06","amount_due":"70.00","amount_paid":"0.00","outstanding":"70.00","status":"overdue"},
        {"id":2,"month":"2026-07","amount_due":"70.00","amount_paid":"20.00","outstanding":"50.00","status":"partial"}
        ]}
    """.trimIndent()

    private val proofJson = """
        {"data":{"id":9,"amount_claimed":"50.00","status":"pending","submitted_at":"2026-07-19T10:00:00+08:00"}}
    """.trimIndent()

    private fun makeStore(handler: (String) -> Pair<HttpStatusCode, String>): BillsStore {
        val engine = MockEngine { request ->
            val (status, body) = handler(request.url.fullPath)
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return BillsStore(BillsRepository(ApiClient("https://x/api/v1", InMemoryTokenStore("t"), engine)))
    }

    private fun routing(proofResult: Pair<HttpStatusCode, String>? = null): (String) -> Pair<HttpStatusCode, String> = { path ->
        when {
            path.endsWith("/bills/summary") -> HttpStatusCode.OK to summaryJson
            path.endsWith("/bills") -> HttpStatusCode.OK to billsJson
            path.endsWith("/payment-proofs") -> proofResult ?: (HttpStatusCode.OK to proofJson)
            else -> HttpStatusCode.NotFound to "{}"
        }
    }

    @Test fun load_emits_content_with_summary_and_bills() = runTest {
        val store = makeStore(routing())
        val uiState = store.state.first { it !is BillsUiState.Loading }
        assertIs<BillsUiState.Content>(uiState)
        assertEquals("120.00", uiState.summary.totalOutstanding)
        assertEquals(2, uiState.bills.size)
        assertEquals("2026-06", uiState.bills.first().month)
    }

    @Test fun error_emits_error() = runTest {
        val store = makeStore { _ -> HttpStatusCode.InternalServerError to "{}" }
        val uiState = store.state.first { it !is BillsUiState.Loading }
        assertIs<BillsUiState.Error>(uiState)
        assertEquals("Something went wrong. Pull down to refresh.", uiState.message)
    }

    @Test fun submit_success_transitions_and_refetches() = runTest {
        var billsFetches = 0
        // Second fetch returns updated data so the test can await the refetch landing in state
        // (the refetch runs AFTER Success — Success alone doesn't prove the list was refetched).
        val refetchedBillsJson = billsJson.replace("\"outstanding\":\"70.00\"", "\"outstanding\":\"20.00\"")
        val store = makeStore { path ->
            when {
                path.endsWith("/bills/summary") -> HttpStatusCode.OK to summaryJson
                path.endsWith("/bills") -> {
                    billsFetches += 1
                    HttpStatusCode.OK to (if (billsFetches == 1) billsJson else refetchedBillsJson)
                }
                path.endsWith("/payment-proofs") -> HttpStatusCode.OK to proofJson
                else -> HttpStatusCode.NotFound to "{}"
            }
        }
        store.state.first { it is BillsUiState.Content }
        assertEquals(1, billsFetches)
        store.submitProof(imageBytes = ByteArray(4), fileName = "proof.jpg", amount = "50.00", note = null)
        store.submit.first { it is SubmitState.Success } // instant feedback: Success precedes the refetch
        store.state.first { it is BillsUiState.Content && it.bills.first().outstanding == "20.00" }
        assertEquals(2, billsFetches) // success refetches the list
    }

    @Test fun submit_failure_sets_error_state() = runTest {
        val store = makeStore(routing(proofResult = HttpStatusCode.InternalServerError to "{}"))
        store.state.first { it is BillsUiState.Content }
        store.submitProof(imageBytes = ByteArray(4), fileName = "proof.jpg", amount = "50.00", note = null)
        val submitState = store.submit.first { it is SubmitState.Error }
        assertIs<SubmitState.Error>(submitState)
        assertEquals("Upload failed. Please try again.", submitState.message)
    }

    @Test fun reset_returns_submit_to_idle() = runTest {
        val store = makeStore(routing(proofResult = HttpStatusCode.InternalServerError to "{}"))
        store.state.first { it is BillsUiState.Content }
        store.submitProof(imageBytes = ByteArray(4), fileName = "proof.jpg", amount = "50.00", note = null)
        store.submit.first { it is SubmitState.Error }
        store.resetSubmit()
        assertIs<SubmitState.Idle>(store.submit.value)
    }

    @Test fun submitting_guards_double_submit() = runTest {
        val gate = CompletableDeferred<Unit>()
        var proofRequests = 0
        val engine = MockEngine { request ->
            val path = request.url.fullPath
            val body = when {
                path.endsWith("/bills/summary") -> summaryJson
                path.endsWith("/payment-proofs") -> { proofRequests += 1; gate.await(); proofJson }
                else -> billsJson
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val store = BillsStore(BillsRepository(ApiClient("https://x/api/v1", InMemoryTokenStore("t"), engine)))
        store.state.first { it is BillsUiState.Content }
        store.submitProof(imageBytes = ByteArray(4), fileName = "proof.jpg", amount = "50.00", note = null)
        store.submit.first { it is SubmitState.Submitting }
        store.submitProof(imageBytes = ByteArray(4), fileName = "proof.jpg", amount = "60.00", note = null) // guarded
        gate.complete(Unit)
        store.submit.first { it is SubmitState.Success }
        assertEquals(1, proofRequests)
    }

    @Test fun blank_note_and_padded_amount_still_submit() = runTest {
        var sawProofRequest = false
        val store = makeStore { path ->
            if (path.endsWith("/payment-proofs")) { sawProofRequest = true; HttpStatusCode.OK to proofJson }
            else routing()(path)
        }
        store.state.first { it is BillsUiState.Content }
        store.submitProof(imageBytes = ByteArray(4), fileName = "proof.jpg", amount = " 50.00 ", note = "   ")
        store.submit.first { it is SubmitState.Success }
        assertTrue(sawProofRequest)
    }

    // Derived display

    @Test fun bill_status_maps_all_cases() {
        assertEquals(BillStatus.Paid, billStatus(bill(status = "paid")))
        assertEquals(BillStatus.Partial, billStatus(bill(status = "partial")))
        assertEquals(BillStatus.Overdue, billStatus(bill(status = "overdue")))
        assertEquals(BillStatus.Unpaid, billStatus(bill(status = "unpaid")))
        assertEquals(BillStatus.Unpaid, billStatus(bill(status = "anything-else")))
        assertEquals(BillStatus.Paid, billStatus(bill(status = "PAID"))) // case-insensitive, as both platforms did
    }

    @Test fun can_submit_requires_numeric_amount_and_photo() {
        assertTrue(canSubmitProof("50.00", hasPhoto = true))
        assertTrue(canSubmitProof(" 50.00 ", hasPhoto = true))
        assertFalse(canSubmitProof("50.00", hasPhoto = false))
        assertFalse(canSubmitProof("", hasPhoto = true))
        assertFalse(canSubmitProof("abc", hasPhoto = true)) // unified on iOS's stricter numeric check
    }

    private fun bill(status: String) = BillDto(
        id = 1, month = "2026-06", amountDue = "70.00", amountPaid = "0.00", outstanding = "70.00", status = status,
    )
}
```

- [ ] **Step 2: Run — expect FAIL** (unresolved `BillsStore`/`BillsUiState`/`SubmitState`/`billStatus`/`canSubmitProof`)

Run: `./gradlew :shared:allTests`

- [ ] **Step 3: Implement `BillsDisplay.kt`**

```kotlin
package my.silentmode.pentana.shared.presentation

import my.silentmode.pentana.shared.model.BillDto

/** Shared decision — each platform maps this to its own chip/pill styling. */
enum class BillStatus { Paid, Partial, Overdue, Unpaid }

fun billStatus(bill: BillDto): BillStatus = when {
    bill.status.equals("paid", ignoreCase = true) -> BillStatus.Paid
    bill.status.equals("partial", ignoreCase = true) -> BillStatus.Partial
    bill.status.equals("overdue", ignoreCase = true) -> BillStatus.Overdue
    else -> BillStatus.Unpaid
}

/**
 * Submit is allowed once the amount parses as a number and a photo is attached.
 * Unified on iOS's stricter numeric check (Android previously allowed any non-blank string).
 */
fun canSubmitProof(amount: String, hasPhoto: Boolean): Boolean =
    amount.trim().toDoubleOrNull() != null && hasPhoto
```

- [ ] **Step 4: Implement `BillsStore.kt`**

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
import my.silentmode.pentana.shared.BillsRepository
import my.silentmode.pentana.shared.model.BillDto
import my.silentmode.pentana.shared.model.BillsSummaryDto

sealed interface BillsUiState {
    data object Loading : BillsUiState
    data class Error(val message: String) : BillsUiState
    data class Content(val summary: BillsSummaryDto, val bills: List<BillDto>) : BillsUiState
}

/** Proof-upload state machine driving the submit sheet on both platforms. */
sealed interface SubmitState {
    data object Idle : SubmitState
    data object Submitting : SubmitState
    data class Error(val message: String) : SubmitState
    data object Success : SubmitState
}

/**
 * Shared presentation logic for the Bills screen. Owns its coroutine scope; the host controls
 * teardown. Android calls [clear] from ViewModel.onCleared(); iOS reuses the store across view
 * reappearance and lets it release with the view, so it does not call [clear] on disappear.
 */
class BillsStore(private val repo: BillsRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow<BillsUiState>(BillsUiState.Loading)
    val state: StateFlow<BillsUiState> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private val refreshTracker = RefreshTracker(_refreshing)

    private val _submit = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val submit: StateFlow<SubmitState> = _submit.asStateFlow()

    init { load() }

    fun load() {
        scope.launch {
            _state.value = BillsUiState.Loading
            fetch()
        }
    }

    /** Suspends until the fetch completes so iOS .refreshable can hold the system spinner. */
    suspend fun refresh() = refreshTracker.run { fetch() }

    private suspend fun fetch() {
        _state.value = try {
            BillsUiState.Content(repo.summary(), repo.bills())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            BillsUiState.Error("Something went wrong. Pull down to refresh.")
        }
    }

    /**
     * Uploads a payment proof. [Submitting][SubmitState.Submitting] doubles as the double-submit
     * guard (the sheet is modal — no per-id set needed). Success refetches so the new proof is
     * reflected; the sheet observes [submit] to show progress/error/done.
     */
    fun submitProof(imageBytes: ByteArray, fileName: String, amount: String, note: String?) {
        if (_submit.value is SubmitState.Submitting) return
        if (!canSubmitProof(amount, hasPhoto = true)) return
        _submit.value = SubmitState.Submitting
        scope.launch {
            try {
                repo.submitPaymentProof(imageBytes, fileName, amount.trim(), note?.takeUnless { it.isBlank() })
                _submit.value = SubmitState.Success
                fetch()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _submit.value = SubmitState.Error("Upload failed. Please try again.")
            }
        }
    }

    fun resetSubmit() { _submit.value = SubmitState.Idle }

    fun clear() { scope.cancel() }
}
```

- [ ] **Step 5: Run — expect PASS**

Run: `./gradlew :shared:allTests`

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/my/silentmode/pentana/shared/presentation/BillsStore.kt shared/src/commonMain/kotlin/my/silentmode/pentana/shared/presentation/BillsDisplay.kt shared/src/commonTest/kotlin/my/silentmode/pentana/shared/BillsStoreTest.kt
git commit -m "feat(shared): BillsStore with SubmitState machine + bill display decisions + tests"
```

---

## Task 6: Rewire Android Bills to the shared store

**Files:**
- Modify: `androidApp/src/main/kotlin/my/silentmode/pentana/feature/bills/BillsViewModel.kt`
- Modify: `androidApp/src/main/kotlin/my/silentmode/pentana/feature/bills/BillsScreen.kt`
- Modify: `androidApp/src/main/kotlin/my/silentmode/pentana/feature/bills/SubmitProofSheet.kt`

- [ ] **Step 1: Slim `BillsViewModel.kt`.** Replace the whole file with:

```kotlin
package my.silentmode.pentana.feature.bills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import my.silentmode.pentana.shared.BillsRepository
import my.silentmode.pentana.shared.presentation.BillsStore

class BillsViewModel(repo: BillsRepository) : ViewModel() {
    val store = BillsStore(repo)
    fun refresh() { viewModelScope.launch { store.refresh() } }
    override fun onCleared() { store.clear() }
}
```

(The local `BillsUiState`, `SubmitState`, `canSubmitProof`, `refreshing`/`submit` mutableStates, and all fetch/submit logic are deleted — shared now.)

- [ ] **Step 2: Update `BillsScreen.kt`.** Import changes — replace the implicit local types with:

```kotlin
import my.silentmode.pentana.shared.presentation.BillStatus
import my.silentmode.pentana.shared.presentation.BillsUiState
import my.silentmode.pentana.shared.presentation.billStatus
```

Replace the file-top `billChip(status: String)` with the shared-decision mapper:

```kotlin
private fun billChip(status: BillStatus): ChipKind = when (status) {
    BillStatus.Paid -> ChipKind.Paid
    BillStatus.Partial -> ChipKind.Partial
    BillStatus.Overdue -> ChipKind.Overdue
    BillStatus.Unpaid -> ChipKind.Unpaid
}
```

`BillsScreen` composable becomes:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillsScreen() {
    val vm = appViewModel { BillsViewModel(it.bills) }
    val state by vm.store.state.collectAsStateWithLifecycle()
    val refreshing by vm.store.refreshing.collectAsStateWithLifecycle()
    var showSheet by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
            when (val uiState = state) {
                is BillsUiState.Loading -> LoadingState()
                is BillsUiState.Error -> ErrorState(uiState.message, vm.store::load)
                is BillsUiState.Content -> BillsContent(uiState.summary, uiState.bills)
            }
        }
        SubmitFab(onClick = { showSheet = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp))
    }

    if (showSheet) {
        SubmitProofSheet(store = vm.store, onDismiss = { showSheet = false; vm.store.resetSubmit() })
    }
}
```

In `BillRow`, the `paid` check and chip call change to the shared decision:

```kotlin
    val status = billStatus(bill)
    val paid = status == BillStatus.Paid
    ...
    StatusChip(billChip(status))
```

- [ ] **Step 3: Update `SubmitProofSheet.kt`.** Signature changes from `(vm: BillsViewModel, onDismiss)` to the store + collected submit state; imports add:

```kotlin
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import my.silentmode.pentana.shared.presentation.BillsStore
import my.silentmode.pentana.shared.presentation.SubmitState
import my.silentmode.pentana.shared.presentation.canSubmitProof
```

The composable becomes (form fields/photo picker/layout unchanged — only the state plumbing shown here changes):

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmitProofSheet(store: BillsStore, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val submit by store.submit.collectAsStateWithLifecycle()
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var photo by remember { mutableStateOf<PickedPhoto?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) photo = readPhoto(context, uri)
    }

    LaunchedEffect(submit) {
        if (submit is SubmitState.Success) {
            delay(700)
            onDismiss()
        }
    }
    ...
            (submit as? SubmitState.Error)?.let { submitError ->
                Spacer(Modifier.height(12.dp))
                Text(submitError.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(16.dp))
            PentButton(
                text = if (submit is SubmitState.Success) "Submitted" else "Submit proof",
                onClick = { photo?.let { picked -> store.submitProof(picked.bytes, picked.name, amount, note.ifBlank { null }) } },
                modifier = Modifier.fillMaxWidth(),
                enabled = canSubmitProof(amount, photo != null),
                loading = submit is SubmitState.Submitting,
            )
```

(`canSubmitProof` now comes from shared — note the behavior unification: the button requires a *numeric* amount, matching iOS.)

- [ ] **Step 4: Build**

Run: `./gradlew :androidApp:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add androidApp/src/main/kotlin/my/silentmode/pentana/feature/bills
git commit -m "refactor(android): Bills consumes shared BillsStore + display decisions"
```

---

## Task 7: Rewire iOS Bills to the shared store via SKIE

**Files:**
- Modify: `iosApp/iosApp/SessionStore.swift`
- Modify: `iosApp/iosApp/BillsView.swift`
- Modify: `iosApp/iosApp/SubmitProofView.swift`

- [ ] **Step 1: Vend the store from `SessionStore`** — next to the other factories:

```swift
/// Vend a shared Bills presentation store. Same lifecycle contract as the other factories:
/// held in @State, reused across reappear, not cleared.
func makeBillsStore() -> BillsStore { BillsStore(repo: bills) }
```

- [ ] **Step 2: Rewrite `BillsView`'s state handling.** Change import to `@preconcurrency import Shared`. Replace `@State summary/bills/isLoading` and `load()` with the store pattern (`HomeView.swift` is the template):

```swift
struct BillsView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var store: BillsStore?
    @State private var state: BillsUiState = BillsUiStateLoading.shared
    @State private var showSubmit = false

    var body: some View {
        content
            .task {
                let activeStore = store ?? session.makeBillsStore()
                store = activeStore
                async let states: Void = { for await value in activeStore.state { await MainActor.run { state = value } } }()
                _ = await states
            }
            .sheet(isPresented: $showSubmit) {
                if let store {
                    SubmitProofView(store: store)
                }
            }
    }

    @ViewBuilder private var content: some View {
        switch onEnum(of: state) {
        case .loading:
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        case .error(let error):
            ScrollView {
                EmptyStateView(symbol: "creditcard.fill", tint: Pent.dues, bg: Pent.duesBg,
                               title: "Couldn't load", message: error.message)
                    .containerRelativeFrame(.vertical, alignment: .center)
            }
            .refreshable { try? await store?.refresh() }
        case .content(let content):
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    summaryCard(content.summary)
                        .padding(.top, 4)

                    Button { showSubmit = true } label: {
                        Label("Submit payment proof", systemImage: "camera.fill")
                    }
                    .buttonStyle(PentProminentButtonStyle())
                    .padding(.top, 14)

                    if content.bills.isEmpty {
                        EmptyStateView(symbol: "creditcard.fill", tint: Pent.dues, bg: Pent.duesBg,
                                       title: "No bills yet", message: "When dues are issued they'll appear here.")
                    } else {
                        SectionLabel(text: "Bill history")
                        InsetGroup {
                            ForEach(Array(content.bills.enumerated()), id: \.element.id) { index, bill in
                                BillRow(bill: bill)
                                if index < content.bills.count - 1 { PentHairline(leadingInset: 64) }
                            }
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 28)
            }
            .refreshable { try? await store?.refresh() }
        }
    }
    // summaryCard becomes a function taking the summary: `private func summaryCard(_ summary: BillsSummaryDto) -> some View`
    // — its body drops the `summary?.` optionals (the value is non-nil in Content). statBlock unchanged.
}
```

In `BillRow`, replace `private func pill(_ status: String)` with the shared decision:

```swift
    private var pillKind: PillKind {
        switch billStatus(bill: bill) {
        case .paid: return .paid
        case .partial: return .partial
        case .overdue: return .overdue
        case .unpaid: return .unpaid
        }
    }
```

(call site: `StatusPill(pillKind)`; `monthLabel` date formatting stays native.)

- [ ] **Step 3: Rewire `SubmitProofView` to the store.** It now takes the store instead of calling the repo; the `onSubmitted` callback is deleted (the store refetches internally on success). Change import to `@preconcurrency import Shared`. Replace the state plumbing and `submit()` (form layout, `PhotoTile`, and the `Data.toKotlinByteArray()` extension stay unchanged):

```swift
struct SubmitProofView: View {
    @Environment(\.dismiss) private var dismiss
    let store: BillsStore

    @State private var amount = ""
    @State private var note = ""
    @State private var photoItem: PhotosPickerItem?
    @State private var imageData: Data?
    @State private var submitState: SubmitState = SubmitStateIdle.shared

    private var isSubmitting: Bool { submitState is SubmitStateSubmitting }
    private var errorMessage: String? { (submitState as? SubmitStateError)?.message }
    private var ready: Bool {
        imageData != nil && canSubmitProof(amount: amount, hasPhoto: imageData != nil) && !isSubmitting
    }

    var body: some View {
        NavigationStack {
            // The ScrollView form body is UNCHANGED except the error banner's binding:
            //   old: if let error { ... Text(error) ... }
            //   new: if let errorMessage { ... Text(errorMessage) ... }
            // The submit button keeps using `isSubmitting` / `ready` exactly as before.
        }
        .task {
            for await value in store.submit {
                await MainActor.run { submitState = value }
                if value is SubmitStateSuccess {
                    await MainActor.run {
                        store.resetSubmit()
                        dismiss()
                    }
                }
            }
        }
    }

    private func submit() {
        guard let data = imageData else { return }
        store.submitProof(imageBytes: data.toKotlinByteArray(), fileName: "proof.jpg",
                          amount: amount, note: note.isEmpty ? nil : note)
    }
}
```

Update the error banner to use `errorMessage` (`if let errorMessage { ... Text(errorMessage) ... }`). Delete `@EnvironmentObject session`, `isSubmitting`/`error` @States, and the old async `submit()`.

> SKIE notes: `SubmitState` sealed interface → `SubmitStateIdle.shared`/`SubmitStateSubmitting`/`SubmitStateError`/`SubmitStateSuccess` classes with `is`/`as?` checks (or `onEnum(of:)` — use whichever compiles cleaner, `is` checks shown are fine for a non-exhaustive read). `canSubmitProof(amount:hasPhoto:)` is a global Swift function. `BillStatus` cases lower-camelCase.

- [ ] **Step 4: Build**

Run: `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build`
Expected: `BUILD SUCCEEDED`.

- [ ] **Step 5: Commit**

```bash
git add iosApp/iosApp/SessionStore.swift iosApp/iosApp/BillsView.swift iosApp/iosApp/SubmitProofView.swift
git commit -m "refactor(ios): Bills + proof submission consume shared BillsStore via SKIE"
```

---

## Task 8: Full milestone verification

- [ ] **Step 1: Everything green**

```bash
./gradlew :shared:allTests :androidApp:assembleDebug :androidApp:testDebugUnitTest --rerun-tasks
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build
```

(`:androidApp:testDebugUnitTest` is in the gate because `assembleDebug` never compiles androidApp
test sources — a stale test import of a deleted symbol slipped through Task 6's gate exactly this way.)
Expected: all suites green (Bills suite adds 10 tests; totals rise accordingly on all three targets); both apps build.

- [ ] **Step 2: No duplicated logic remains**

```bash
grep -rn 'status.lowercase()\|status == "paid"\|SubmitState\b' androidApp/src/main/kotlin/my/silentmode/pentana/feature/bills iosApp/iosApp/BillsView.swift iosApp/iosApp/SubmitProofView.swift | grep -v 'shared.presentation\|import' || echo CLEAN
```
Expected: `CLEAN` (or only shared-type usages, to be judged).

- [ ] **Step 3: AppConfig never staged**

Run: `git log main..HEAD --name-only --pretty= | sort -u | grep -c AppConfig || echo NEVER-STAGED`
Expected: `NEVER-STAGED`.

- [ ] **Step 4: STOP — hand to the maintainer** (no push/merge; report test totals, build results, behavior notes: Android submit button now requires numeric amount; iOS spinner restored; iOS sheet keeps upload error visible via shared SubmitState).

---

## Self-review notes

- **Spec/M2 coverage:** BillsStore state + refreshing + SubmitState + submitProof + resetSubmit (T5) · Submitting-as-guard (T5 test) · success-refetch (T5) · shared canSubmitProof + billStatus (T5) · Android rewire incl. sheet (T6) · iOS rewire incl. SubmitProofView gaining error feedback + KotlinByteArray path preserved (T7). Brainstorm addenda: helpers + overlap fix (T1), suspend refresh + platform rewires (T1–T3), apostrophe (T4).
- **Behavior changes, all adjudicated:** numeric-amount validation on Android (unified on iOS's check); iOS spinner held (fix of M1 regression); iOS submit sheet shows shared error copy (was silent local copy — same message text preserved); typographic apostrophe now matches. iOS sheet's `onSubmitted` reload callback deleted — the store's internal refetch covers it.
- **Types verified:** `BillsRepository.summary()/bills()/submitPaymentProof(imageBytes, fileName, amountClaimed, memberNote)`, `BillDto`/`BillsSummaryDto`/`PaymentProofDto` fields, `PickedPhoto(bytes, name, sizeLabel)`, iOS `Data.toKotlinByteArray()` already in SubmitProofView.swift. No invented members.
- **Naming:** all snippets use descriptive names per the standing mandate.
