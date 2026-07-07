package my.silentmode.pentana.shared.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import my.silentmode.pentana.shared.LunchRepository
import my.silentmode.pentana.shared.model.LunchDto

sealed interface LunchUiState {
    data object Loading : LunchUiState
    data class Error(val message: String) : LunchUiState
    data class Content(val lunches: List<LunchDto>) : LunchUiState
}

/**
 * Shared presentation logic for the Lunch screen. Owns its coroutine scope; each platform
 * calls [clear] on teardown (Android onCleared, iOS onDisappear).
 */
class LunchStore(private val repo: LunchRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow<LunchUiState>(LunchUiState.Loading)
    val state: StateFlow<LunchUiState> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init { load() }

    fun load() {
        scope.launch {
            _state.value = LunchUiState.Loading
            fetch()
        }
    }

    fun refresh() {
        scope.launch {
            _refreshing.value = true
            fetch()
            _refreshing.value = false
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
        scope.launch { runCatching { replace(repo.chooseOption(lunchId, mealOptionId)) } }
    }

    fun notAttending(lunchId: Long) {
        scope.launch { runCatching { replace(repo.markNotAttending(lunchId)) } }
    }

    private fun replace(updated: LunchDto) {
        val current = (_state.value as? LunchUiState.Content)?.lunches ?: return
        _state.value = LunchUiState.Content(current.map { if (it.id == updated.id) updated else it })
    }

    fun clear() { scope.cancel() }
}
