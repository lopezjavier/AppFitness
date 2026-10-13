package io.github.lopezjavier.appfitness.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** App-wide shell state. [isOffline] drives the "Sin conexión" banner. */
data class AppUiState(val isOffline: Boolean = false)

/**
 * Root ViewModel: turns connectivity into shell UI state. The initial value is online, so the banner
 * never flashes at cold start; the observer's first emission corrects it at once if the device starts offline.
 */
class AppViewModel(connectivity: ConnectivityObserver) : ViewModel() {
    val uiState: StateFlow<AppUiState> =
        connectivity.isOnline
            .map { AppUiState(isOffline = !it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppUiState())

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
