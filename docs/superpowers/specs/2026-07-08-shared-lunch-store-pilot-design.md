# Shared Presentation Logic — LunchStore Pilot

**Date:** 2026-07-08
**Status:** Approved (design) → ready for implementation plan
**Repo:** `pentana-mobile`

## Goal
Pilot sharing the **presentation/orchestration logic** (not just repos/DTOs) across iOS + Android
by extracting one screen — **Lunch** — into a shared Kotlin `LunchStore` in `commonMain`, consumed
natively by Compose and by SwiftUI via **SKIE**. Prove the pattern + measure the iOS interop cost
before deciding whether to roll it out to the other screens and the session layer.

## Motivation
Today only the model + repository layer is shared. Each screen's *load → state → actions → error →
derived-display* logic is re-implemented twice — an Android `ViewModel` and a SwiftUI `View` (+ the
`SessionStore`). Lunch alone duplicates the load/optimistic-update flow **and** derived rules (the
status chip + "ordering closed" summary live in both `LunchScreen.kt` and `LunchView.swift`). A
presentation-logic change means editing both sides and risks drift.

## Scope
**In (pilot):** the Lunch screen only — a shared `LunchStore` + shared derived logic, Android + iOS
rewired to consume it, SKIE added to `:shared`, shared tests.
**Out:** all other screens (Home, Bills, Activities, Notifications) and the session layer stay exactly
as they are until the pilot is judged. No UI/visual redesign — the native look is unchanged.

## Architecture

### Shared `LunchStore` (`commonMain`)
A plain Kotlin state-holder — deliberately **not** an AndroidX `ViewModel`, so it stays
platform-neutral. It owns its coroutine scope so each platform controls only teardown.

```
class LunchStore(private val repo: LunchRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val state: StateFlow<LunchUiState>     // MutableStateFlow internally, starts Loading
    val refreshing: StateFlow<Boolean>

    init { load() }
    fun load()                              // -> Loading -> Content|Error
    fun refresh()                           // sets refreshing, re-fetches
    fun choose(lunchId: Long, mealOptionId: Long)   // optimistic replace on success
    fun notAttending(lunchId: Long)
    fun clear()                             // scope.cancel() — called on teardown
}
```

`LunchUiState` (sealed interface: `Loading` / `Error(message)` / `Content(lunches)`) moves to
`commonMain`. Repos already hop to `Dispatchers.Main`, so a Main-dispatched store scope is consistent
and keeps SwiftUI updates on the main thread.

### The seam — share decisions, keep visuals
The duplicated derived logic moves to `commonMain` as pure, testable functions; each platform maps
the **decision** to its own **styling**:

```
enum class LunchStatus { VoteNow, Responded, Closed }
fun lunchStatus(lunch: LunchDto): LunchStatus
fun lunchClosedSummary(lunch: LunchDto): String   // the user-facing "Ordering closed — …" copy
```

Android maps `LunchStatus` → its `ChipKind` (Compose colours); iOS maps it → its chip. The
*logic/copy* is shared; the *look* stays native.

### Android
- `LunchViewModel` shrinks to a thin `ViewModel` that constructs `LunchStore(container.lunch)` and
  calls `store.clear()` in `onCleared()`.
- `LunchScreen.kt` collects `store.state` + `store.refreshing` (`collectAsStateWithLifecycle`) and
  maps `LunchStatus` → `ChipKind`. Its local `lunchChip()` / `closedSummary()` are **deleted**
  (now shared). `LunchUiState` import moves to the shared package.

### iOS (via SKIE)
- `LunchView` obtains a `LunchStore` (vended from `SessionStore`, e.g. `session.makeLunchStore()`),
  held for the view's lifetime, and calls `store.clear()` in `.onDisappear`.
- SKIE exposes `state` as a Swift `AsyncSequence`; the view consumes it in a `.task`
  (`for await s in store.state { self.state = s }`) and reads `store.state.value` for the current
  value. Intents (`store.load()`, `store.choose(...)`) are plain calls — they launch inside the
  store's scope, so they're fire-and-forget from Swift.
- `LunchView`'s `load()` / `update()` helpers and its status/summary code are **deleted**; the
  sealed `LunchUiState` and `LunchStatus` arrive as Swift enums (via SKIE) for exhaustive `switch`.

### SKIE setup
- Add the SKIE Gradle plugin to `:shared`: `id("co.touchlab.skie") version "0.10.13"` (0.10.x tracks
  Kotlin 2.1.x; pin the release whose matrix lists **Kotlin 2.1.21** — SKIE is a compiler plugin, so
  a mismatch fails the build immediately, i.e. self-verifying).
- Confirm the **static** `Shared.framework` (`isStatic = true`) still links and the SwiftUI app builds.
  De-risking this is the pilot's primary purpose.

## Data flow (choose a meal)
tap in SwiftUI/Compose → `store.choose(lunchId, optionId)` → launches in store scope →
`repo.chooseOption(...)` → on success replaces that lunch in `Content` → `state` emits →
both UIs re-render from the shared flow.

## File structure
```
shared/src/commonMain/.../shared/
  presentation/LunchStore.kt        # NEW — store + LunchUiState
  presentation/LunchDisplay.kt      # NEW — LunchStatus + lunchStatus() + lunchClosedSummary()
shared/src/commonTest/.../shared/
  LunchStoreTest.kt                 # NEW — MockEngine: load/choose/notAttending/error + derived
shared/build.gradle.kts             # + SKIE plugin
androidApp/.../feature/lunch/
  LunchViewModel.kt                 # slimmed to wrap LunchStore
  LunchScreen.kt                    # consumes store; maps LunchStatus->ChipKind; dup logic removed
iosApp/iosApp/
  LunchView.swift                   # consumes LunchStore via SKIE; dup logic removed
  SessionStore.swift                # + makeLunchStore()
```

## Testing
- **Shared `commonTest` (the payoff):** `LunchStore` against Ktor `MockEngine` — `load()`→`Content`,
  `choose()` updates the right lunch, `notAttending()`, error→`Error`; plus `lunchStatus()` and
  `lunchClosedSummary()` for open/responded/closed. Logic verified **once**.
- **Android:** `./gradlew :androidApp:assembleDebug` + `:shared:allTests` green.
- **iOS:** `xcodebuild` succeeds with SKIE enabled; `LunchView` compiles against the generated Swift
  enums and renders/behaves as before (on-device eyeball).

## Risks & guardrails
- **SKIE ↔ toolchain:** adding the plugin could perturb the framework build/link (static framework,
  Kotlin 2.1.21). Contained to one screen → cheap to revert (drop the plugin + restore `LunchView`).
- **Scope leaks:** the store owns its scope; both platforms must call `clear()` on teardown
  (Android `onCleared`, iOS `.onDisappear`).
- Everything outside Lunch is untouched, so a failed pilot has no blast radius.

## Success criteria (decide rollout)
Pilot is a "yes, extend it" if: both apps build with SKIE; Lunch behaves identically; the shared
`LunchStore` + derived tests replace the two duplicated implementations; and the SwiftUI interop
(SKIE) is comfortable. Then extend to the remaining screens and a shared `SessionManager`.
