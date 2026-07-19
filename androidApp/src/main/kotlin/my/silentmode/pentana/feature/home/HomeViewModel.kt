package my.silentmode.pentana.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import my.silentmode.pentana.shared.DashboardRepository
import my.silentmode.pentana.shared.presentation.HomeStore

class HomeViewModel(repo: DashboardRepository) : ViewModel() {
    val store = HomeStore(repo)
    fun refresh() { viewModelScope.launch { store.refresh() } }
    override fun onCleared() { store.clear() }
}
