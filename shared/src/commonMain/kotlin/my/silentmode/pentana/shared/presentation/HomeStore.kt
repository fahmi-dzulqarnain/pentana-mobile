package my.silentmode.pentana.shared.presentation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import my.silentmode.pentana.shared.DashboardRepository
import my.silentmode.pentana.shared.model.DashboardDto

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Error(val message: String) : HomeUiState
    data class Content(val data: DashboardDto) : HomeUiState
}

/**
 * Shared presentation logic for the Home (dashboard) screen. Owns its coroutine scope; the host
 * controls teardown. Android calls [clear] from ViewModel.onCleared(); iOS reuses the store across
 * view reappearance and lets it release with the view, so it does not call [clear] on disappear.
 */
class HomeStore(private val repo: DashboardRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val refreshTracker = RefreshTracker(_refreshing)

    init { load() }

    fun load() {
        scope.launch {
            _state.value = HomeUiState.Loading
            fetch()
        }
    }

    /** Suspends until the fetch completes so iOS .refreshable can hold the system spinner. */
    suspend fun refresh() = refreshTracker.run { fetch() }

    private suspend fun fetch() {
        _state.value = try {
            HomeUiState.Content(repo.dashboard())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            HomeUiState.Error("Couldn't load your summary. Pull to refresh.")
        }
    }

    fun clear() { scope.cancel() }
}
