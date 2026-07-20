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
import my.silentmode.pentana.shared.ActivitiesRepository
import my.silentmode.pentana.shared.model.ActivityDto

sealed interface ActivitiesUiState {
    data object Loading : ActivitiesUiState
    data class Error(val message: String) : ActivitiesUiState
    data class Content(val activities: List<ActivityDto>) : ActivitiesUiState
}

/** Registration state machine driving the question sheet on both platforms. */
sealed interface RegState {
    data object Idle : RegState
    data object Submitting : RegState
    data class Error(val message: String) : RegState
    data object Success : RegState
}

/**
 * Shared presentation logic for the Activities screen. Owns its coroutine scope; the host controls
 * teardown. Android calls [clear] from ViewModel.onCleared(); iOS reuses the store across view
 * reappearance and lets it release with the view, so it does not call [clear] on disappear.
 */
class ActivitiesStore(private val repo: ActivitiesRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow<ActivitiesUiState>(ActivitiesUiState.Loading)
    val state: StateFlow<ActivitiesUiState> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private val refreshTracker = RefreshTracker(_refreshing)

    /** Activity ids with a register/cancel request in flight — drives per-card pending UI + the tap-guard. */
    private val _inFlight = MutableStateFlow<Set<Long>>(emptySet())
    val inFlight: StateFlow<Set<Long>> = _inFlight.asStateFlow()

    /** Set when a fire-and-forget action fails; the UI shows it natively (Snackbar / alert). */
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()
    private val actionRunner = GuardedActionRunner(scope, _inFlight, _actionError)

    private val _reg = MutableStateFlow<RegState>(RegState.Idle)
    val reg: StateFlow<RegState> = _reg.asStateFlow()

    /** Bumped by [resetReg]; a completion from an older generation must not write [reg]. */
    private var regGeneration = 0

    init { load() }

    fun load() {
        scope.launch {
            _state.value = ActivitiesUiState.Loading
            fetch()
        }
    }

    /** Suspends until the fetch completes so iOS .refreshable can hold the system spinner. */
    suspend fun refresh() = refreshTracker.run { fetch() }

    private suspend fun fetch() {
        _state.value = try {
            ActivitiesUiState.Content(repo.activities())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            ActivitiesUiState.Error("Something went wrong. Pull down to refresh.")
        }
    }

    /**
     * Registers for [activityId] with the sheet's [answers] (pass them raw; empty answers are
     * dropped here via [registrationPayload]). Drives BOTH the [reg] machine (sheet feedback)
     * and [inFlight] (card dimming — for no-questions registrations it replaces iOS's busyId).
     * A [resetReg] while in flight abandons the submission: the optimistic replace still lands,
     * but the stale terminal state no longer writes [reg].
     *
     * Callers must invoke this from the main thread — the guards are plain read-modify-writes.
     */
    fun register(activityId: Long, answers: Map<String, String>) {
        if (_reg.value is RegState.Submitting) return
        if (activityId in _inFlight.value) return
        _inFlight.value = _inFlight.value + activityId
        val generation = ++regGeneration
        _reg.value = RegState.Submitting
        scope.launch {
            try {
                replace(repo.register(activityId, registrationPayload(answers)))
                if (generation == regGeneration) _reg.value = RegState.Success
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                if (generation == regGeneration) _reg.value = RegState.Error("Registration failed. Please check your answers.")
            } finally {
                _inFlight.value = _inFlight.value - activityId
            }
        }
    }

    fun cancel(activityId: Long) =
        actionRunner.run(activityId, "Couldn't cancel. Please try again.") {
            replace(repo.cancel(activityId))
        }

    fun resetReg() {
        regGeneration += 1
        _reg.value = RegState.Idle
    }

    fun dismissActionError() { _actionError.value = null }

    private fun replace(updated: ActivityDto) {
        val current = (_state.value as? ActivitiesUiState.Content)?.activities ?: return
        _state.value = ActivitiesUiState.Content(current.map { if (it.id == updated.id) updated else it })
    }

    fun clear() { scope.cancel() }
}
