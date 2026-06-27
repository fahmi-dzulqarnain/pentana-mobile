package my.silentmode.pentana.feature.lunch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import my.silentmode.pentana.shared.LunchRepository
import my.silentmode.pentana.shared.model.LunchDto

sealed interface LunchUiState {
    data object Loading : LunchUiState
    data class Error(val message: String) : LunchUiState
    data class Content(val lunches: List<LunchDto>) : LunchUiState
}

class LunchViewModel(private val repo: LunchRepository) : ViewModel() {
    private val _state = MutableStateFlow<LunchUiState>(LunchUiState.Loading)
    val state = _state.asStateFlow()

    var refreshing by mutableStateOf(false)
        private set

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = LunchUiState.Loading
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
            LunchUiState.Content(repo.lunches())
        } catch (_: Exception) {
            LunchUiState.Error("Something went wrong. Pull down to refresh.")
        }
    }

    fun choose(lunchId: Long, mealOptionId: Long) {
        viewModelScope.launch {
            try { replace(repo.chooseOption(lunchId, mealOptionId)) } catch (_: Exception) {}
        }
    }

    fun notAttending(lunchId: Long) {
        viewModelScope.launch {
            try { replace(repo.markNotAttending(lunchId)) } catch (_: Exception) {}
        }
    }

    private fun replace(updated: LunchDto) {
        val current = (_state.value as? LunchUiState.Content)?.lunches ?: return
        _state.value = LunchUiState.Content(current.map { if (it.id == updated.id) updated else it })
    }
}
