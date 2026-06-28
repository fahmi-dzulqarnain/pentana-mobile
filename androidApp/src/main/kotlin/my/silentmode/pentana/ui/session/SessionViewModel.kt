package my.silentmode.pentana.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import my.silentmode.pentana.shared.AuthRepository
import my.silentmode.pentana.shared.NotificationsRepository
import my.silentmode.pentana.shared.model.UserDto

class SessionViewModel(
    private val auth: AuthRepository,
    private val notifications: NotificationsRepository,
) : ViewModel() {

    data class State(
        val user: UserDto? = null,
        val unread: Int = 0,
        val bootstrapping: Boolean = true,
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    /** On launch: if a token exists, fetch the profile; drop it if the token is stale. */
    fun bootstrap() {
        viewModelScope.launch {
            if (auth.isLoggedIn()) {
                try {
                    val user = auth.me()
                    _state.update { it.copy(user = user, bootstrapping = false) }
                    refreshBadge()
                } catch (_: Exception) {
                    try { auth.logout() } catch (_: Exception) {}
                    _state.update { it.copy(user = null, bootstrapping = false) }
                }
            } else {
                _state.update { it.copy(bootstrapping = false) }
            }
        }
    }

    fun onLoggedIn(user: UserDto) {
        _state.update { it.copy(user = user) }
        refreshBadge()
    }

    fun logout() {
        viewModelScope.launch {
            try { auth.logout() } catch (_: Exception) {}
            _state.update { State(bootstrapping = false) }
        }
    }

    fun refreshBadge() {
        viewModelScope.launch {
            try {
                val unread = notifications.notifications().unreadCount
                _state.update { it.copy(unread = unread) }
            } catch (_: Exception) {
            }
        }
    }

    /** Opening the bell marks everything read and clears the badge. */
    fun markNotificationsRead() {
        viewModelScope.launch {
            try { notifications.markAllRead() } catch (_: Exception) {}
            _state.update { it.copy(unread = 0) }
        }
    }
}
