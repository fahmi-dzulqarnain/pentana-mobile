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
 * A pick the server just saved, for the toast, the success haptic and Undo. [seq] grows with every
 * save, so a host can fire one haptic per save even when two saves carry the same text.
 */
data class LunchChange(
    val seq: Long,
    val lunchId: Long,
    /** "Changed Regular → Salad" or "Saved · Thu 29 Oct: Salad" */
    val message: String,
    /** False for a first answer: the API can't take a response back, so there is nothing to undo to. */
    val canUndo: Boolean,
)

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

    /** Lunch ids with a vote/not-attending request in flight - drives per-card pending UI + the tap-guard. */
    private val _inFlight = MutableStateFlow<Set<Long>>(emptySet())
    val inFlight: StateFlow<Set<Long>> = _inFlight.asStateFlow()

    /** Set when a fire-and-forget action fails; the UI shows it natively (Snackbar / alert). */
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()
    private val actionRunner = GuardedActionRunner(scope, _inFlight, _actionError)

    fun dismissActionError() { _actionError.value = null }

    private val _lastChange = MutableStateFlow<LunchChange?>(null)
    val lastChange: StateFlow<LunchChange?> = _lastChange.asStateFlow()
    private var changeSeq = 0L

    /** What each lunch was answered as before its latest change: an option id, or null for not attending. */
    private val undoTargets = mutableMapOf<Long, Long?>()

    fun dismissChange() { _lastChange.value = null }

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

    fun choose(lunchId: Long, lunchOptionId: Long) = save(lunchId, lunchOptionId)

    fun notAttending(lunchId: Long) = save(lunchId, null)

    /** Puts back the answer the latest change replaced. */
    fun undo() {
        val change = _lastChange.value?.takeIf { it.canUndo } ?: return
        if (change.lunchId !in undoTargets) return
        val target = undoTargets.getValue(change.lunchId)
        actionRunner.run(change.lunchId, SAVE_CHOICE_ERROR) {
            val updated = if (target == null) repo.markNotAttending(change.lunchId) else repo.chooseOption(change.lunchId, target)
            replace(updated)
            undoTargets.remove(change.lunchId)
            changeSeq += 1
            _lastChange.value = LunchChange(changeSeq, change.lunchId, "Put back " + answerLabel(updated), canUndo = false)
        }
    }

    private fun save(lunchId: Long, lunchOptionId: Long?) {
        val before = currentLunches().firstOrNull { it.id == lunchId }
        // Tapping the answer that's already saved changes nothing: no request, no toast.
        if (before != null && before.responded && answerId(before) == lunchOptionId) return
        actionRunner.run(lunchId, SAVE_CHOICE_ERROR) {
            val updated = if (lunchOptionId == null) repo.markNotAttending(lunchId) else repo.chooseOption(lunchId, lunchOptionId)
            replace(updated)
            // An old-server answer known only by meal option can't be restored exactly, so it isn't undoable.
            val canUndo = before != null && before.responded && (before.myLunchOptionId != null || before.myMealOptionId == null)
            if (canUndo) undoTargets[lunchId] = answerId(before!!) else undoTargets.remove(lunchId)
            changeSeq += 1
            _lastChange.value = LunchChange(
                seq = changeSeq,
                lunchId = lunchId,
                message = if (canUndo) {
                    "Changed ${answerLabel(before!!)} → ${answerLabel(updated)}"
                } else {
                    "Saved · ${lunchDayLabel(updated)}: ${answerLabel(updated)}"
                },
                canUndo = canUndo,
            )
        }
    }

    private fun currentLunches(): List<LunchDto> = (_state.value as? LunchUiState.Content)?.lunches.orEmpty()

    private fun answerId(lunch: LunchDto): Long? = lunch.myLunchOptionId

    private fun answerLabel(lunch: LunchDto): String =
        lunchPickedOptionName(lunch) ?: if (lunch.myLunchOptionId == null && lunch.myMealOptionId == null) "Not attending" else "your pick"

    private fun replace(updated: LunchDto) {
        val current = (_state.value as? LunchUiState.Content)?.lunches ?: return
        _state.value = LunchUiState.Content(current.map { if (it.id == updated.id) updated else it })
    }

    fun clear() { scope.cancel() }

    private companion object {
        const val SAVE_CHOICE_ERROR = "Couldn't save your choice. Please try again."
    }
}
