package my.silentmode.pentana.feature.notifications

import androidx.lifecycle.ViewModel
import my.silentmode.pentana.shared.NotificationsRepository
import my.silentmode.pentana.shared.presentation.NotificationsStore

class NotificationsViewModel(repo: NotificationsRepository) : ViewModel() {
    val store = NotificationsStore(repo)
    override fun onCleared() { store.clear() }
}
