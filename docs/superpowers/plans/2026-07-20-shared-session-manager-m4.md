# Shared Stores Milestone 4 — SessionManager + Bundled UI Follow-ups Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Unify the duplicated session layer (iOS `SessionStore` + Android `SessionViewModel`) into a shared `SessionManager` owning the auth/bootstrap/badge state machine including `login()`, with passkey/push ceremonies staying native — plus three small adjudicated UI follow-ups from M3 riding along as an early task.

**Architecture:** `SessionManager(auth, notifications)` is a single app-wide instance (Android: vended by `AppContainer`; iOS: constructed once inside `SessionStore`) exposing `state: StateFlow<SessionState>` (user/unread/bootstrapping) and `loginError: StateFlow<String?>`. Methods platforms must sequence around (`bootstrap`/`login`/`logout`/`refreshBadge`/`markAllRead`) are `suspend` (SKIE → Swift `async`); natively-completed ceremonies feed in via `onLoggedIn(user)`. Platform wrappers shrink to bridges: Android `SessionViewModel`/`LoginViewModel` keep only Compose form state; iOS `SessionStore` keeps DI, APNs, and passkeys, bridging manager flows into its `@Published` properties so views are untouched.

**Tech Stack:** Kotlin 2.1.21, kotlinx-coroutines StateFlow, Ktor MockEngine (tests), SKIE 0.10.13, Compose, SwiftUI.

**Spec:** `docs/superpowers/specs/2026-07-19-shared-presentation-rollout-design.md` (Milestone 4) + brainstorm addenda 2026-07-20
**Branch:** `feat/shared-session-manager` (created off merged main)

**Standing rules for every task:**
- Gradle/xcodebuild have no network inside the sandbox — run them with the sandbox disabled.
- iOS builds MUST use `-destination 'platform=iOS Simulator,name=iPhone 17 Pro'` (arm64; generic fails).
- NEVER stage `androidApp/src/main/kotlin/my/silentmode/pentana/core/AppConfig.kt`. `git add` explicit paths only.
- Do not push or merge — the maintainer reviews first.
- **Descriptive variable names only — no single-letter locals/params anywhere, including tests.**
- Android gate: `./gradlew :androidApp:assembleDebug :androidApp:testDebugUnitTest`.

**Adjudicated decisions (2026-07-20, do not "fix" back):**
1. **Login error copy** unified to Android's `"The provided credentials are incorrect."` — iOS drops its diagnostic `"Sign in failed: <reason>"`.
2. **Notifications sheet refetches on every open** (Android gains `LaunchedEffect(Unit) { vm.store.load() }`; iOS already refetches per presentation). Accepted cost: on the very first Android open the store's `init { load() }` plus the effect issue two GETs — cheap, self-correcting, not worth first-open tracking.
3. **Closed-card pill**: iOS wins — `ActivityCardState.Closed` renders a Closed chip on Android too (never a spots count beside "Registration closed").
4. **Questionless-register failures surface via `actionError`**: new shared `quickRegister(activityId)` on `ActivitiesStore` (per-id guard + actionError, reg machine untouched); copy `"Registration failed. Please try again."` (no answers to check). Both platforms' no-questions paths switch to it.
5. **iOS error-state retry**: Bills + Activities error `EmptyStateView`s gain `actionTitle: "Try again", action: { store?.load() }` (LunchView precedent).

---

## Task 1: Bundled M3 UI follow-ups (shared quickRegister + platform touches)

**Files:**
- Modify: `shared/src/commonMain/kotlin/my/silentmode/pentana/shared/presentation/ActivitiesStore.kt`
- Test: `shared/src/commonTest/kotlin/my/silentmode/pentana/shared/ActivitiesStoreTest.kt`
- Modify: `androidApp/src/main/kotlin/my/silentmode/pentana/feature/activities/ActivitiesScreen.kt`
- Modify: `iosApp/iosApp/ActivitiesView.swift`
- Modify: `iosApp/iosApp/BillsView.swift`

- [ ] **Step 1: Write the failing tests** — add to `ActivitiesStoreTest` (fixtures `listJson`/`registeredJson` and `makeStore`/`routing` already exist):

```kotlin
@Test fun quick_register_success_replaces_activity() = runTest {
    val store = makeStore(routing())
    store.state.first { it is ActivitiesUiState.Content }
    store.quickRegister(activityId = 1)
    store.inFlight.first { it.isEmpty() }
    assertEquals("registered", (store.state.value as ActivitiesUiState.Content).activities.first { it.id == 1L }.myStatus)
    assertNull(store.actionError.value)
}

@Test fun quick_register_failure_surfaces_action_error_not_reg() = runTest {
    val store = makeStore(routing(registerResult = HttpStatusCode.InternalServerError to "{}"))
    store.state.first { it is ActivitiesUiState.Content }
    store.quickRegister(activityId = 1)
    store.inFlight.first { it.isEmpty() }
    assertEquals("Registration failed. Please try again.", store.actionError.value)
    assertIs<RegState.Idle>(store.reg.value) // the sheet machine is untouched
}
```

- [ ] **Step 2: Run — expect FAIL** (unresolved `quickRegister`): `./gradlew :shared:allTests`

- [ ] **Step 3: Implement `quickRegister`** in `ActivitiesStore.kt`, next to `cancel`:

```kotlin
    /**
     * Registration for activities WITHOUT questions — no sheet is open, so failures surface via
     * [actionError] (Snackbar/alert) instead of the sheet-bound [reg] machine.
     */
    fun quickRegister(activityId: Long) =
        actionRunner.run(activityId, "Registration failed. Please try again.") {
            replace(repo.register(activityId, emptyMap()))
        }
```

- [ ] **Step 4: Run — expect PASS**: `./gradlew :shared:allTests` (ActivitiesStoreTest 12/12).

- [ ] **Step 5: Android touches.** In `ActivitiesScreen.kt`:
  - The no-questions branch of `onRegister` becomes `vm.store.quickRegister(activity.id)` (was `vm.store.register(activity.id, emptyMap())`).
  - In `SpotsChip`, the `Closed` case splits out of the Open branch: `ActivityCardState.Closed -> StatusChip(ChipKind.Closed)` (a closed activity never shows a spots count — adjudication #3). `Open` keeps `StatusChip(ChipKind.Open, label = spotsLabel(activity))`.

- [ ] **Step 6: iOS touches.**
  - `ActivitiesView.swift`: no-questions branch → `store?.quickRegister(activityId: activity.id)`; the error case's `EmptyStateView` gains `actionTitle: "Try again", action: { store?.load() }`.
  - `BillsView.swift`: the error case's `EmptyStateView` gains the same `actionTitle`/`action` pair.

- [ ] **Step 7: Gates**

```bash
./gradlew :shared:allTests :androidApp:assembleDebug :androidApp:testDebugUnitTest
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build
```

- [ ] **Step 8: Commit**

```bash
git add shared/src/commonMain/kotlin/my/silentmode/pentana/shared/presentation/ActivitiesStore.kt shared/src/commonTest/kotlin/my/silentmode/pentana/shared/ActivitiesStoreTest.kt androidApp/src/main/kotlin/my/silentmode/pentana/feature/activities/ActivitiesScreen.kt iosApp/iosApp/ActivitiesView.swift iosApp/iosApp/BillsView.swift
git commit -m "feat: quickRegister via actionError, Closed pill unification, iOS Try-again retry"
```

---

## Task 2: Shared SessionManager (TDD)

**Files:**
- Create: `shared/src/commonMain/kotlin/my/silentmode/pentana/shared/presentation/SessionManager.kt`
- Test: `shared/src/commonTest/kotlin/my/silentmode/pentana/shared/SessionManagerTest.kt`

- [ ] **Step 1: Write the failing tests** — create `SessionManagerTest.kt`:

```kotlin
package my.silentmode.pentana.shared

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.fullPath
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import my.silentmode.pentana.shared.presentation.SessionManager
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SessionManagerTest {
    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private val userJson = """{"data":{"id":7,"name":"Aisyah Rahman","email":"aisyah@org.my","credit":12.5}}"""
    private val loginJson = """{"token":"fresh-token","user":{"id":7,"name":"Aisyah Rahman","email":"aisyah@org.my","credit":12.5}}"""
    private val notificationsJson = """{"data":[],"unread_count":3}"""
    private val markReadJson = """{"unread_count":0}"""

    private class Managed(val manager: SessionManager, val tokenStore: InMemoryTokenStore)

    private fun makeManager(
        startToken: String? = null,
        handler: suspend (String) -> Pair<HttpStatusCode, String>,
    ): Managed {
        val tokenStore = InMemoryTokenStore(startToken)
        val engine = MockEngine { request ->
            val (status, body) = handler(request.url.fullPath)
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = ApiClient("https://x/api/v1", tokenStore, engine)
        return Managed(SessionManager(AuthRepository(client), NotificationsRepository(client)), tokenStore)
    }

    private fun routing(
        meResult: Pair<HttpStatusCode, String>? = null,
        loginResult: Pair<HttpStatusCode, String>? = null,
    ): suspend (String) -> Pair<HttpStatusCode, String> = { path ->
        when {
            path.endsWith("/me") -> meResult ?: (HttpStatusCode.OK to userJson)
            path.endsWith("/login") -> loginResult ?: (HttpStatusCode.OK to loginJson)
            path.endsWith("/logout") -> HttpStatusCode.OK to "{}"
            path.endsWith("/notifications/read") -> HttpStatusCode.OK to markReadJson
            path.endsWith("/notifications") -> HttpStatusCode.OK to notificationsJson
            else -> HttpStatusCode.NotFound to "{}"
        }
    }

    @Test fun bootstrap_with_valid_token_loads_user_and_badge() = runTest {
        val managed = makeManager(startToken = "stored-token", handler = routing())
        managed.manager.bootstrap()
        val sessionState = managed.manager.state.value
        assertEquals("Aisyah Rahman", sessionState.user?.name)
        assertEquals(3, sessionState.unread)
        assertFalse(sessionState.bootstrapping)
    }

    @Test fun bootstrap_with_stale_token_logs_out() = runTest {
        val managed = makeManager(startToken = "stale-token", handler = routing(meResult = HttpStatusCode.Unauthorized to "{}"))
        managed.manager.bootstrap()
        val sessionState = managed.manager.state.value
        assertNull(sessionState.user)
        assertFalse(sessionState.bootstrapping)
        assertNull(managed.tokenStore.get()) // logout cleared the stale token
    }

    @Test fun bootstrap_without_token_just_finishes() = runTest {
        val managed = makeManager(startToken = null, handler = routing())
        managed.manager.bootstrap()
        assertNull(managed.manager.state.value.user)
        assertFalse(managed.manager.state.value.bootstrapping)
    }

    @Test fun login_success_sets_user_refreshes_badge_and_returns_user() = runTest {
        val managed = makeManager(startToken = null, handler = routing())
        val loggedIn = managed.manager.login(email = " aisyah@org.my ", password = "secret", deviceName = "Test")
        assertNotNull(loggedIn)
        assertEquals("Aisyah Rahman", managed.manager.state.value.user?.name)
        assertEquals(3, managed.manager.state.value.unread)
        assertNull(managed.manager.loginError.value)
        assertEquals("fresh-token", managed.tokenStore.get())
    }

    @Test fun login_failure_sets_error_and_returns_null() = runTest {
        val managed = makeManager(startToken = null, handler = routing(loginResult = HttpStatusCode.UnprocessableEntity to "{}"))
        val loggedIn = managed.manager.login(email = "aisyah@org.my", password = "wrong", deviceName = "Test")
        assertNull(loggedIn)
        assertNull(managed.manager.state.value.user)
        assertEquals("The provided credentials are incorrect.", managed.manager.loginError.value)
        managed.manager.dismissLoginError()
        assertNull(managed.manager.loginError.value)
    }

    @Test fun next_login_attempt_clears_previous_error() = runTest {
        var failNext = true
        val managed = makeManager(startToken = null) { path ->
            when {
                path.endsWith("/login") -> {
                    if (failNext) { failNext = false; HttpStatusCode.UnprocessableEntity to "{}" } else HttpStatusCode.OK to loginJson
                }
                else -> routing()(path)
            }
        }
        managed.manager.login(email = "aisyah@org.my", password = "wrong", deviceName = "Test")
        assertNotNull(managed.manager.loginError.value)
        managed.manager.login(email = "aisyah@org.my", password = "right", deviceName = "Test")
        assertNull(managed.manager.loginError.value)
    }

    @Test fun logout_resets_state_even_if_server_call_fails() = runTest {
        val managed = makeManager(startToken = "stored-token") { path ->
            when {
                path.endsWith("/logout") -> HttpStatusCode.InternalServerError to "{}"
                else -> routing()(path)
            }
        }
        managed.manager.bootstrap()
        assertNotNull(managed.manager.state.value.user)
        managed.manager.logout()
        assertNull(managed.manager.state.value.user)
        assertEquals(0, managed.manager.state.value.unread)
        assertFalse(managed.manager.state.value.bootstrapping)
    }

    @Test fun badge_and_mark_read_are_noops_when_logged_out() = runTest {
        var notificationRequests = 0
        val managed = makeManager(startToken = null) { path ->
            if (path.contains("/notifications")) notificationRequests += 1
            routing()(path)
        }
        managed.manager.refreshBadge()
        managed.manager.markAllRead()
        assertEquals(0, notificationRequests)
    }

    @Test fun mark_all_read_zeroes_unread() = runTest {
        val managed = makeManager(startToken = "stored-token", handler = routing())
        managed.manager.bootstrap()
        assertEquals(3, managed.manager.state.value.unread)
        managed.manager.markAllRead()
        assertEquals(0, managed.manager.state.value.unread)
    }

    @Test fun on_logged_in_sets_user_and_refreshes_badge() = runTest {
        val managed = makeManager(startToken = "ceremony-token", handler = routing())
        val ceremonyUser = my.silentmode.pentana.shared.model.UserDto(id = 7, name = "Aisyah Rahman", email = "aisyah@org.my")
        managed.manager.onLoggedIn(ceremonyUser)
        managed.manager.state.first { it.unread == 3 }
        assertEquals("Aisyah Rahman", managed.manager.state.value.user?.name)
    }
}
```

- [ ] **Step 2: Run — expect FAIL** (unresolved `SessionManager`): `./gradlew :shared:allTests`

- [ ] **Step 3: Implement `SessionManager.kt`:**

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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import my.silentmode.pentana.shared.AuthRepository
import my.silentmode.pentana.shared.NotificationsRepository
import my.silentmode.pentana.shared.model.UserDto

data class SessionState(
    val user: UserDto? = null,
    val unread: Int = 0,
    val bootstrapping: Boolean = true,
)

/**
 * Shared session state machine: auth bootstrap, credential login, logout, and the bell badge.
 * ONE instance per app process — Android vends it from AppContainer (SessionViewModel and
 * LoginViewModel share it and must NOT clear() it on ViewModel teardown); iOS constructs it once
 * inside SessionStore. Natively-completed ceremonies (passkey) feed in via [onLoggedIn]; APNs
 * registration/unregistration and DI stay native, sequenced around the suspend methods
 * (SKIE surfaces them as Swift async).
 *
 * All methods hop to Dispatchers.Main internally — safe to call from Swift-async threads.
 */
class SessionManager(
    private val auth: AuthRepository,
    private val notifications: NotificationsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    /** On launch: if a token exists, fetch the profile; drop the token if it is stale. */
    suspend fun bootstrap() = withContext(Dispatchers.Main) {
        if (!auth.isLoggedIn()) {
            _state.update { it.copy(bootstrapping = false) }
            return@withContext
        }
        try {
            val user = auth.me()
            _state.update { it.copy(user = user, bootstrapping = false) }
            refreshBadge()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            try {
                auth.logout()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
            }
            _state.update { it.copy(user = null, bootstrapping = false) }
        }
    }

    /**
     * Credential login. On success sets the user, refreshes the badge, and returns the user
     * (so platforms can chain native follow-ups like push registration); on failure returns
     * null with [loginError] set. Starting a new attempt clears the previous error.
     */
    suspend fun login(email: String, password: String, deviceName: String): UserDto? =
        withContext(Dispatchers.Main) {
            _loginError.value = null
            try {
                val user = auth.login(email.trim(), password, deviceName)
                _state.update { it.copy(user = user) }
                refreshBadge()
                user
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _loginError.value = "The provided credentials are incorrect."
                null
            }
        }

    /** For natively-completed ceremonies (passkey): adopt the signed-in user. */
    fun onLoggedIn(user: UserDto) {
        _state.update { it.copy(user = user) }
        scope.launch { refreshBadge() }
    }

    /** Best-effort server logout; local state resets regardless. */
    suspend fun logout() = withContext(Dispatchers.Main) {
        try {
            auth.logout()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
        }
        _state.value = SessionState(bootstrapping = false)
    }

    /** Best-effort bell-badge refresh; no-op when logged out. */
    suspend fun refreshBadge() = withContext(Dispatchers.Main) {
        if (_state.value.user == null) return@withContext
        try {
            val unreadCount = notifications.notifications().unreadCount
            _state.update { it.copy(unread = unreadCount) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
        }
    }

    /** Marks every notification read (best-effort) and clears the badge; no-op when logged out. */
    suspend fun markAllRead() = withContext(Dispatchers.Main) {
        if (_state.value.user == null) return@withContext
        try {
            notifications.markAllRead()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
        }
        _state.update { it.copy(unread = 0) }
    }

    fun dismissLoginError() { _loginError.value = null }

    fun clear() { scope.cancel() }
}
```

Note the stale-token logout inside `bootstrap`: `AuthRepository.logout()` clears the token store even when the server call fails only if the request itself doesn't throw before `tokenStore.clear()` — it does `post` then `clear()`, and `ensureSuccess` is NOT called on logout, so a non-2xx response still clears. A thrown connection error skips the clear; the catch swallows it and the user is logged out in-memory either way (matches both platforms today).

- [ ] **Step 4: Run — expect PASS**: `./gradlew :shared:allTests` (SessionManagerTest 10/10).

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/my/silentmode/pentana/shared/presentation/SessionManager.kt shared/src/commonTest/kotlin/my/silentmode/pentana/shared/SessionManagerTest.kt
git commit -m "feat(shared): SessionManager — bootstrap/login/logout/badge state machine + tests"
```

---

## Task 3: Rewire Android session layer to the shared manager

**Files:**
- Modify: `androidApp/src/main/kotlin/my/silentmode/pentana/core/AppContainer.kt`
- Modify: `androidApp/src/main/kotlin/my/silentmode/pentana/ui/session/SessionViewModel.kt`
- Modify: `androidApp/src/main/kotlin/my/silentmode/pentana/feature/login/LoginViewModel.kt`
- Modify: `androidApp/src/main/kotlin/my/silentmode/pentana/feature/login/LoginScreen.kt`
- Modify: `androidApp/src/main/kotlin/my/silentmode/pentana/PentanaApp.kt`
- Modify: `androidApp/src/main/kotlin/my/silentmode/pentana/feature/notifications/NotificationsSheet.kt`

- [ ] **Step 1: Vend the manager from `AppContainer.kt`** — add after the repository declarations:

```kotlin
import my.silentmode.pentana.shared.presentation.SessionManager
...
    /** App-scoped session state machine — shared by SessionViewModel and LoginViewModel. */
    val sessionManager = SessionManager(auth, notifications)
```

- [ ] **Step 2: Slim `SessionViewModel.kt`.** Replace the whole file with:

```kotlin
package my.silentmode.pentana.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import my.silentmode.pentana.shared.presentation.SessionManager

/**
 * Thin launcher over the app-scoped [SessionManager]. Deliberately does NOT clear() the manager
 * in onCleared() — the manager outlives any ViewModel (it is owned by AppContainer).
 */
class SessionViewModel(private val sessionManager: SessionManager) : ViewModel() {
    val state = sessionManager.state

    fun bootstrap() { viewModelScope.launch { sessionManager.bootstrap() } }
    fun logout() { viewModelScope.launch { sessionManager.logout() } }
    fun markNotificationsRead() { viewModelScope.launch { sessionManager.markAllRead() } }
}
```

(The local `State` data class, `onLoggedIn`, and `refreshBadge` are deleted — state comes from shared `SessionState`; login lands in the manager directly, so no callback is needed.)

- [ ] **Step 3: Rewire `LoginViewModel.kt`.** Replace the whole file with:

```kotlin
package my.silentmode.pentana.feature.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import my.silentmode.pentana.shared.presentation.SessionManager

/** Form state stays here (screen-local); the actual login + error copy live in the shared manager. */
class LoginViewModel(private val sessionManager: SessionManager) : ViewModel() {
    var email by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var loading by mutableStateOf(false)
        private set

    val loginError = sessionManager.loginError

    val canSubmit: Boolean get() = email.isNotBlank() && password.isNotBlank() && !loading

    fun onEmail(value: String) { email = value; sessionManager.dismissLoginError() }
    fun onPassword(value: String) { password = value; sessionManager.dismissLoginError() }

    fun submit() {
        if (!canSubmit) return
        loading = true
        viewModelScope.launch {
            sessionManager.login(email, password, "Android")
            loading = false
        }
    }
}
```

(No `onSuccess` callback: a successful login sets `state.user` in the manager, which flips `PentanaApp`'s `when` automatically. The manager trims the email itself.)

- [ ] **Step 4: Update `LoginScreen.kt`.** Signature loses the callback; the error comes from the flow:

```kotlin
import androidx.lifecycle.compose.collectAsStateWithLifecycle
...
@Composable
fun LoginScreen() {
    val vm = appViewModel { LoginViewModel(it.sessionManager) }
    val loginError by vm.loginError.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(loginError) { loginError?.let { message -> snackbar.showSnackbar(message) } }
```

and in the form: `isError = loginError != null` (both fields), `supportingText = loginError` (password field), button `onClick = vm::submit`. The `UserDto` import is removed.

- [ ] **Step 5: Update `PentanaApp.kt`:**

```kotlin
        val session = appViewModel { SessionViewModel(it.sessionManager) }
...
            s.user == null -> LoginScreen()
```

(Everything else — `MainScaffold`, `ProfileSheet` sign-out via `session.logout()`, bell via `session::markNotificationsRead` — is source-compatible: the shared `SessionState` has the same `user`/`unread`/`bootstrapping` property names as the deleted local `State`.)

- [ ] **Step 6: Notifications fetch-on-open** — in `NotificationsSheet.kt`, next to the existing mark-read effect:

```kotlin
    // Refetch on every open so read-states are current (adjudication #2; the store's init-load
    // makes the very first open double-fetch — accepted, cheap).
    LaunchedEffect(Unit) { vm.store.load() }
```

- [ ] **Step 7: Gates**

Run: `./gradlew :androidApp:assembleDebug :androidApp:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add androidApp/src/main/kotlin/my/silentmode/pentana/core/AppContainer.kt androidApp/src/main/kotlin/my/silentmode/pentana/ui/session/SessionViewModel.kt androidApp/src/main/kotlin/my/silentmode/pentana/feature/login androidApp/src/main/kotlin/my/silentmode/pentana/PentanaApp.kt androidApp/src/main/kotlin/my/silentmode/pentana/feature/notifications/NotificationsSheet.kt
git commit -m "refactor(android): session layer consumes shared SessionManager"
```

---

## Task 4: Rewire iOS SessionStore onto the shared manager

**Files:**
- Modify: `iosApp/iosApp/SessionStore.swift`

- [ ] **Step 1: Construct the manager + bridge its flows.** In `SessionStore.init`, after the repositories are built, add:

```swift
        manager = SessionManager(auth: auth, notifications: notifications)
```

with the property declared alongside the repos:

```swift
    private let manager: SessionManager
```

At the END of `init` (after the PushRegistrar wiring), start the bridges (SessionStore is `@MainActor`; these tasks live as long as the app):

```swift
        // Bridge the shared manager's flows into the @Published properties the views observe.
        Task { [weak self] in
            guard let self else { return }
            for await sessionState in manager.state {
                self.user = sessionState.user
                self.unreadCount = Int(sessionState.unread)
                self.isBootstrapping = sessionState.bootstrapping
            }
        }
        Task { [weak self] in
            guard let self else { return }
            for await message in manager.loginError {
                self.errorMessage = message
            }
        }
```

- [ ] **Step 2: Replace the session methods** (`bootstrap`, `login`, `logout`, `refreshBadge`, `markNotificationsRead`) — native push/passkey sequencing stays, state writes are gone (the bridge owns them):

```swift
    /// On launch: shared bootstrap (token → profile → badge), then native push enablement.
    func bootstrap() async {
        try? await manager.bootstrap()
        if manager.state.value.user != nil {
            await enablePushNotifications()
        }
    }

    func login(email: String, password: String) async {
        // Flatten the double optional (`try?` over an async function returning UserDto? yields
        // UserDto?? — a failed login would otherwise read as .some(nil) != nil).
        let loggedIn = (try? await manager.login(email: email, password: password, deviceName: "iOS")) ?? nil
        if loggedIn != nil {
            await enablePushNotifications()
        }
    }

    func logout() async {
        if let token = lastDeviceToken { try? await deviceTokens.unregister(token: token) }
        try? await manager.logout()
    }

    /// Refresh the bell badge's unread count (best-effort; the manager no-ops when logged out).
    func refreshBadge() async {
        try? await manager.refreshBadge()
    }

    /// Mark every notification read and clear the badge.
    func markNotificationsRead() async {
        try? await manager.markAllRead()
    }
```

In `passkeySignIn()`, the success line `user = try await passkey.loginVerify(...)` becomes:

```swift
            let verifiedUser = try await passkey.loginVerify(state: challenge.state, credentialJson: credential)
            manager.onLoggedIn(user: verifiedUser)
```

(the `await refreshBadge()` line after it is deleted — `onLoggedIn` refreshes internally; `enablePushNotifications()` stays). `passkeySignIn`'s `errorMessage = nil` / passkey-failure `errorMessage = "Couldn't sign in..."` assignments stay — the passkey error surface remains native (`loginError` only carries credential-login failures; a later `loginError` emission of nil will clear it, which matches today's clear-on-next-attempt behavior).

The `@Published` properties keep their declarations (now written only by the bridges + passkey error path). `isLoggedIn` computed property unchanged. Login-screen behavior note: iOS now shows the unified copy `"The provided credentials are incorrect."` (adjudication #1).

- [ ] **Step 3: Build**

Run: `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build`
Expected: BUILD SUCCEEDED. SKIE notes: `SessionState` arrives as a Swift class with `user: UserDto?`, `unread: Int32`, `bootstrapping: Bool`; `manager.state.value` gives the current value synchronously; suspend methods are `async throws`; `login` returns `UserDto?`. If `bootstrapping` arrives boxed (`KotlinBoolean`), adapt with `.boolValue` and note it.

- [ ] **Step 4: Commit**

```bash
git add iosApp/iosApp/SessionStore.swift
git commit -m "refactor(ios): SessionStore bridges shared SessionManager; ceremonies stay native"
```

---

## Task 5: Full milestone verification

- [ ] **Step 1: Everything green**

```bash
./gradlew :shared:allTests :androidApp:assembleDebug :androidApp:testDebugUnitTest --rerun-tasks
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build
```
Expected: all suites green (SessionManagerTest adds 10, ActivitiesStoreTest grows to 12); both apps build.

- [ ] **Step 2: No duplicated session logic remains**

```bash
grep -rn 'auth.me()\|auth.login\|auth.logout\|unreadCount = \|markAllRead' androidApp/src/main iosApp/iosApp | grep -v 'shared.presentation\|manager\.' || echo CLEAN
```
Expected: `CLEAN` (or only manager-delegating call sites, to be judged).

- [ ] **Step 3: AppConfig never staged**

Run: `git log main..HEAD --name-only --pretty= | sort -u | grep -c AppConfig || echo NEVER-STAGED`

- [ ] **Step 4: STOP — hand to the maintainer** (no push/merge). Report: test totals, build results, behavior notes (unified login copy on iOS; notifications refetch-on-open; Closed pill on Android; questionless-register failures now visible; iOS Try-again buttons). This completes the rollout — the final report should note the original goal is met: every screen's presentation logic + the session layer now live in commonMain.

---

## Self-review notes

- **Spec/M4 coverage:** SessionState + loginError + suspend bootstrap/login/logout/refreshBadge/markAllRead + onLoggedIn + clear (T2, matching the spec's signature sketch; `login` also sets user + refreshes badge per spec) · single app-wide instance via AppContainer/SessionStore (T3 S1, T4 S1) · Android thin wrappers, LoginViewModel keeps form state and delegates (T3) · iOS keeps DI/APNs/passkey, unregister-before-logout ordering preserved, `onLoggedIn` feed (T4) · badge/mark-read moved out of platform session layers (T3/T4) · login copy unification (T2/T4 note). Addenda: notifications fetch-on-open (T3 S6) · three M3 follow-ups (T1).
- **Types verified:** `AuthRepository.login(email,password,deviceName)/me()/logout()/isLoggedIn()`, `NotificationsRepository.notifications().unreadCount/markAllRead()`, `InMemoryTokenStore(token: String? = null)`, `UserDto(id,name,email,…defaults)`, `LoginResponse{token,user}` (login JSON is NOT envelope-wrapped — verified against Dtos.kt), `/notifications/read` route order in the mock (checked before `/notifications` since endsWith would match both).
- **Deliberate lifecycle rule stated twice** (manager KDoc + SessionViewModel KDoc): the app-scoped manager is never `clear()`ed by ViewModels.
- **Naming:** descriptive names throughout.
