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
import my.silentmode.pentana.shared.BillsRepository
import my.silentmode.pentana.shared.model.BillDto
import my.silentmode.pentana.shared.model.BillsSummaryDto

sealed interface BillsUiState {
    data object Loading : BillsUiState
    data class Error(val message: String) : BillsUiState
    data class Content(val summary: BillsSummaryDto, val bills: List<BillDto>) : BillsUiState
}

/** Proof-upload state machine driving the submit sheet on both platforms. */
sealed interface SubmitState {
    data object Idle : SubmitState
    data object Submitting : SubmitState
    data class Error(val message: String) : SubmitState
    data object Success : SubmitState
}

/**
 * Shared presentation logic for the Bills screen. Owns its coroutine scope; the host controls
 * teardown. Android calls [clear] from ViewModel.onCleared(); iOS reuses the store across view
 * reappearance and lets it release with the view, so it does not call [clear] on disappear.
 */
class BillsStore(private val repo: BillsRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow<BillsUiState>(BillsUiState.Loading)
    val state: StateFlow<BillsUiState> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private val refreshTracker = RefreshTracker(_refreshing)

    private val _submit = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val submit: StateFlow<SubmitState> = _submit.asStateFlow()

    init { load() }

    fun load() {
        scope.launch {
            _state.value = BillsUiState.Loading
            fetch()
        }
    }

    /** Suspends until the fetch completes so iOS .refreshable can hold the system spinner. */
    suspend fun refresh() = refreshTracker.run { fetch() }

    private suspend fun fetch() {
        _state.value = try {
            BillsUiState.Content(repo.summary(), repo.bills())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            BillsUiState.Error("Something went wrong. Pull down to refresh.")
        }
    }

    /**
     * Uploads a payment proof. [Submitting][SubmitState.Submitting] doubles as the double-submit
     * guard (the sheet is modal — no per-id set needed). Sets [SubmitState.Success] immediately
     * on upload completion (instant sheet feedback), then refetches so the list reflects the new
     * proof; the sheet observes [submit] to show progress/error/done.
     */
    fun submitProof(imageBytes: ByteArray, fileName: String, amount: String, note: String?) {
        if (_submit.value is SubmitState.Submitting) return
        if (!canSubmitProof(amount, hasPhoto = true)) return
        _submit.value = SubmitState.Submitting
        scope.launch {
            try {
                repo.submitPaymentProof(imageBytes, fileName, amount.trim(), note?.takeUnless { it.isBlank() })
                _submit.value = SubmitState.Success
                fetch()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _submit.value = SubmitState.Error("Upload failed. Please try again.")
            }
        }
    }

    fun resetSubmit() { _submit.value = SubmitState.Idle }

    fun clear() { scope.cancel() }
}
