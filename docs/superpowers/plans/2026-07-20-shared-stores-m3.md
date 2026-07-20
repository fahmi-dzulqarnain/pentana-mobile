# Shared Stores Milestone 3 — GuardedActionRunner + Generation Counter + ActivitiesStore Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reshape the guarded-action helper into a per-store runner class, add a submission generation counter (fixing dismiss-mid-submit stale-state replay in Bills and Activities), then build the shared `ActivitiesStore` + registration-form logic consumed by both platforms — unifying the checkbox wire format, required-checkbox semantics, and payload filtering that currently diverge.

**Architecture:** `GuardedActionRunner(scope, inFlight, actionError)` replaces the 6-param `runGuardedAction` (LunchStore retrofits; ActivitiesStore adopts for `cancel`). Submission machines (`BillsStore.submit`, new `ActivitiesStore.reg`) gate their terminal-state writes on a generation counter bumped by `resetSubmit()`/`resetReg()`, so a completion from an abandoned submission can't replay into the next sheet. `ActivitiesDisplay.kt` carries the card-state/spots/waitlist decisions and the registration-form helpers (`requiredAnswered`, `checkboxValue`, `registrationPayload`, `plainTextBlurb`).

**Tech Stack:** Kotlin 2.1.21, kotlinx-coroutines StateFlow, Ktor MockEngine (tests), SKIE 0.10.13, Compose, SwiftUI.

**Spec:** `docs/superpowers/specs/2026-07-19-shared-presentation-rollout-design.md` (Milestone 3 + brainstorm addenda 2026-07-20: runner class; generation counter both stores)
**Branch:** `feat/shared-stores-m3` (created off merged main)

**Standing rules for every task:**
- Gradle/xcodebuild have no network inside the sandbox — run them with the sandbox disabled.
- iOS builds MUST use `-destination 'platform=iOS Simulator,name=iPhone 17 Pro'` (arm64; generic fails).
- NEVER stage `androidApp/src/main/kotlin/my/silentmode/pentana/core/AppConfig.kt`. `git add` explicit paths only.
- Do not push or merge — the maintainer reviews first.
- **Descriptive variable names only — no single-letter locals/params anywhere, including tests.**
- Android gate is `./gradlew :androidApp:assembleDebug :androidApp:testDebugUnitTest` (assembleDebug alone does not compile test sources).

**Adjudicated behavior unifications (do not "fix" these back):**
1. **Checkbox wire format**: iOS currently submits `"1"/"0"`; the documented contract (RegForm KDoc) and Android use `"true"/"false"`. Unified on `"true"/"false"` — iOS changes.
2. **Required checkbox must be CHECKED**: iOS semantics win; Android previously accepted a required checkbox toggled to `"false"`.
3. **Registration payload drops empty answers** (iOS semantics; Android adopts).
4. **Reg error copy** unified to Android's `"Registration failed. Please check your answers."` (iOS drops " and try again").
5. **Cancel failures get user feedback** via `actionError` (both platforms; previously silent).
6. **Spots label union**: `null → "Open"`, `<= 0 → "Full"`, `1 → "1 spot left"`, `n → "n spots left"` (iOS gains the Full-at-zero guard, Android gains singular pluralization).
7. **Waitlist copy** unified to Android's `"Waitlisted — #N"` (iOS drops " in line"); no-position fallback `"Waitlisted"`.

---

## Task 1: GuardedActionRunner class + LunchStore retrofit (behavior-neutral refactor)

**Files:**
- Modify: `shared/src/commonMain/kotlin/my/silentmode/pentana/shared/presentation/StoreSupport.kt`
- Modify: `shared/src/commonMain/kotlin/my/silentmode/pentana/shared/presentation/LunchStore.kt`

- [ ] **Step 1: Replace `runGuardedAction` with the runner class** in `StoreSupport.kt` (delete the top-level function; the file keeps `RefreshTracker` unchanged):

```kotlin
/**
 * Guarded fire-and-forget per-id actions for a store: synchronous in-flight check-and-set BEFORE
 * launching (a plain Main dispatch would arm the guard a turn too late), failure surfaced via
 * [actionError], id always removed in finally. A guarded duplicate returns before touching
 * [actionError] — it must not wipe an error the user is reading.
 *
 * Callers must invoke [run] from the main thread — the check-and-set is a plain read-modify-write;
 * Main confinement is what makes the guard sound.
 */
internal class GuardedActionRunner(
    private val scope: CoroutineScope,
    private val inFlight: MutableStateFlow<Set<Long>>,
    private val actionError: MutableStateFlow<String?>,
) {
    fun run(id: Long, errorMessage: String, action: suspend () -> Unit) {
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
}
```

- [ ] **Step 2: Retrofit `LunchStore.kt`** — add after the `_actionError` declaration:

```kotlin
    private val actionRunner = GuardedActionRunner(scope, _inFlight, _actionError)
```

and replace the two actions:

```kotlin
    fun choose(lunchId: Long, mealOptionId: Long) =
        actionRunner.run(lunchId, SAVE_CHOICE_ERROR) {
            replace(repo.chooseOption(lunchId, mealOptionId))
        }

    fun notAttending(lunchId: Long) =
        actionRunner.run(lunchId, SAVE_CHOICE_ERROR) {
            replace(repo.markNotAttending(lunchId))
        }
```

- [ ] **Step 3: Run — expect PASS (refactor gate)**

Run: `./gradlew :shared:allTests`
Expected: all green. The LunchStore pin tests (`choose_marks_in_flight_and_guards_duplicate_submits`, `guarded_duplicate_does_not_clear_visible_action_error`, actionError set/dismiss/clear-on-retry) are the behavioral proof — if any fails, the runner deviates from the old function; fix the runner, not the tests.

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/my/silentmode/pentana/shared/presentation/StoreSupport.kt shared/src/commonMain/kotlin/my/silentmode/pentana/shared/presentation/LunchStore.kt
git commit -m "refactor(shared): reshape runGuardedAction into per-store GuardedActionRunner"
```

---

## Task 2: Submission generation counter in BillsStore (TDD)

**Files:**
- Modify: `shared/src/commonMain/kotlin/my/silentmode/pentana/shared/presentation/BillsStore.kt`
- Test: `shared/src/commonTest/kotlin/my/silentmode/pentana/shared/BillsStoreTest.kt`

- [ ] **Step 1: Write the failing test** — add to `BillsStoreTest` (imports already present: `CompletableDeferred`):

```kotlin
@Test fun reset_discards_late_completion_of_abandoned_submission() = runTest {
    val gate = CompletableDeferred<Unit>()
    val store = makeStore { path ->
        when {
            path.endsWith("/bills/summary") -> HttpStatusCode.OK to summaryJson
            path.endsWith("/bills") -> HttpStatusCode.OK to billsJson
            path.endsWith("/payment-proofs") -> { gate.await(); HttpStatusCode.OK to proofJson }
            else -> HttpStatusCode.NotFound to "{}"
        }
    }
    store.state.first { it is BillsUiState.Content }
    store.submitProof(imageBytes = ByteArray(4), fileName = "proof.jpg", amount = "50.00", note = null)
    store.submit.first { it is SubmitState.Submitting }
    store.resetSubmit() // user dismissed the sheet mid-upload
    assertIs<SubmitState.Idle>(store.submit.value)
    gate.complete(Unit) // the abandoned upload now completes
    // Await the post-completion refetch landing (the upload DID happen — the list must refresh)…
    store.state.first { it is BillsUiState.Content }
    // …but the stale Success must NOT replay into the submit flow.
    assertIs<SubmitState.Idle>(store.submit.value)
}
```

Note: with the gate released, the store's post-success `fetch()` runs; awaiting Content then asserting Idle needs the fetch to have settled. Make the assertion robust by first awaiting the refetch via a distinguishable payload (same technique as `submit_success_transitions_and_refetches`): serve `billsJson.replace("\"outstanding\":\"70.00\"", "\"outstanding\":\"20.00\"")` for the second-and-later `/bills` fetches, and await `store.state.first { it is BillsUiState.Content && it.bills.first().outstanding == "20.00" }` before the final `assertIs<SubmitState.Idle>`.

- [ ] **Step 2: Run — expect FAIL**: `./gradlew :shared:allTests` — the final assertion sees `SubmitState.Success` (stale completion landed after reset).

- [ ] **Step 3: Implement the generation gate** in `BillsStore.kt`:

Add a field after `_submit`:
```kotlin
    /** Bumped by [resetSubmit]; a completion from an older generation must not write [submit]. */
    private var submitGeneration = 0
```

`submitProof` captures and checks it (the refetch still runs unconditionally — the upload happened):
```kotlin
    fun submitProof(imageBytes: ByteArray, fileName: String, amount: String, note: String?) {
        if (_submit.value is SubmitState.Submitting) return
        if (!canSubmitProof(amount, hasPhoto = true)) return
        val generation = ++submitGeneration
        _submit.value = SubmitState.Submitting
        scope.launch {
            try {
                repo.submitPaymentProof(imageBytes, fileName, amount.trim(), note?.takeUnless { it.isBlank() })
                if (generation == submitGeneration) _submit.value = SubmitState.Success
                fetch()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                if (generation == submitGeneration) _submit.value = SubmitState.Error("Upload failed. Please try again.")
            }
        }
    }

    fun resetSubmit() {
        submitGeneration += 1
        _submit.value = SubmitState.Idle
    }
```

(Keep the existing `submitProof` KDoc; append: "A [resetSubmit] while the upload is in flight abandons it — the eventual completion still refetches, but no longer writes [submit].")

- [ ] **Step 4: Run — expect PASS**: `./gradlew :shared:allTests` — new test green, all 13 Bills tests green (existing resubmit/refetch/guard pins unaffected).

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/my/silentmode/pentana/shared/presentation/BillsStore.kt shared/src/commonTest/kotlin/my/silentmode/pentana/shared/BillsStoreTest.kt
git commit -m "fix(shared): generation counter discards late completions of abandoned proof uploads"
```

---

## Task 3: ActivitiesDisplay — card decisions + registration-form helpers (TDD)

**Files:**
- Create: `shared/src/commonMain/kotlin/my/silentmode/pentana/shared/presentation/ActivitiesDisplay.kt`
- Test: `shared/src/commonTest/kotlin/my/silentmode/pentana/shared/ActivitiesDisplayTest.kt`

- [ ] **Step 1: Write the failing tests** — create `ActivitiesDisplayTest.kt`:

```kotlin
package my.silentmode.pentana.shared

import my.silentmode.pentana.shared.model.ActivityDto
import my.silentmode.pentana.shared.model.QuestionDto
import my.silentmode.pentana.shared.presentation.ActivityCardState
import my.silentmode.pentana.shared.presentation.activityCardState
import my.silentmode.pentana.shared.presentation.checkboxValue
import my.silentmode.pentana.shared.presentation.plainTextBlurb
import my.silentmode.pentana.shared.presentation.registrationPayload
import my.silentmode.pentana.shared.presentation.requiredAnswered
import my.silentmode.pentana.shared.presentation.spotsLabel
import my.silentmode.pentana.shared.presentation.waitlistLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActivitiesDisplayTest {

    // Card state (order: registered → waitlisted → closed → open; matches both platforms today)

    @Test fun card_state_registered_wins() {
        assertEquals(ActivityCardState.Registered, activityCardState(activity(myStatus = "registered", isOpen = false)))
    }

    @Test fun card_state_waitlisted() {
        assertEquals(ActivityCardState.Waitlisted, activityCardState(activity(myStatus = "waitlisted", isOpen = true)))
    }

    @Test fun card_state_closed_when_not_open() {
        assertEquals(ActivityCardState.Closed, activityCardState(activity(myStatus = "none", isOpen = false)))
    }

    @Test fun card_state_open() {
        assertEquals(ActivityCardState.Open, activityCardState(activity(myStatus = "none", isOpen = true)))
    }

    // Spots label (union semantics: null=unlimited, zero-guard, singular/plural)

    @Test fun spots_label_cases() {
        assertEquals("Open", spotsLabel(activity(spotsLeft = null)))
        assertEquals("Full", spotsLabel(activity(spotsLeft = 0)))
        assertEquals("Full", spotsLabel(activity(spotsLeft = -1)))
        assertEquals("1 spot left", spotsLabel(activity(spotsLeft = 1)))
        assertEquals("4 spots left", spotsLabel(activity(spotsLeft = 4)))
    }

    // Waitlist label (unified: no " in line" suffix)

    @Test fun waitlist_label_with_and_without_position() {
        assertEquals("Waitlisted — #3", waitlistLabel(activity(waitlistPosition = 3)))
        assertEquals("Waitlisted", waitlistLabel(activity(waitlistPosition = null)))
    }

    // Registration form helpers

    @Test fun required_text_must_be_non_blank() {
        val questions = listOf(QuestionDto(key = "name", label = "Name", required = true))
        assertFalse(requiredAnswered(questions, emptyMap()))
        assertFalse(requiredAnswered(questions, mapOf("name" to "   ")))
        assertTrue(requiredAnswered(questions, mapOf("name" to "Aisyah")))
    }

    @Test fun required_checkbox_must_be_checked() {
        val questions = listOf(QuestionDto(key = "consent", label = "Consent", type = "checkbox", required = true))
        assertFalse(requiredAnswered(questions, emptyMap()))
        assertFalse(requiredAnswered(questions, mapOf("consent" to "false"))) // toggled off is NOT answered
        assertTrue(requiredAnswered(questions, mapOf("consent" to "true")))
    }

    @Test fun optional_questions_never_block() {
        val questions = listOf(QuestionDto(key = "diet", label = "Diet", required = false))
        assertTrue(requiredAnswered(questions, emptyMap()))
    }

    @Test fun checkbox_encodes_documented_wire_format() {
        assertEquals("true", checkboxValue(true))
        assertEquals("false", checkboxValue(false))
    }

    @Test fun payload_drops_empty_answers() {
        assertEquals(
            mapOf("name" to "Aisyah", "consent" to "false"),
            registrationPayload(mapOf("name" to "Aisyah", "diet" to "", "consent" to "false")),
        )
    }

    // Blurb

    @Test fun blurb_strips_html_and_collapses_whitespace() {
        assertEquals("Bring water and shoes.", plainTextBlurb("<p>Bring   water</p> and&nbsp;<b>shoes</b>."))
    }

    @Test fun blurb_is_null_for_empty_input() {
        assertNull(plainTextBlurb(null))
        assertNull(plainTextBlurb(""))
        assertNull(plainTextBlurb("<p>  </p>"))
    }

    private fun activity(
        myStatus: String = "none",
        isOpen: Boolean = true,
        spotsLeft: Int? = null,
        waitlistPosition: Int? = null,
    ) = ActivityDto(
        id = 1, title = "Beach Cleanup", description = null, startsAt = null, location = null,
        spotsLeft = spotsLeft, isOpen = isOpen, myStatus = myStatus, waitlistPosition = waitlistPosition,
        questions = emptyList(),
    )
}
```

- [ ] **Step 2: Run — expect FAIL** (unresolved references): `./gradlew :shared:allTests`

- [ ] **Step 3: Implement `ActivitiesDisplay.kt`:**

```kotlin
package my.silentmode.pentana.shared.presentation

import my.silentmode.pentana.shared.model.ActivityDto
import my.silentmode.pentana.shared.model.QuestionDto

/** Shared decision — each platform maps this to its own card styling. */
enum class ActivityCardState { Registered, Waitlisted, Open, Closed }

fun activityCardState(activity: ActivityDto): ActivityCardState = when {
    activity.myStatus == "registered" -> ActivityCardState.Registered
    activity.myStatus == "waitlisted" -> ActivityCardState.Waitlisted
    !activity.isOpen -> ActivityCardState.Closed
    else -> ActivityCardState.Open
}

/** Chip copy for an open activity's capacity (null spotsLeft = unlimited). */
fun spotsLabel(activity: ActivityDto): String {
    val spotsLeft = activity.spotsLeft ?: return "Open"
    return when {
        spotsLeft <= 0 -> "Full"
        spotsLeft == 1 -> "1 spot left"
        else -> "$spotsLeft spots left"
    }
}

fun waitlistLabel(activity: ActivityDto): String =
    activity.waitlistPosition?.let { position -> "Waitlisted — #$position" } ?: "Waitlisted"

/**
 * True when every required question is answered: non-blank for text/textarea/select, and
 * CHECKED for checkboxes (a required checkbox toggled off is not an answer — consent semantics).
 */
fun requiredAnswered(questions: List<QuestionDto>, answers: Map<String, String>): Boolean =
    questions.filter { it.required }.all { question ->
        val answer = answers[question.key] ?: ""
        if (question.type == "checkbox") answer == "true" else answer.isNotBlank()
    }

/** Checkbox answers are submitted as "true"/"false" strings (the documented API contract). */
fun checkboxValue(checked: Boolean): String = if (checked) "true" else "false"

/** The request payload: answered questions only — empty strings are unanswered, not answers. */
fun registrationPayload(answers: Map<String, String>): Map<String, String> =
    answers.filterValues { it.isNotEmpty() }

/** HTML description → plain-text blurb (tags stripped, entities/whitespace collapsed), null if empty. */
fun plainTextBlurb(html: String?): String? {
    if (html.isNullOrEmpty()) return null
    val plain = html
        .replace(Regex("<[^>]*>"), " ")
        .replace("&nbsp;", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    return plain.ifEmpty { null }
}
```

- [ ] **Step 4: Run — expect PASS**: `./gradlew :shared:allTests`

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/my/silentmode/pentana/shared/presentation/ActivitiesDisplay.kt shared/src/commonTest/kotlin/my/silentmode/pentana/shared/ActivitiesDisplayTest.kt
git commit -m "feat(shared): activity card decisions + registration-form helpers + tests"
```

---

## Task 4: Shared ActivitiesStore (TDD)

**Files:**
- Create: `shared/src/commonMain/kotlin/my/silentmode/pentana/shared/presentation/ActivitiesStore.kt`
- Test: `shared/src/commonTest/kotlin/my/silentmode/pentana/shared/ActivitiesStoreTest.kt`

- [ ] **Step 1: Write the failing tests** — create `ActivitiesStoreTest.kt`:

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
import my.silentmode.pentana.shared.presentation.ActivitiesStore
import my.silentmode.pentana.shared.presentation.ActivitiesUiState
import my.silentmode.pentana.shared.presentation.RegState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class ActivitiesStoreTest {
    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private fun activityJson(id: Long, myStatus: String) = """
        {"id":$id,"title":"Beach Cleanup","description":"<p>Bring water</p>","starts_at":"2026-07-25T08:00:00+08:00",
        "location":"Port Dickson","spots_left":4,"is_open":true,"my_status":"$myStatus","waitlist_position":null,
        "questions":[{"key":"name","label":"Name","type":"text","required":true}]}
    """.trimIndent()

    private val listJson = """{"data":[${activityJson(1, "none")},${activityJson(2, "none")}]}"""
    private val registeredJson = """{"data":${activityJson(1, "registered")}}"""
    private val cancelledJson = """{"data":${activityJson(1, "none")}}"""

    private fun makeStore(handler: (String) -> Pair<HttpStatusCode, String>): ActivitiesStore {
        val engine = MockEngine { request ->
            val (status, body) = handler(request.url.fullPath)
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return ActivitiesStore(ActivitiesRepository(ApiClient("https://x/api/v1", InMemoryTokenStore("t"), engine)))
    }

    private fun routing(
        registerResult: Pair<HttpStatusCode, String>? = null,
        cancelResult: Pair<HttpStatusCode, String>? = null,
    ): (String) -> Pair<HttpStatusCode, String> = { path ->
        when {
            path.endsWith("/register") -> registerResult ?: (HttpStatusCode.OK to registeredJson)
            path.endsWith("/cancel") -> cancelResult ?: (HttpStatusCode.OK to cancelledJson)
            path.endsWith("/activities") -> HttpStatusCode.OK to listJson
            else -> HttpStatusCode.NotFound to "{}"
        }
    }

    @Test fun load_emits_content() = runTest {
        val store = makeStore(routing())
        val uiState = store.state.first { it !is ActivitiesUiState.Loading }
        assertIs<ActivitiesUiState.Content>(uiState)
        assertEquals(2, uiState.activities.size)
    }

    @Test fun error_emits_error() = runTest {
        val store = makeStore { _ -> HttpStatusCode.InternalServerError to "{}" }
        val uiState = store.state.first { it !is ActivitiesUiState.Loading }
        assertIs<ActivitiesUiState.Error>(uiState)
        assertEquals("Something went wrong. Pull down to refresh.", uiState.message)
    }

    @Test fun register_success_replaces_activity_and_reaches_success() = runTest {
        val store = makeStore(routing())
        store.state.first { it is ActivitiesUiState.Content }
        store.register(activityId = 1, answers = mapOf("name" to "Aisyah"))
        store.reg.first { it is RegState.Success }
        val uiState = store.state.first { it is ActivitiesUiState.Content }
        assertIs<ActivitiesUiState.Content>(uiState)
        assertEquals("registered", uiState.activities.first { it.id == 1L }.myStatus)
        assertEquals("none", uiState.activities.first { it.id == 2L }.myStatus) // untouched
    }

    @Test fun register_failure_sets_reg_error() = runTest {
        val store = makeStore(routing(registerResult = HttpStatusCode.UnprocessableEntity to "{}"))
        store.state.first { it is ActivitiesUiState.Content }
        store.register(activityId = 1, answers = mapOf("name" to "Aisyah"))
        val regState = store.reg.first { it is RegState.Error }
        assertIs<RegState.Error>(regState)
        assertEquals("Registration failed. Please check your answers.", regState.message)
    }

    @Test fun register_marks_in_flight_for_card_dimming_and_guards_duplicates() = runTest {
        val gate = CompletableDeferred<Unit>()
        var registerRequests = 0
        val engine = MockEngine { request ->
            val path = request.url.fullPath
            val body = when {
                path.endsWith("/register") -> { registerRequests += 1; gate.await(); registeredJson }
                path.endsWith("/activities") -> listJson
                else -> "{}"
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val store = ActivitiesStore(ActivitiesRepository(ApiClient("https://x/api/v1", InMemoryTokenStore("t"), engine)))
        store.state.first { it is ActivitiesUiState.Content }
        store.register(activityId = 1, answers = mapOf("name" to "Aisyah"))
        store.inFlight.first { 1L in it } // request pending — card dims
        store.register(activityId = 1, answers = mapOf("name" to "Aisyah")) // guarded duplicate
        gate.complete(Unit)
        store.inFlight.first { it.isEmpty() }
        assertEquals(1, registerRequests)
    }

    @Test fun reset_discards_late_completion_of_abandoned_registration() = runTest {
        val gate = CompletableDeferred<Unit>()
        val engine = MockEngine { request ->
            val path = request.url.fullPath
            val body = when {
                path.endsWith("/register") -> { gate.await(); registeredJson }
                path.endsWith("/activities") -> listJson
                else -> "{}"
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val store = ActivitiesStore(ActivitiesRepository(ApiClient("https://x/api/v1", InMemoryTokenStore("t"), engine)))
        store.state.first { it is ActivitiesUiState.Content }
        store.register(activityId = 1, answers = mapOf("name" to "Aisyah"))
        store.reg.first { it is RegState.Submitting }
        store.resetReg() // sheet dismissed mid-flight
        assertIs<RegState.Idle>(store.reg.value)
        gate.complete(Unit)
        store.inFlight.first { it.isEmpty() }
        // The registration DID happen server-side: the optimistic replace still lands…
        assertEquals("registered", (store.state.value as ActivitiesUiState.Content).activities.first { it.id == 1L }.myStatus)
        // …but the stale Success must not replay into the reg flow.
        assertIs<RegState.Idle>(store.reg.value)
    }

    @Test fun cancel_failure_sets_action_error_and_cancel_success_replaces() = runTest {
        var failCancel = true
        val store = makeStore { path ->
            when {
                path.endsWith("/cancel") -> {
                    if (failCancel) { failCancel = false; HttpStatusCode.InternalServerError to "{}" } else HttpStatusCode.OK to cancelledJson
                }
                path.endsWith("/activities") -> HttpStatusCode.OK to listJson
                else -> HttpStatusCode.NotFound to "{}"
            }
        }
        store.state.first { it is ActivitiesUiState.Content }
        store.cancel(activityId = 1)
        store.inFlight.first { it.isEmpty() }
        assertEquals("Couldn't cancel. Please try again.", store.actionError.value)
        store.cancel(activityId = 1) // retry clears the error as it starts
        store.inFlight.first { it.isEmpty() }
        assertNull(store.actionError.value)
    }

    @Test fun refresh_failure_replaces_content_with_error() = runTest {
        var fail = false
        val store = makeStore { path ->
            if (fail) HttpStatusCode.InternalServerError to "{}" else routing()(path)
        }
        store.state.first { it is ActivitiesUiState.Content }
        fail = true
        store.refresh()
        assertIs<ActivitiesUiState.Error>(store.state.value)
        assertFalse(store.refreshing.value)
    }
}
```

- [ ] **Step 2: Run — expect FAIL** (unresolved `ActivitiesStore`/`ActivitiesUiState`/`RegState`): `./gradlew :shared:allTests`

- [ ] **Step 3: Implement `ActivitiesStore.kt`:**

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
import my.silentmode.pentana.shared.ActivitiesRepository
import my.silentmode.pentana.shared.model.ActivityDto

sealed interface ActivitiesUiState {
    data object Loading : ActivitiesUiState
    data class Error(val message: String) : ActivitiesUiState
    data class Content(val activities: List<ActivityDto>) : ActivitiesUiState
}

/** Registration state machine driving the question sheet on both platforms. */
sealed interface RegState {
    data object Idle : RegState
    data object Submitting : RegState
    data class Error(val message: String) : RegState
    data object Success : RegState
}

/**
 * Shared presentation logic for the Activities screen. Owns its coroutine scope; the host controls
 * teardown. Android calls [clear] from ViewModel.onCleared(); iOS reuses the store across view
 * reappearance and lets it release with the view, so it does not call [clear] on disappear.
 */
class ActivitiesStore(private val repo: ActivitiesRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow<ActivitiesUiState>(ActivitiesUiState.Loading)
    val state: StateFlow<ActivitiesUiState> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private val refreshTracker = RefreshTracker(_refreshing)

    /** Activity ids with a register/cancel request in flight — drives per-card pending UI + the tap-guard. */
    private val _inFlight = MutableStateFlow<Set<Long>>(emptySet())
    val inFlight: StateFlow<Set<Long>> = _inFlight.asStateFlow()

    /** Set when a fire-and-forget action fails; the UI shows it natively (Snackbar / alert). */
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()
    private val actionRunner = GuardedActionRunner(scope, _inFlight, _actionError)

    private val _reg = MutableStateFlow<RegState>(RegState.Idle)
    val reg: StateFlow<RegState> = _reg.asStateFlow()

    /** Bumped by [resetReg]; a completion from an older generation must not write [reg]. */
    private var regGeneration = 0

    init { load() }

    fun load() {
        scope.launch {
            _state.value = ActivitiesUiState.Loading
            fetch()
        }
    }

    /** Suspends until the fetch completes so iOS .refreshable can hold the system spinner. */
    suspend fun refresh() = refreshTracker.run { fetch() }

    private suspend fun fetch() {
        _state.value = try {
            ActivitiesUiState.Content(repo.activities())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            ActivitiesUiState.Error("Something went wrong. Pull down to refresh.")
        }
    }

    /**
     * Registers for [activityId] with the sheet's [answers] (pass them raw; empty answers are
     * dropped here via [registrationPayload]). Drives BOTH the [reg] machine (sheet feedback)
     * and [inFlight] (card dimming for no-questions registrations — replaces iOS's busyId).
     * A [resetReg] while in flight abandons the submission: the optimistic replace still lands,
     * but the stale terminal state no longer writes [reg].
     *
     * Callers must invoke this from the main thread — the guards are plain read-modify-writes.
     */
    fun register(activityId: Long, answers: Map<String, String>) {
        if (_reg.value is RegState.Submitting) return
        if (activityId in _inFlight.value) return
        _inFlight.value = _inFlight.value + activityId
        val generation = ++regGeneration
        _reg.value = RegState.Submitting
        scope.launch {
            try {
                replace(repo.register(activityId, registrationPayload(answers)))
                if (generation == regGeneration) _reg.value = RegState.Success
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                if (generation == regGeneration) _reg.value = RegState.Error("Registration failed. Please check your answers.")
            } finally {
                _inFlight.value = _inFlight.value - activityId
            }
        }
    }

    fun cancel(activityId: Long) =
        actionRunner.run(activityId, "Couldn't cancel. Please try again.") {
            replace(repo.cancel(activityId))
        }

    fun resetReg() {
        regGeneration += 1
        _reg.value = RegState.Idle
    }

    fun dismissActionError() { _actionError.value = null }

    private fun replace(updated: ActivityDto) {
        val current = (_state.value as? ActivitiesUiState.Content)?.activities ?: return
        _state.value = ActivitiesUiState.Content(current.map { if (it.id == updated.id) updated else it })
    }

    fun clear() { scope.cancel() }
}
```

- [ ] **Step 4: Run — expect PASS**: `./gradlew :shared:allTests`

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/my/silentmode/pentana/shared/presentation/ActivitiesStore.kt shared/src/commonTest/kotlin/my/silentmode/pentana/shared/ActivitiesStoreTest.kt
git commit -m "feat(shared): ActivitiesStore with reg machine, generation gate, guarded cancel + tests"
```

---

## Task 5: Rewire Android Activities to the shared store

**Files:**
- Modify: `androidApp/src/main/kotlin/my/silentmode/pentana/feature/activities/ActivitiesViewModel.kt`
- Modify: `androidApp/src/main/kotlin/my/silentmode/pentana/feature/activities/ActivitiesScreen.kt`
- Modify: `androidApp/src/main/kotlin/my/silentmode/pentana/feature/activities/RegistrationSheet.kt`
- Delete: `androidApp/src/main/kotlin/my/silentmode/pentana/feature/activities/RegForm.kt`
- Delete: `androidApp/src/test/kotlin/my/silentmode/pentana/RegFormTest.kt` (superseded by `ActivitiesDisplayTest`'s stricter coverage — same rationale as SubmitProofValidationTest in M2)

- [ ] **Step 1: Slim `ActivitiesViewModel.kt`.** Replace the whole file with:

```kotlin
package my.silentmode.pentana.feature.activities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import my.silentmode.pentana.shared.ActivitiesRepository
import my.silentmode.pentana.shared.presentation.ActivitiesStore

class ActivitiesViewModel(repo: ActivitiesRepository) : ViewModel() {
    val store = ActivitiesStore(repo)
    fun refresh() { viewModelScope.launch { store.refresh() } }
    override fun onCleared() { store.clear() }
}
```

- [ ] **Step 2: Update `ActivitiesScreen.kt`.** Imports to add:

```kotlin
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import my.silentmode.pentana.shared.presentation.ActivitiesUiState
import my.silentmode.pentana.shared.presentation.ActivityCardState
import my.silentmode.pentana.shared.presentation.activityCardState
import my.silentmode.pentana.shared.presentation.plainTextBlurb
import my.silentmode.pentana.shared.presentation.spotsLabel
import my.silentmode.pentana.shared.presentation.waitlistLabel
```

The screen composable (Snackbar wiring copied from `LunchScreen.kt` — cancel failures now get feedback):

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivitiesScreen() {
    val vm = appViewModel { ActivitiesViewModel(it.activities) }
    val state by vm.store.state.collectAsStateWithLifecycle()
    val refreshing by vm.store.refreshing.collectAsStateWithLifecycle()
    val inFlight by vm.store.inFlight.collectAsStateWithLifecycle()
    val actionError by vm.store.actionError.collectAsStateWithLifecycle()
    var sheetActivity by remember { mutableStateOf<ActivityDto?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(actionError) {
        actionError?.let { message ->
            snackbarHostState.showSnackbar(message)
            vm.store.dismissActionError()
        }
    }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        when (val uiState = state) {
            is ActivitiesUiState.Loading -> LoadingState()
            is ActivitiesUiState.Error -> ErrorState(uiState.message, vm.store::load)
            is ActivitiesUiState.Content -> if (uiState.activities.isEmpty()) {
                ActivitiesEmpty()
            } else {
                ActivitiesList(
                    activities = uiState.activities,
                    inFlight = inFlight,
                    onRegister = { activity ->
                        if (activity.questions.isNotEmpty()) { vm.store.resetReg(); sheetActivity = activity } else vm.store.register(activity.id, emptyMap())
                    },
                    onCancel = { vm.store.cancel(it) },
                )
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }

    sheetActivity?.let { activity ->
        RegistrationSheet(activity = activity, store = vm.store, onDismiss = { sheetActivity = null; vm.store.resetReg() })
    }
}
```

`ActivitiesList` gains the `inFlight` parameter and passes per-card busy state; `ActivityCard` gains `busy: Boolean` and dims/disables its action row exactly as `LunchCard` does (`Modifier.alpha(if (busy) 0.6f else 1f)` on the action row + `enabled = !busy` on the buttons — pass `enabled` through `PentButton`):

```kotlin
@Composable
private fun ActivitiesList(activities: List<ActivityDto>, inFlight: Set<Long>, onRegister: (ActivityDto) -> Unit, onCancel: (Long) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        activities.forEach { ActivityCard(it, busy = it.id in inFlight, onRegister, onCancel) }
    }
}
```

Replace the decision sites with shared calls:
- `SpotsChip` switches on `activityCardState(activity)`: `Registered → StatusChip(ChipKind.Registered)`, `Waitlisted → StatusChip(ChipKind.Waitlisted, label = "Full")`, `Open/Closed → StatusChip(ChipKind.Open, label = spotsLabel(activity))`.
- `ActionRow` switches on the same `activityCardState(activity)` value (compute once in `ActivityCard` and pass down): `Registered`/`Waitlisted`/`Closed`/`Open` branches keep their current rendering; the waitlisted text becomes `waitlistLabel(activity)`.
- The description blurb becomes `plainTextBlurb(activity.description)?.let { blurb -> Text(excerptOf(blurb), …) }` — see Step 3 note on `excerpt`.

- [ ] **Step 3: Keep Android's truncation native but delete the duplicated strip logic.** In `androidApp/src/main/kotlin/my/silentmode/pentana/core/Format.kt`, `excerpt` currently strips HTML AND truncates. Refactor it to delegate the strip to shared and keep only truncation:

```kotlin
import my.silentmode.pentana.shared.presentation.plainTextBlurb

/** Plain-text excerpt of a rich-text [text]: shared HTML-strip + native length cap. */
fun excerpt(text: String, max: Int = 140): String {
    val plain = plainTextBlurb(text) ?: return ""
    return if (plain.length <= max) plain else plain.take(max).trimEnd() + "…"
}
```

(`ActivitiesScreen`/`RegistrationSheet` keep calling `excerpt(...)` — no call-site changes needed; the `plainTextBlurb(...)?.let` form in Step 2 is NOT required if `excerpt` delegates — keep the existing `activity.description?.let { Text(excerpt(it), …) }` call sites as they are.)

- [ ] **Step 4: Update `RegistrationSheet.kt`.** Signature `(activity, store: ActivitiesStore, onDismiss)`; imports swap `vm` types for:

```kotlin
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import my.silentmode.pentana.shared.presentation.ActivitiesStore
import my.silentmode.pentana.shared.presentation.RegState
import my.silentmode.pentana.shared.presentation.checkboxValue
import my.silentmode.pentana.shared.presentation.requiredAnswered
```

Body changes (form fields/QuestionField unchanged except the import source of `checkboxValue`):

```kotlin
fun RegistrationSheet(activity: ActivityDto, store: ActivitiesStore, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val answers = remember { mutableStateMapOf<String, String>() }
    val reg by store.reg.collectAsStateWithLifecycle()

    LaunchedEffect(reg) { if (reg is RegState.Success) onDismiss() }
    ...
            (reg as? RegState.Error)?.let { regError ->
                Text(regError.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
            }

            PentButton(
                text = "Register",
                onClick = { store.register(activity.id, answers.toMap()) },
                modifier = Modifier.fillMaxWidth(),
                variant = BtnVariant.Filled,
                enabled = requiredAnswered(activity.questions, answers) && reg !is RegState.Submitting && reg !is RegState.Success,
                loading = reg is RegState.Submitting,
            )
```

(Note the added `&& reg !is RegState.Success` — same duplicate-fire guard the Bills sheet gained in M2. Behavior note: `requiredAnswered` now requires a required checkbox to be CHECKED — adjudicated unification #2.)

- [ ] **Step 5: Delete the superseded files:**

```bash
git rm androidApp/src/main/kotlin/my/silentmode/pentana/feature/activities/RegForm.kt androidApp/src/test/kotlin/my/silentmode/pentana/RegFormTest.kt
```

- [ ] **Step 6: Build + test gate:**

Run: `./gradlew :androidApp:assembleDebug :androidApp:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, remaining unit tests (FormatTest) green.

- [ ] **Step 7: Commit**

```bash
git add androidApp/src/main/kotlin/my/silentmode/pentana/feature/activities androidApp/src/main/kotlin/my/silentmode/pentana/core/Format.kt
git commit -m "refactor(android): Activities consumes shared store + display/registration helpers"
```

---

## Task 6: Rewire iOS Activities to the shared store via SKIE

**Files:**
- Modify: `iosApp/iosApp/SessionStore.swift`
- Modify: `iosApp/iosApp/ActivitiesView.swift`
- Modify: `iosApp/iosApp/RegisterActivityView.swift`

- [ ] **Step 1: Vend the store** — in `SessionStore.swift`, next to the other factories:

```swift
/// Vend a shared Activities presentation store. Same lifecycle contract as the other factories:
/// held in @State, reused across reappear, not cleared.
func makeActivitiesStore() -> ActivitiesStore { ActivitiesStore(repo: activities) }
```

- [ ] **Step 2: Rewrite `ActivitiesView`** on the established template (`HomeView`/`LunchView`). Change import to `@preconcurrency import Shared`. Replace `@State activities/isLoading/busyId` and `load/register/cancel/replace`:

```swift
struct ActivitiesView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var store: ActivitiesStore?
    @State private var state: ActivitiesUiState = ActivitiesUiStateLoading.shared
    @State private var inFlight: Set<Int64> = []
    @State private var actionError: String?
    @State private var registering: ActivityDto?

    var body: some View {
        content
            .task {
                let activeStore = store ?? session.makeActivitiesStore()
                store = activeStore
                async let states: Void = { for await value in activeStore.state { await MainActor.run { state = value } } }()
                async let flights: Void = { for await ids in activeStore.inFlight { await MainActor.run { inFlight = Set(ids.map { $0.int64Value }) } } }()
                async let errors: Void = { for await message in activeStore.actionError { await MainActor.run { actionError = message } } }()
                _ = await (states, flights, errors)
            }
            .alert(
                "Something went wrong",
                isPresented: Binding(get: { actionError != nil }, set: { if !$0 { actionError = nil; store?.dismissActionError() } })
            ) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(actionError ?? "")
            }
            .sheet(item: $registering, onDismiss: { store?.resetReg() }) { activity in
                if let store {
                    RegisterActivityView(store: store, activity: activity)
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
                EmptyStateView(symbol: "calendar", tint: Pent.activ, bg: Pent.activBg,
                               title: "Couldn't load", message: error.message)
                    .containerRelativeFrame(.vertical, alignment: .center)
            }
            .refreshable { try? await store?.refresh() }
        case .content(let content):
            ScrollView {
                if content.activities.isEmpty {
                    EmptyStateView(symbol: "calendar", tint: Pent.activ, bg: Pent.activBg,
                                   title: "No upcoming activities", message: "Check back soon for events to join.")
                        .containerRelativeFrame(.vertical, alignment: .center)
                } else {
                    VStack(spacing: 13) {
                        ForEach(content.activities, id: \.id) { activity in
                            ActivityCard(activity: activity, busy: inFlight.contains(activity.id),
                                         onRegister: {
                                             if activity.questions.isEmpty {
                                                 store?.register(activityId: activity.id, answers: [:])
                                             } else {
                                                 store?.resetReg()
                                                 registering = activity
                                             }
                                         },
                                         onCancel: { store?.cancel(activityId: activity.id) })
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.bottom, 28)
                }
            }
            .refreshable { try? await store?.refresh() }
        }
    }
}
```

In `ActivityCard`, delete the local `State` enum and its `state` computed property; use the shared decision (SKIE cases lower-camelCased):

```swift
    private var cardState: ActivityCardState { activityCardState(activity: activity) }
```

- `body`'s switch sites map `.registered/.waitlisted/.open/.closed`.
- `spotsPill` becomes: `.registered → ("Registered", ok)`, `.waitlisted → ("Full", warn)`, `.closed → ("Closed", neutral)`, `.open → (spotsLabel(activity: activity), activ)` — the count/pluralization logic is deleted (shared).
- `waitlistText` becomes `waitlistLabel(activity: activity)` (copy change: drops " in line" — adjudicated #7).
- `plainText(activity.description_)` becomes `plainTextBlurb(html: activity.description_)` and the private `plainText` func is deleted. **SKIE note: the DTO property is `description_` in Swift** (NSObject.description collision) — keep that spelling at call sites.
- Keep the existing `extension ActivityDto: @retroactive Identifiable {}` — both `ForEach(…, id: \.id)` and `.sheet(item:)` rely on it.

- [ ] **Step 3: Rewrite `RegisterActivityView`** to be store-driven (template: M2's `SubmitProofView`). Change import to `@preconcurrency import Shared`. Replace `session`/`onRegistered`/`isSubmitting`/`error` and `submit()`:

```swift
struct RegisterActivityView: View {
    @Environment(\.dismiss) private var dismiss
    let store: ActivitiesStore
    let activity: ActivityDto

    @State private var answers: [String: String] = [:]
    @State private var regState: RegState = RegStateIdle.shared

    private var isSubmitting: Bool { regState is RegStateSubmitting }
    private var errorMessage: String? { (regState as? RegStateError)?.message }
    private var canRegister: Bool {
        requiredAnswered(questions: activity.questions, answers: answers) && !isSubmitting
    }

    var body: some View {
        NavigationStack {
            // ScrollView form body UNCHANGED except:
            //   - the error banner reads `errorMessage` instead of the old `error`
            //   - checkbox toggling uses the shared wire format:
            //       answers[question.key] = checkboxValue(checked: answers[question.key] != "true")
            //     and the checked test everywhere becomes `answers[question.key] == "true"`
            //     (adjudicated #1 — iOS previously used "1"/"0")
        }
        .safeAreaInset(edge: .bottom) {
            Button(action: submit) {
                if isSubmitting { ProgressView().tint(Pent.onBrand) } else { Text("Register") }
            }
            .buttonStyle(PentProminentButtonStyle(enabled: canRegister))
            .disabled(!canRegister)
            .padding(.horizontal, 18).padding(.top, 8).padding(.bottom, 12)
            .background(.bar)
        }
        .task {
            for await value in store.reg {
                await MainActor.run { regState = value }
                if value is RegStateSuccess {
                    await MainActor.run {
                        store.resetReg()
                        dismiss()
                    }
                }
            }
        }
    }

    private func submit() {
        store.register(activityId: activity.id, answers: answers)
    }
}
```

Delete the local `requiredAnswered` computed property (the shared function replaces it — note the semantics: required checkbox must be `"true"`), the `answers.filter { !$0.value.isEmpty }` payload filtering (the store's `registrationPayload` does it), and the old async `submit()`. The banner/questionField/selectField layouts stay unchanged apart from the checkbox value strings.

- [ ] **Step 4: Build:**

```
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build
```
Expected: BUILD SUCCEEDED. (SKIE surfaces: `activityCardState(activity:)`, `spotsLabel(activity:)`, `waitlistLabel(activity:)`, `requiredAnswered(questions:answers:)`, `checkboxValue(checked:)`, `plainTextBlurb(html:)` as global Swift functions; `RegState` sealed → `RegStateIdle.shared` etc.; `ActivityCardState` enum cases lower-camelCased. If a signature differs, adapt minimally and note it.)

- [ ] **Step 5: Commit**

```bash
git add iosApp/iosApp/SessionStore.swift iosApp/iosApp/ActivitiesView.swift iosApp/iosApp/RegisterActivityView.swift
git commit -m "refactor(ios): Activities + registration consume shared ActivitiesStore via SKIE"
```

---

## Task 7: Full milestone verification

- [ ] **Step 1: Everything green**

```bash
./gradlew :shared:allTests :androidApp:assembleDebug :androidApp:testDebugUnitTest --rerun-tasks
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build
```
Expected: all suites green (Activities adds ~21 tests across the two new suites); both apps build; androidApp unit tests compile and pass.

- [ ] **Step 2: No duplicated logic remains**

```bash
grep -rn 'myStatus ==\|spots left\|Waitlisted —\|"1"\|requiredAnswered\|checkboxValue' androidApp/src/main/kotlin/my/silentmode/pentana/feature/activities iosApp/iosApp/ActivitiesView.swift iosApp/iosApp/RegisterActivityView.swift | grep -v 'shared.presentation\|import\|activityCardState\|spotsLabel\|waitlistLabel' || echo CLEAN
```
Expected: `CLEAN` (or only shared-function call sites, to be judged).

- [ ] **Step 3: AppConfig never staged**

Run: `git log main..HEAD --name-only --pretty= | sort -u | grep -c AppConfig || echo NEVER-STAGED`
Expected: `NEVER-STAGED`.

- [ ] **Step 4: STOP — hand to the maintainer** (no push/merge; report test totals, build results, and the seven adjudicated unifications for sign-off).

---

## Self-review notes

- **Spec/M3 coverage:** ActivitiesStore state/refreshing/reg/register/cancel/resetReg (T4) · register-adds-inFlight replacing iOS busyId (T4 + T6) · guarded cancel with actionError copy (T4) · activityCardState/spotsLabel/waitlistLabel/plainTextBlurb (T3) · `description_` rename honored at Swift call sites (T6) · Android + iOS rewires (T5, T6). Brainstorm addenda: GuardedActionRunner + Lunch retrofit (T1), generation counter Bills backport (T2) + Activities native (T4). M2-carryover: RegFormTest migration (T5, deleted in favor of stricter shared tests), Success-dwell guard on the Android sheet (T5 Step 4).
- **Types verified:** `ActivitiesRepository.activities()/register(activityId, answers)/cancel(activityId)`, `ActivityDto`/`QuestionDto` fields (incl. `type` values text/textarea/select/checkbox), Android `excerpt` internals, iOS checkbox "1"/"0" usage (being unified). No invented members.
- **The seven behavior unifications are enumerated at the top** and each is exercised by a test (checkbox format T3, required-checked T3, payload filter T3+T4 via registrationPayload, error copy T4, cancel feedback T4, spots labels T3, waitlist copy T3).
- **Naming:** descriptive names throughout, including test locals.
