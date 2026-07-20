package my.silentmode.pentana.feature.activities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import my.silentmode.pentana.shared.ActivitiesRepository
import my.silentmode.pentana.shared.presentation.ActivitiesStore

class ActivitiesViewModel(repo: ActivitiesRepository) : ViewModel() {
    val store = ActivitiesStore(repo)
    fun refresh() { viewModelScope.launch { store.refresh() } }
    override fun onCleared() { store.clear() }
}
