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
import my.silentmode.pentana.shared.SupportRepository
import my.silentmode.pentana.shared.model.ReceiptPdf
import my.silentmode.pentana.shared.model.SupportTicketDetailDto

sealed interface SupportThreadUiState {
    data object Loading : SupportThreadUiState
    data class Error(val message: String) : SupportThreadUiState
    data class Content(val detail: SupportTicketDetailDto) : SupportThreadUiState
}

/** A message's screenshot, fetched with the login token (the URL alone doesn't work). */
sealed interface SupportImage {
    data object Loading : SupportImage
    class Loaded(val bytes: ByteArray) : SupportImage
    data object Failed : SupportImage
}

/**
 * A committee PDF download, consumed once by the platform: on [Ready] it writes the file and opens
 * the system viewer, on [Error] it shows the message; either way it then calls
 * [SupportThreadStore.consumePdf].
 */
sealed interface SupportPdfState {
    data object Idle : SupportPdfState
    data class Downloading(val messageId: Long) : SupportPdfState
    class Ready(val messageId: Long, val pdf: ReceiptPdf) : SupportPdfState
    data class Error(val message: String) : SupportPdfState
}

/**
 * One message's screenshot state. A list of these, not a Map<Long, …>: a Kotlin map's Long keys
 * reach Swift as NSNumber, and SKIE's [KotlinLong: …] cast of them crashes the app.
 */
class SupportImageEntry(val messageId: Long, val image: SupportImage)

/**
 * One ticket's conversation. Replying and resolving are guarded per action (reply on the ticket
 * id, resolve on its negative) so a double tap posts once and the two never block each other.
 * A reply re-fetches the ticket so its status (a reply reopens a closed ticket) is current.
 */
class SupportThreadStore(private val repo: SupportRepository, val ticketId: Long) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow<SupportThreadUiState>(SupportThreadUiState.Loading)
    val state: StateFlow<SupportThreadUiState> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private val refreshTracker = RefreshTracker(_refreshing)

    private val _inFlight = MutableStateFlow<Set<Long>>(emptySet())
    val inFlight: StateFlow<Set<Long>> = _inFlight.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private val actionRunner = GuardedActionRunner(scope, _inFlight, _actionError)

    private val _images = MutableStateFlow<List<SupportImageEntry>>(emptyList())
    val images: StateFlow<List<SupportImageEntry>> = _images.asStateFlow()

    private val _pdf = MutableStateFlow<SupportPdfState>(SupportPdfState.Idle)
    val pdf: StateFlow<SupportPdfState> = _pdf.asStateFlow()

    /** Bumped by [consumePdf]; a download from an older generation must not write [pdf]. */
    private var pdfGeneration = 0

    init { load() }

    fun load() {
        scope.launch {
            _state.value = SupportThreadUiState.Loading
            fetch()
        }
    }

    suspend fun refresh() = refreshTracker.run { fetch() }

    private suspend fun fetch() {
        try {
            _state.value = SupportThreadUiState.Content(repo.ticket(ticketId))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // A failed refresh keeps the conversation on screen; only a first load shows the error state.
            if (_state.value is SupportThreadUiState.Content) {
                _actionError.value = "Couldn't refresh. Please try again."
            } else {
                _state.value = SupportThreadUiState.Error("Couldn't open this question. Pull down to try again.")
            }
        }
    }

    /**
     * Call from the main thread: the in-flight check-and-set is what stops a double tap posting twice.
     * The platforms clear their reply box on send; a failure hands the text back via [failedReply].
     */
    fun reply(body: String) {
        val text = body.trim()
        if (text.isEmpty()) return
        actionRunner.run(ticketId, "Couldn't send your reply. Please try again.") {
            try {
                repo.reply(ticketId, text)
            } catch (failure: Exception) {
                if (failure !is CancellationException) _failedReply.value = text
                throw failure
            }
            refreshDetail()
        }
    }

    fun resolve() {
        actionRunner.run(-ticketId, "Couldn't update this question. Please try again.") {
            _state.value = SupportThreadUiState.Content(repo.resolve(ticketId))
        }
    }

    /** Fetches a screenshot once per message; a failure becomes [SupportImage.Failed], never a crash. */
    fun loadImage(messageId: Long, url: String) {
        if (_images.value.any { it.messageId == messageId }) return
        _images.value = _images.value + SupportImageEntry(messageId, SupportImage.Loading)
        scope.launch {
            val image = try {
                SupportImage.Loaded(repo.attachment(url))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                SupportImage.Failed
            }
            _images.value = _images.value.filterNot { it.messageId == messageId } + SupportImageEntry(messageId, image)
        }
    }

    /** Downloads a committee PDF for the platform to open. One at a time; a tap while one downloads is ignored. */
    fun openPdf(messageId: Long, url: String) {
        if (_pdf.value is SupportPdfState.Downloading) return
        val generation = ++pdfGeneration
        _pdf.value = SupportPdfState.Downloading(messageId)
        scope.launch {
            val result = try {
                SupportPdfState.Ready(messageId, repo.attachmentPdf(messageId, url))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                SupportPdfState.Error("Couldn't open the attachment. Please try again.")
            }
            if (generation == pdfGeneration) _pdf.value = result
        }
    }

    /** The platform has shown the PDF or its error, or left the thread; any in-flight download is dropped. */
    fun consumePdf() {
        pdfGeneration += 1
        _pdf.value = SupportPdfState.Idle
    }

    /** The text of a reply that failed to send, for the platform to put back in its reply box. */
    private val _failedReply = MutableStateFlow<String?>(null)
    val failedReply: StateFlow<String?> = _failedReply.asStateFlow()

    /** The platform restored [failedReply] into its reply box. */
    fun consumeFailedReply() { _failedReply.value = null }

    fun dismissActionError() { _actionError.value = null }

    fun clear() { scope.cancel() }

    // A failed re-fetch after a successful reply keeps the old thread rather than showing an error.
    private suspend fun refreshDetail() {
        try {
            _state.value = SupportThreadUiState.Content(repo.ticket(ticketId))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // The reply was sent; pull-to-refresh will catch the thread up.
        }
    }
}
