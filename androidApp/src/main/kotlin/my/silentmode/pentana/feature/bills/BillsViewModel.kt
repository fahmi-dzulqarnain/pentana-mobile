package my.silentmode.pentana.feature.bills

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import my.silentmode.pentana.core.PickedPhoto
import my.silentmode.pentana.shared.BillsRepository
import my.silentmode.pentana.shared.model.BillDto
import my.silentmode.pentana.shared.model.BillsSummaryDto

/** Submit is allowed once an amount is entered and a photo is attached. */
fun canSubmitProof(amount: String, hasPhoto: Boolean): Boolean = amount.isNotBlank() && hasPhoto

sealed interface BillsUiState {
    data object Loading : BillsUiState
    data class Error(val message: String) : BillsUiState
    data class Content(val summary: BillsSummaryDto, val bills: List<BillDto>) : BillsUiState
}

sealed interface SubmitState {
    data object Idle : SubmitState
    data object Submitting : SubmitState
    data class Error(val message: String) : SubmitState
    data object Success : SubmitState
}

class BillsViewModel(private val repo: BillsRepository) : ViewModel() {
    private val _state = MutableStateFlow<BillsUiState>(BillsUiState.Loading)
    val state = _state.asStateFlow()

    var refreshing by mutableStateOf(false)
        private set
    var submit by mutableStateOf<SubmitState>(SubmitState.Idle)
        private set

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = BillsUiState.Loading
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
            BillsUiState.Content(repo.summary(), repo.bills())
        } catch (_: Exception) {
            BillsUiState.Error("Something went wrong. Pull down to refresh.")
        }
    }

    fun submitProof(amount: String, note: String, photo: PickedPhoto) {
        if (!canSubmitProof(amount, true)) return
        submit = SubmitState.Submitting
        viewModelScope.launch {
            try {
                repo.submitPaymentProof(photo.bytes, photo.name, amount.trim(), note.ifBlank { null })
                submit = SubmitState.Success
                fetch()
            } catch (_: Exception) {
                submit = SubmitState.Error("Upload failed. Please try again.")
            }
        }
    }

    fun resetSubmit() { submit = SubmitState.Idle }
}
