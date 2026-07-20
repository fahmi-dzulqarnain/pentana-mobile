package my.silentmode.pentana.feature.lunch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import my.silentmode.pentana.shared.LunchRepository
import my.silentmode.pentana.shared.presentation.LunchStore

class LunchViewModel(repo: LunchRepository) : ViewModel() {
    val store = LunchStore(repo)
    fun refresh() { viewModelScope.launch { store.refresh() } }
    override fun onCleared() { store.clear() }
}
