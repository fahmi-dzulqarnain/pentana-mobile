package my.silentmode.pentana.feature.activities

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import my.silentmode.pentana.shared.ActivitiesRepository
import my.silentmode.pentana.shared.model.ActivityDto

sealed interface ActivitiesUiState {
    data object Loading : ActivitiesUiState
    data class Error(val message: String) : ActivitiesUiState
    data class Content(val activities: List<ActivityDto>) : ActivitiesUiState
}

sealed interface RegState {
    data object Idle : RegState
    data object Submitting : RegState
    data class Error(val message: String) : RegState
    data object Success : RegState
}

class ActivitiesViewModel(private val repo: ActivitiesRepository) : ViewModel() {
    private val _state = MutableStateFlow<ActivitiesUiState>(ActivitiesUiState.Loading)
    val state = _state.asStateFlow()

    var refreshing by mutableStateOf(false)
        private set
    var reg by mutableStateOf<RegState>(RegState.Idle)
        private set

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = ActivitiesUiState.Loading
            fetch()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            refreshing = true
            fetch()
            refreshing = false
        }
    }

    private suspend fun fetch() {
        _state.value = try {
            ActivitiesUiState.Content(repo.activities())
        } catch (_: Exception) {
            ActivitiesUiState.Error("Something went wrong. Pull down to refresh.")
        }
    }

    fun register(activityId: Long, answers: Map<String, String>) {
        reg = RegState.Submitting
        viewModelScope.launch {
            try {
                replace(repo.register(activityId, answers))
                reg = RegState.Success
            } catch (_: Exception) {
                reg = RegState.Error("Registration failed. Please check your answers.")
            }
        }
    }

    fun cancel(activityId: Long) {
        viewModelScope.launch {
            try { replace(repo.cancel(activityId)) } catch (_: Exception) {}
        }
    }

    fun resetReg() { reg = RegState.Idle }

    private fun replace(updated: ActivityDto) {
        val current = (_state.value as? ActivitiesUiState.Content)?.activities ?: return
        _state.value = ActivitiesUiState.Content(current.map { if (it.id == updated.id) updated else it })
    }
}
