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
import my.silentmode.pentana.shared.LunchRepository
import my.silentmode.pentana.shared.model.LunchDto

sealed interface LunchUiState {
    data object Loading : LunchUiState
    data class Error(val message: String) : LunchUiState
    data class Content(val lunches: List<LunchDto>) : LunchUiState
}

/**
 * Shared presentation logic for the Lunch screen. Owns its coroutine scope; the host controls
 * teardown. Android calls [clear] from ViewModel.onCleared(); iOS reuses the store across view
 * reappearance and lets it release with the view, so it does not call [clear] on disappear.
 */
class LunchStore(private val repo: LunchRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow<LunchUiState>(LunchUiState.Loading)
    val state: StateFlow<LunchUiState> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** Lunch ids with a vote/not-attending request in flight — drives per-card pending UI + the tap-guard. */
    private val _inFlight = MutableStateFlow<Set<Long>>(emptySet())
    val inFlight: StateFlow<Set<Long>> = _inFlight.asStateFlow()

    /** Set when a fire-and-forget action fails; the UI shows it natively (Snackbar / alert). */
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    fun dismissActionError() { _actionError.value = null }

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
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            LunchUiState.Error("Something went wrong. Pull down to refresh.")
        }
    }

    fun choose(lunchId: Long, mealOptionId: Long) {
        if (lunchId in _inFlight.value) return
        _inFlight.value = _inFlight.value + lunchId // synchronous check-and-set: no dispatch gap before the guard arms
        _actionError.value = null
        scope.launch {
            try {
                replace(repo.chooseOption(lunchId, mealOptionId))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _actionError.value = "Couldn't save your choice. Please try again."
            } finally {
                _inFlight.value = _inFlight.value - lunchId
            }
        }
    }

    fun notAttending(lunchId: Long) {
        if (lunchId in _inFlight.value) return
        _inFlight.value = _inFlight.value + lunchId // synchronous check-and-set: no dispatch gap before the guard arms
        _actionError.value = null
        scope.launch {
            try {
                replace(repo.markNotAttending(lunchId))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _actionError.value = "Couldn't save your choice. Please try again."
            } finally {
                _inFlight.value = _inFlight.value - lunchId
            }
        }
    }

    private fun replace(updated: LunchDto) {
        val current = (_state.value as? LunchUiState.Content)?.lunches ?: return
        _state.value = LunchUiState.Content(current.map { if (it.id == updated.id) updated else it })
    }

    fun clear() { scope.cancel() }
}
