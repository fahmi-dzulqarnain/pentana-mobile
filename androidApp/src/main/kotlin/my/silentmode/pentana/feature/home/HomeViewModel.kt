package my.silentmode.pentana.feature.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import my.silentmode.pentana.shared.DashboardRepository
import my.silentmode.pentana.shared.model.DashboardDto

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Error(val message: String) : HomeUiState
    data class Content(val data: DashboardDto) : HomeUiState
}

class HomeViewModel(private val repo: DashboardRepository) : ViewModel() {
    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state = _state.asStateFlow()

    var refreshing by mutableStateOf(false)
        private set

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = HomeUiState.Loading
            fetch()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            refreshing = true
            fetch()
            refreshing = false
        }
    }

    private suspend fun fetch() {
        _state.value = try {
            HomeUiState.Content(repo.dashboard())
        } catch (_: Exception) {
            HomeUiState.Error("Couldn't load your summary. Pull to refresh.")
        }
    }
}
