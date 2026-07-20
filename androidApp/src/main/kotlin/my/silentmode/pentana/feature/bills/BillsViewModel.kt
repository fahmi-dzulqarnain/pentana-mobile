package my.silentmode.pentana.feature.bills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import my.silentmode.pentana.shared.BillsRepository
import my.silentmode.pentana.shared.presentation.BillsStore

class BillsViewModel(repo: BillsRepository) : ViewModel() {
    val store = BillsStore(repo)
    fun refresh() { viewModelScope.launch { store.refresh() } }
    override fun onCleared() { store.clear() }
}
