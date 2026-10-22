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
import my.silentmode.pentana.shared.ApiException
import my.silentmode.pentana.shared.BillsRepository
import my.silentmode.pentana.shared.model.BillDto
import my.silentmode.pentana.shared.model.BillsSummaryDto
import my.silentmode.pentana.shared.model.PaymentProofDto
import my.silentmode.pentana.shared.model.ReceiptPdf

sealed interface BillsUiState {
    data object Loading : BillsUiState
    data class Error(val message: String) : BillsUiState
    /** [proofs] are the member's submitted payment proofs, newest first, so a rejection's reason stays readable. */
    data class Content(
        val summary: BillsSummaryDto,
        val bills: List<BillDto>,
        val proofs: List<PaymentProofDto> = emptyList(),
    ) : BillsUiState
}

/** Proof-upload state machine driving the submit sheet on both platforms. */
sealed interface SubmitState {
    data object Idle : SubmitState
    data object Submitting : SubmitState
    data class Error(val message: String) : SubmitState
    data object Success : SubmitState
}

/**
 * Paid-receipt download, consumed once by the platform: on [Ready] it writes the PDF and opens the
 * system viewer, on [Error] it shows the message; either way it then calls [BillsStore.consumeReceipt].
 */
sealed interface ReceiptState {
    data object Idle : ReceiptState
    data class Downloading(val proofId: Long) : ReceiptState
    class Ready(val proofId: Long, val pdf: ReceiptPdf) : ReceiptState
    data class Error(val message: String) : ReceiptState
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

    /** Bumped by [resetSubmit]; a completion from an older generation must not write [submit]. */
    private var submitGeneration = 0

    private val _receipt = MutableStateFlow<ReceiptState>(ReceiptState.Idle)
    val receipt: StateFlow<ReceiptState> = _receipt.asStateFlow()

    /** Bumped by [consumeReceipt]; a download from an older generation must not write [receipt]. */
    private var receiptGeneration = 0

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
            BillsUiState.Content(repo.summary(), repo.bills(), repo.paymentProofs())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            BillsUiState.Error("Something went wrong. Pull down to refresh.")
        }
    }

    /**
     * Uploads a payment proof. [Submitting][SubmitState.Submitting] doubles as the double-submit
     * guard (the sheet is modal - no per-id set needed). Sets [SubmitState.Success] immediately
     * on upload completion (instant sheet feedback), then refetches so the list reflects the new
     * proof; the sheet observes [submit] to show progress/error/done.
     *
     * Callers must invoke this from the main thread - the Submitting check-and-set is a plain
     * read-modify-write; Main confinement is what makes the double-submit guard sound.
     *
     * A [resetSubmit] while the upload is in flight abandons it - the eventual completion still
     * refetches, but no longer writes [submit].
     */
    fun submitProof(imageBytes: ByteArray, fileName: String, amount: String, note: String?) {
        if (_submit.value is SubmitState.Submitting) return
        if (!canSubmitProof(amount, hasPhoto = true)) return
        val generation = ++submitGeneration
        _submit.value = SubmitState.Submitting
        scope.launch {
            try {
                repo.submitPaymentProof(imageBytes, fileName, amount.trim(), note?.takeUnless { it.isBlank() })
                if (generation == submitGeneration) _submit.value = SubmitState.Success
                fetch()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                if (generation == submitGeneration) _submit.value = SubmitState.Error("Upload failed. Please try again.")
            }
        }
    }

    fun resetSubmit() {
        submitGeneration += 1
        _submit.value = SubmitState.Idle
    }

    /**
     * Downloads the receipt PDF for a verified proof. One download at a time: ignored while one is in
     * flight (the viewer is modal anyway). Call from the main thread, like [submitProof].
     */
    fun openReceipt(proofId: Long) {
        if (_receipt.value is ReceiptState.Downloading) return
        val generation = ++receiptGeneration
        _receipt.value = ReceiptState.Downloading(proofId)
        scope.launch {
            val result = try {
                ReceiptState.Ready(proofId, repo.receipt(proofId))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                val notFound = (failure as? ApiException)?.status == 404
                ReceiptState.Error(if (notFound) RECEIPT_UNAVAILABLE_MESSAGE else RECEIPT_FAILED_MESSAGE)
            }
            if (generation == receiptGeneration) _receipt.value = result
        }
    }

    /** The platform has shown the receipt or its error, or left Bills; any in-flight download is dropped. */
    fun consumeReceipt() {
        receiptGeneration += 1
        _receipt.value = ReceiptState.Idle
    }

    fun clear() { scope.cancel() }
}

private const val RECEIPT_UNAVAILABLE_MESSAGE = "This receipt isn't available."
private const val RECEIPT_FAILED_MESSAGE = "Couldn't download the receipt. Please try again."
