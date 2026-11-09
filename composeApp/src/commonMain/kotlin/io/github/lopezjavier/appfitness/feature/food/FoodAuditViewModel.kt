package io.github.lopezjavier.appfitness.feature.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.FoodAuditRow
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.repository.FoodRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Food audit (US-117): lists the user's own foods whose kcal and macros do not match, in the server's order. It only
 * reads and holds no draft, so after recreation the list is simply read again. A fixed food leaves the list because
 * the editor reports back with [FoodAuditEvent.Refresh]; a load already in flight absorbs both reload events. When the
 * connection returns while the list is [FoodAuditUiState.Offline] it is reloaded.
 */
class FoodAuditViewModel(private val foodRepository: FoodRepository, connectivity: ConnectivityObserver) : ViewModel() {
    private val _uiState = MutableStateFlow<FoodAuditUiState>(FoodAuditUiState.Loading)
    val uiState: StateFlow<FoodAuditUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<FoodAuditEffect>(Channel.BUFFERED)
    val effects: Flow<FoodAuditEffect> = effectChannel.receiveAsFlow()

    private var loading = false
    private var wasOnline = true

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect { online ->
                if (online && !wasOnline && _uiState.value == FoodAuditUiState.Offline) load(silent = false)
                wasOnline = online
            }
        }
        load(silent = false)
    }

    fun onEvent(event: FoodAuditEvent) {
        when (event) {
            FoodAuditEvent.Retry -> load(silent = false)
            FoodAuditEvent.Refresh -> load(silent = true)
            is FoodAuditEvent.OpenFood -> effectChannel.trySend(FoodAuditEffect.OpenEditor(event.foodId))
        }
    }

    private fun load(silent: Boolean) {
        if (loading) return
        loading = true
        if (!silent) _uiState.value = FoodAuditUiState.Loading
        viewModelScope.launch {
            try {
                val result = foodRepository.foodEnergyAudit()
                result.getOrNull()?.let { _uiState.value = contentOrEmpty(it) }
                // A silent refresh that fails keeps whatever is shown.
                if (result.isFailure && !silent) {
                    _uiState.value = if (result.appErrorOrNull() == AppError.Network) {
                        FoodAuditUiState.Offline
                    } else {
                        FoodAuditUiState.Error
                    }
                }
            } finally {
                loading = false
            }
        }
    }

    private fun contentOrEmpty(rows: List<FoodAuditRow>): FoodAuditUiState =
        if (rows.isEmpty()) FoodAuditUiState.Empty else FoodAuditUiState.Content(rows)
}
