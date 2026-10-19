package my.silentmode.pentana.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import my.silentmode.pentana.core.UpdateCheckSchedule
import my.silentmode.pentana.shared.AppUpdateRepository
import my.silentmode.pentana.shared.model.AndroidReleaseDto

/** Offers a newer sideloaded build, if there is one. Failures stay silent: the app works without it. */
class UpdateViewModel(
    private val appUpdates: AppUpdateRepository,
    private val schedule: UpdateCheckSchedule,
    private val installedVersionCode: Int,
) : ViewModel() {
    private val _availableUpdate = MutableStateFlow<AndroidReleaseDto?>(null)
    val availableUpdate: StateFlow<AndroidReleaseDto?> = _availableUpdate.asStateFlow()

    private var checked = false

    fun checkIfDue() {
        if (checked || !schedule.isDue()) return
        checked = true
        viewModelScope.launch {
            try {
                _availableUpdate.value = appUpdates.availableUpdate(installedVersionCode)
                schedule.markChecked()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Offline or server trouble: try again next launch.
            }
        }
    }

    fun dismiss() {
        _availableUpdate.value = null
    }
}
