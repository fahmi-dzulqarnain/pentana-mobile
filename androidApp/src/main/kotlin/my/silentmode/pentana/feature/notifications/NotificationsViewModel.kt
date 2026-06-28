package my.silentmode.pentana.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import my.silentmode.pentana.shared.NotificationsRepository
import my.silentmode.pentana.shared.model.NotificationDto

sealed interface NotifUiState {
    data object Loading : NotifUiState
    data class Error(val message: String) : NotifUiState
    data class Content(val items: List<NotificationDto>) : NotifUiState
}

class NotificationsViewModel(private val repo: NotificationsRepository) : ViewModel() {
    private val _state = MutableStateFlow<NotifUiState>(NotifUiState.Loading)
    val state = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = NotifUiState.Loading
            _state.value = try {
                NotifUiState.Content(repo.notifications().data)
            } catch (_: Exception) {
                NotifUiState.Error("Couldn't load notifications.")
            }
        }
    }
}
