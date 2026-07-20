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
