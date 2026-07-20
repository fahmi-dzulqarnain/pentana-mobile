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

    private val refreshTracker = RefreshTracker(_refreshing)

    /** Lunch ids with a vote/not-attending request in flight — drives per-card pending UI + the tap-guard. */
    private val _inFlight = MutableStateFlow<Set<Long>>(emptySet())
    val inFlight: StateFlow<Set<Long>> = _inFlight.asStateFlow()

    /** Set when a fire-and-forget action fails; the UI shows it natively (Snackbar / alert). */
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()
    private val actionRunner = GuardedActionRunner(scope, _inFlight, _actionError)

    fun dismissActionError() { _actionError.value = null }

    init { load() }

    fun load() {
        scope.launch {
            _state.value = LunchUiState.Loading
            fetch()
        }
    }

    /** Suspends until the fetch completes so iOS .refreshable can hold the system spinner. */
    suspend fun refresh() = refreshTracker.run { fetch() }

    private suspend fun fetch() {
        _state.value = try {
            LunchUiState.Content(repo.lunches())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            LunchUiState.Error("Something went wrong. Pull down to refresh.")
        }
    }

    fun choose(lunchId: Long, mealOptionId: Long) =
        actionRunner.run(lunchId, SAVE_CHOICE_ERROR) {
            replace(repo.chooseOption(lunchId, mealOptionId))
        }

    fun notAttending(lunchId: Long) =
        actionRunner.run(lunchId, SAVE_CHOICE_ERROR) {
            replace(repo.markNotAttending(lunchId))
        }

    private fun replace(updated: LunchDto) {
        val current = (_state.value as? LunchUiState.Content)?.lunches ?: return
        _state.value = LunchUiState.Content(current.map { if (it.id == updated.id) updated else it })
    }

    fun clear() { scope.cancel() }

    private companion object {
        const val SAVE_CHOICE_ERROR = "Couldn't save your choice. Please try again."
    }
}
