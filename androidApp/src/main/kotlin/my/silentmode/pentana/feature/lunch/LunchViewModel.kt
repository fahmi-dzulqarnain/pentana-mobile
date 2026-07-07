package my.silentmode.pentana.feature.lunch

import androidx.lifecycle.ViewModel
import my.silentmode.pentana.shared.LunchRepository
import my.silentmode.pentana.shared.presentation.LunchStore

class LunchViewModel(repo: LunchRepository) : ViewModel() {
    val store = LunchStore(repo)
    override fun onCleared() { store.clear() }
}
