package my.silentmode.pentana.feature.home

import androidx.lifecycle.ViewModel
import my.silentmode.pentana.shared.DashboardRepository
import my.silentmode.pentana.shared.presentation.HomeStore

class HomeViewModel(repo: DashboardRepository) : ViewModel() {
    val store = HomeStore(repo)
    override fun onCleared() { store.clear() }
}
