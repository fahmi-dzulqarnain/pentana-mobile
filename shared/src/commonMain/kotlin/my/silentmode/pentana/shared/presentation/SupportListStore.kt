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
import my.silentmode.pentana.shared.SupportRepository
import my.silentmode.pentana.shared.model.SupportTicketDto

sealed interface SupportListUiState {
    data object Loading : SupportListUiState
    data class Error(val message: String) : SupportListUiState
    data class Content(val open: List<SupportTicketDto>, val closed: List<SupportTicketDto>) : SupportListUiState
}

sealed interface SupportCreateState {
    data object Idle : SupportCreateState
    data object Sending : SupportCreateState
    data class Error(val message: String) : SupportCreateState
    data class Sent(val ticketId: Long) : SupportCreateState
}

/**
 * The member's "Ask the committee" list and the New question form. Open tickets with a committee
 * reply waiting come first; ISO-8601 timestamps from the server sort correctly as strings.
 */
class SupportListStore(private val repo: SupportRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow<SupportListUiState>(SupportListUiState.Loading)
    val state: StateFlow<SupportListUiState> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private val refreshTracker = RefreshTracker(_refreshing)

    private val _create = MutableStateFlow<SupportCreateState>(SupportCreateState.Idle)
    val create: StateFlow<SupportCreateState> = _create.asStateFlow()

    /** Bumped by [resetCreate]; a completion from an older generation must not write [create]. */
    private var createGeneration = 0

    init { load() }

    fun load() {
        scope.launch {
            _state.value = SupportListUiState.Loading
            fetch()
        }
    }

    /** Suspends until the fetch completes so iOS .refreshable can hold the system spinner. */
    suspend fun refresh() = refreshTracker.run { fetch() }

    private suspend fun fetch() {
        _state.value = try {
            val tickets = repo.tickets()
            val (closed, open) = tickets.partition { supportStatus(it.status) == SupportStatus.CLOSED }
            SupportListUiState.Content(
                open = open.sortedWith(
                    compareByDescending<SupportTicketDto> { supportStatus(it.status) == SupportStatus.REPLIED }
                        .thenByDescending { it.lastMessageAt },
                ),
                closed = closed.sortedByDescending { it.lastMessageAt },
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            SupportListUiState.Error("Something went wrong. Pull down to refresh.")
        }
    }

    /**
     * Sends a new question. [SupportCreateState.Sending] doubles as the double-submit guard (the
     * form is modal). Call from the main thread, like BillsStore.submitProof.
     */
    fun create(type: String, subject: String, body: String, screenshot: ByteArray?, fileName: String?) {
        if (_create.value is SupportCreateState.Sending) return
        if (subject.isBlank() || body.isBlank()) return
        val generation = ++createGeneration
        _create.value = SupportCreateState.Sending
        scope.launch {
            try {
                val ticket = repo.open(type, subject.trim(), body.trim(), screenshot, fileName)
                if (generation == createGeneration) _create.value = SupportCreateState.Sent(ticket.id)
                fetch()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                val tooBig = (failure as? ApiException)?.status == 422
                if (generation == createGeneration) {
                    _create.value = SupportCreateState.Error(
                        if (tooBig) "Please check your question. Screenshots must be images under 5 MB." else "Couldn't send. Please try again.",
                    )
                }
            }
        }
    }

    /** Ignored while sending: the form can't be closed then, and disarming the guard could send twice. */
    fun resetCreate() {
        if (_create.value is SupportCreateState.Sending) return
        createGeneration += 1
        _create.value = SupportCreateState.Idle
    }

    fun clear() { scope.cancel() }
}
