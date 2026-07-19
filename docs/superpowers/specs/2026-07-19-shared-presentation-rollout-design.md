# Shared Presentation Rollout — Home, Notifications, Bills, Activities, Session

**Date:** 2026-07-19
**Status:** Approved (design) → ready for implementation plans (one per milestone)
**Repo:** `pentana-mobile`
**Predecessor:** `2026-07-08-shared-lunch-store-pilot-design.md` (pilot judged successful; this extends it)

## Goal

Extend the pilot's shared-store pattern to every remaining screen — **Home (Dashboard)**,
**Notifications**, **Bills**, **Activities** — and finally unify the duplicated session layer
(iOS `SessionStore` + Android `SessionViewModel`) into a shared **`SessionManager`**. After this,
every load/state/action/error/derived-display rule lives — and is tested — once in `commonMain`.

## Decisions taken (brainstorm, 2026-07-19)

1. **Error surfacing** — add `actionError: StateFlow<String?>` + `dismissActionError()` to stores
   with fire-and-forget actions (Lunch choose/notAttending, Activities cancel). Nullable StateFlow,
   not a one-shot SharedFlow: SKIE-friendly, survives view recollection, trivially testable.
   Form-driven actions (Bills submit, Activities register) keep explicit sealed state machines
   (Android's existing `SubmitState`/`RegState` move into the shared stores) — iOS gains the error
   feedback it currently swallows.
2. **Rollout order** — risk-ascending: Home → Notifications → Bills → Activities → Session.
3. **Branching** — per-milestone branches, each reviewed then merged to main **locally by Fahmi**:
   M1 `feat/shared-stores-m1` (error pattern + Lunch retrofit + Home + Notifications),
   M2 `feat/shared-stores-m2` (Bills), M3 `feat/shared-stores-m3` (Activities),
   M4 `feat/shared-session-manager` (Session).
4. **SessionManager scope** — shared state machine **including `login()`**; passkey ceremonies,
   APNs push registration/unregistration, and DI stay native and feed results in via hooks.

## The canonical store pattern (from the pilot, unchanged)

- Plain Kotlin class (NOT an AndroidX ViewModel) owning
  `CoroutineScope(SupervisorJob() + Dispatchers.Main)`; exposes `StateFlow`s; `init { load() }`;
  `clear()` cancels the scope.
- Android wraps it in a thin `ViewModel` and calls `clear()` in `onCleared()`.
- iOS holds it in `@State`, **reuses it across tab reappearance**, and does **not** clear on
  disappear (clearing then reusing = dead store → silent no-ops). Flow collection is structured:
  `async let x: Void = { for await v in flow { await MainActor.run { state = v } } }()` under
  `.task`, with `@preconcurrency import Shared` (see `LunchView.swift`).
- Mutating per-item actions: per-id `inFlight: StateFlow<Set<Long>>` with a **synchronous
  check-and-set before `scope.launch`** (`if (id in set) return; set = set + id`), removal in
  `finally`. Drives disabled/dimmed pending rows (Android `alpha` + `enabled = !busy`; iOS
  `.opacity` + `.allowsHitTesting(!busy)`).
- Always rethrow `CancellationException` before broad catches.
- Derived display logic = pure top-level functions in `commonMain` returning shared enums/copy;
  each platform maps the **decision** to its own **styling**. Date/number *formatting* stays
  native (locale-sensitive); decisions and user-facing sentence copy move.

### New in this rollout: action-error surfacing

```kotlin
private val _actionError = MutableStateFlow<String?>(null)
val actionError: StateFlow<String?> = _actionError.asStateFlow()
fun dismissActionError() { _actionError.value = null }
```

Set on a fire-and-forget action failure (replacing today's silent `catch (_: Exception) {}`),
cleared on dismiss **and at the start of the next action attempt**. UI: Android Snackbar,
iOS `.alert` — native look, shared copy. `LunchStore` is retrofitted first (choose/notAttending)
as the template.

## Milestone 1 — `feat/shared-stores-m1`: error pattern + Home + Notifications

### Lunch retrofit
`LunchStore` gains `actionError`/`dismissActionError()`; `choose`/`notAttending` set it on failure
(copy: `"Couldn't save your choice. Please try again."`). `LunchScreen.kt` shows a Snackbar;
`LunchView.swift` shows an `.alert`. Tests: failure sets the message, dismiss clears it, next
action attempt clears it, success never sets it.

### HomeStore (`presentation/HomeStore.kt` + `HomeDisplay.kt`)
- `HomeUiState`: `Loading` / `Error(message)` / `Content(data: DashboardDto)` — moves to shared.
- `load()`, `refresh()` + `refreshing: StateFlow<Boolean>`. No mutations, no `actionError`.
- Android `HomeViewModel` becomes a thin wrapper (its `refreshing` moves from Compose
  `mutableStateOf` into the store). iOS `HomeView` drops `@State dashboard/isLoading` + `load()`
  and consumes the store (gaining the Loading/Error/Content treatment Android already has; iOS
  currently has no Error state — it shows the shared error message in its existing
  `EmptyStateView` treatment).
- Shared derived (in `HomeDisplay.kt`):
  - `duesCleared(d: DashboardDto): Boolean` — `totalOutstanding == "0.00" && pendingProofsCount == 0`
    (identical on both platforms today).
  - `dashboardLunchStatus(lunch: DashboardLunchDto): LunchStatus` — reuses the pilot's
    `LunchStatus` enum. Verified: both platforms agree `responded` wins over closed
    (`isOpen && !responded → VoteNow; responded → Responded; else → Closed`). Rendering of
    `Closed` stays native (Android: "Voting closed" text; iOS: Closed pill).
  - Activity status decision on the card: reuse `myStatus` string → shared
    `DashboardActivityStatus` enum (`Registered` / `Waitlisted` / `None`).
- Stays native: greeting/date formatting, "N open (to join)" line construction, card layout/copy
  differences that are presentation-only.

### NotificationsStore (`presentation/NotificationsStore.kt` + `NotificationsDisplay.kt`)
- `NotifUiState`: `Loading` / `Error(message)` / `Content(items: List<NotificationDto>)` — moves
  to shared. `load()` only; no refresh flow exists today on either platform.
- **Badge + mark-read stay in the platform session layers until M4.** Android's sheet keeps its
  `onMarkAllRead` callback; iOS keeps calling `session.markNotificationsRead()` (including its
  mark-read-on-open-if-unread behaviour inside `load()`'s completion — the *view* triggers it,
  not the store).
- Shared derived: `notificationKind(title: String): NotificationKind` with
  `Lunch / Cancelled / Payment / ActivityJoined / Activity / General`, unifying the two
  platforms' keyword lists to their **union**:
  - `Lunch`: "lunch" · `Cancelled`: "cancel" · `Payment`: "proof" | "payment" | "dues"
  - `ActivityJoined`: "you're in" | "promoted" | "waitlist"
  - `Activity`: "activity" | "spot" | "event" | "hik" | "clean" | "workshop"
  - order of checks preserved (lunch → cancelled → payment → joined → activity → general).
  Each platform maps the kind to its own icon + colours; the keyword heuristics are deleted from
  both UIs.

## Milestone 2 — `feat/shared-stores-m2`: Bills

### BillsStore (`presentation/BillsStore.kt` + `BillsDisplay.kt`)
- `BillsUiState`: `Loading` / `Error(message)` / `Content(summary: BillsSummaryDto, bills: List<BillDto>)`.
- `load()`, `refresh()` + `refreshing`.
- `submit: StateFlow<SubmitState>` (sealed: `Idle` / `Submitting` / `Error(message)` / `Success`),
  `submitProof(bytes: ByteArray, fileName: String, amount: String, note: String?)`,
  `resetSubmit()`. `Submitting` itself is the double-submit guard (single global flow — the
  submit sheet is modal; no per-id set needed). On success: set `Success` then re-fetch.
- Android's platform `PickedPhoto` stays in the app; the ViewModel/View passes `photo.bytes` +
  `photo.name` down. iOS `SubmitProofView` currently calls the repo directly with photo bytes —
  it switches to the store and **gains error feedback** (today: silent failure; new shared copy:
  `"Upload failed. Please try again."`). Swift `Data` → `KotlinByteArray` via SKIE is already
  proven by the existing repo call.
- Shared derived: `canSubmitProof(amount, hasPhoto)` (moves from Android as-is);
  `billStatus(bill: BillDto): BillStatus` enum (`Paid / Partial / Overdue / Unpaid`) replacing
  both platforms' string-switch on `bill.status`. Month-label formatting stays native.

## Milestone 3 — `feat/shared-stores-m3`: Activities

### ActivitiesStore (`presentation/ActivitiesStore.kt` + `ActivitiesDisplay.kt`)
- `ActivitiesUiState`: `Loading` / `Error(message)` / `Content(activities: List<ActivityDto>)`.
- `load()`, `refresh()` + `refreshing`.
- `reg: StateFlow<RegState>` (sealed: `Idle` / `Submitting` / `Error(message)` / `Success`),
  `register(activityId: Long, answers: Map<String, String>)`, `resetReg()` — the registration
  sheet flow (dynamic questions UI stays native on both platforms).
- `cancel(activityId: Long)` = fire-and-forget: per-id `inFlight` set (synchronous check-and-set)
  + `actionError` on failure (`"Couldn't cancel. Please try again."`). `register` **also adds the
  id to `inFlight`** so the card dims while a no-questions registration runs — this replaces iOS's
  `busyId`. Optimistic `replace(updated)` on success (same as Lunch/pilot).
- Shared derived: `activityCardState(activity: ActivityDto): ActivityCardState` enum
  (`Registered / Waitlisted / Open / Closed` from `myStatus` + `isOpen` — logic identical on both
  platforms today); `spotsLabel(activity)` copy ("N spot(s) left" / "Open" / "Full" / …);
  `waitlistLabel(activity)` ("Waitlisted — #N in line" / "Waitlisted"); `plainTextBlurb(html?)`
  (iOS's regex HTML-strip, portable Kotlin implementation).
- **SKIE gotcha:** `ActivityDto.description` surfaces in Swift as `description_` — the updated
  Swift call sites must use `description_` (already true today in `ActivitiesView.swift`; keep it).
- Boxed optionals via SKIE: `spotsLeft`/`waitlistPosition` are `KotlinInt?`-style boxes on Swift —
  moving their interpretation into shared copy functions removes most of that handling.

## Milestone 4 — `feat/shared-session-manager`: SessionManager

### Shared `SessionManager(auth: AuthRepository, notifications: NotificationsRepository)`
(`presentation/SessionManager.kt`)

```kotlin
data class SessionState(
    val user: UserDto? = null,
    val unread: Int = 0,
    val bootstrapping: Boolean = true,
)
val state: StateFlow<SessionState>
val loginError: StateFlow<String?>          // + dismissLoginError()

suspend fun bootstrap()                      // token? → me() → refreshBadge(); stale → logout+null
suspend fun login(email: String, password: String, deviceName: String): UserDto?
                                             // null on failure + sets loginError
fun onLoggedIn(user: UserDto)                // for natively-completed ceremonies (passkey)
suspend fun logout()                         // best-effort auth.logout(); resets state
suspend fun refreshBadge()                   // best-effort unread count
suspend fun markAllRead()                    // best-effort; unread → 0
fun clear()
```

- Methods that platforms must sequence around (`bootstrap`, `login`, `logout`) are **`suspend`** —
  SKIE surfaces them as Swift `async`, so iOS can `await login(...)` then run its native
  push-enable step; Android wraps calls in `viewModelScope.launch`. Fire-and-forget helpers
  (`onLoggedIn`) stay plain. `refreshBadge`/`markAllRead` are suspend so callers can chain them.
- `login` returns the user (or null) so iOS/Android can trigger follow-ups without re-reading
  state; on success it also sets `state.user` and refreshes the badge (subsuming both platforms'
  post-login sequences). `loginError` carries the message for the login screens. Copy unifies to
  Android's `"The provided credentials are incorrect."` (iOS's dynamic
  `"Sign in failed: <reason>"` was a debugging aid; if Fahmi wants the reason preserved, the
  manager can append it — flag at plan review).
- **Android `LoginViewModel`** keeps its form-field state (email/password/loading/canSubmit —
  screen-local, stays native) but its `submit()` delegates to `manager.login(...)` and its local
  `error` copy is replaced by observing `loginError`. **iOS `LoginView`** likewise reads
  `loginError` instead of `SessionStore.errorMessage`.
- Guard: badge/mark-read no-op unless logged in (matches iOS today).
- **Single app-wide instance**: exactly one `SessionManager` per app process — Android vends it
  from `AppContainer` (so `SessionViewModel` and `LoginViewModel` share it); iOS constructs it
  once inside `SessionStore`.

### Stays native
- **iOS `SessionStore`**: repo/DI wiring, APNs (`enablePushNotifications`, device-token
  register/unregister — unregister runs *before* the shared `logout()`), passkey ceremonies
  (`passkeySignIn`/`registerPasskey` → `manager.onLoggedIn(user)` on success), `PushRegistrar`
  callbacks. It holds the `SessionManager`, bridges `state`/`loginError` into its `@Published`
  properties (or views observe the flows directly — implementer's choice, whichever keeps the
  view diff smallest), and keeps vending `make*Store()` factories.
- **Android `SessionViewModel`**: thin wrapper (constructs the manager, forwards calls, `clear()`
  in `onCleared()`).
- **M4 also moves Notifications' badge coupling**: `markNotificationsRead` /`refreshBadge` logic
  deleted from both platform session layers in favour of the manager.

## Testing (every milestone)

- commonTest per store with Ktor `MockEngine` (pattern: `LunchStoreTest`): happy path → `Content`,
  failure → `Error`, action-error set/dismiss/clear-on-retry, state-machine transitions
  (`SubmitState`/`RegState`), in-flight guard (add-before-launch, removal on completion & failure),
  derived functions per case. `Dispatchers.setMain(UnconfinedTestDispatcher())` +
  `StandardTestDispatcher` where the guard's mid-flight state is asserted (see pilot tests).
- `SessionManagerTest`: bootstrap with valid/stale/absent token, login success/failure +
  `loginError`, logout resets, badge refresh, markAllRead zeroes unread.
- Every milestone gates on: `./gradlew :shared:allTests` + `./gradlew :androidApp:assembleDebug` +
  `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination
  'platform=iOS Simulator,name=iPhone 17 Pro' build` (arm64 sim required — no iosX64 target;
  Gradle/xcodebuild run with sandbox disabled: no network in sandbox).

## Risks & guardrails

- **SKIE surface growth**: each store adds generated Swift; the pilot proved the toolchain
  (Kotlin 2.1.21 + SKIE 0.10.13 + static framework), so risk is per-screen interop details
  (`description_`, boxed numerics) — all catalogued above.
- **Session is highest-risk** (auth/bootstrap/push/passkey interleaving) → isolated in M4, done
  last, on its own branch.
- **Behaviour parity**: no visual redesign; where platforms diverge today the spec names the
  unification (Notifications keyword union, iOS gaining Error states/feedback) — anything else
  unexpected gets surfaced, not silently changed.
- Uncommitted `androidApp/.../core/AppConfig.kt` LAN-IP edit is never staged.
- Nothing is pushed or merged without Fahmi's say-so; local main is ahead of origin/main.

## Success criteria

Per milestone: both apps build; the screen behaves identically (modulo the named improvements:
error feedback on iOS, unified keyword mapping); duplicated per-platform logic is **deleted**;
shared tests cover the store. After M4: a presentation-logic change touches exactly one Kotlin
file, and `SessionStore.swift`/`SessionViewModel.kt` contain no duplicated state-machine logic.
