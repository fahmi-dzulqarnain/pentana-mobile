package my.silentmode.pentana.feature.support

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import my.silentmode.pentana.shared.SupportRepository
import my.silentmode.pentana.shared.presentation.SupportListStore
import my.silentmode.pentana.shared.presentation.SupportThreadStore

class SupportListViewModel(repo: SupportRepository) : ViewModel() {
    val store = SupportListStore(repo)
    fun refresh() { viewModelScope.launch { store.refresh() } }
    override fun onCleared() { store.clear() }
}

class SupportThreadViewModel(repo: SupportRepository, ticketId: Long) : ViewModel() {
    val store = SupportThreadStore(repo, ticketId)
    fun refresh() { viewModelScope.launch { store.refresh() } }
    override fun onCleared() { store.clear() }
}
