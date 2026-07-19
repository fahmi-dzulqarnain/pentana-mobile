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
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                NotifUiState.Error("Couldn't load notifications.")
            }
        }
    }

    fun clear() { scope.cancel() }
}
