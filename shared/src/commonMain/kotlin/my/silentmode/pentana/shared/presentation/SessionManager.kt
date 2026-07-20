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
