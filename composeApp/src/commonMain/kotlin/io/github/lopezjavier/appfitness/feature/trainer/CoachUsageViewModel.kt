package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.CoachUsageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * "Uso de IA" screen: this month's AI spending (read-only, always re-fetched, never persisted).
 */
class CoachUsageViewModel(private val repository: CoachUsageRepository, private val clock: Clock) : ViewModel() {
    private val _uiState = MutableStateFlow<CoachUsageUiState>(CoachUsageUiState.Loading)
    val uiState: StateFlow<CoachUsageUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: CoachUsageEvent) {
        when (event) {
            CoachUsageEvent.Retry -> load()
        }
    }

    private fun today(): LocalDate = clock.now().toLocalDateTime(TimeZone.UTC).date

    private fun load() {
        _uiState.value = CoachUsageUiState.Loading
        viewModelScope.launch { _uiState.value = fetchUsage() }
    }

    private suspend fun fetchUsage(): CoachUsageUiState {
        // The month is the UTC one, like the function that writes the row.
        val result = repository.currentMonth(today())
        val offline = result.appErrorOrNull() == AppError.Network
        return result.fold(
            onSuccess = { CoachUsageUiState.Content(it) },
            onFailure = { if (offline) CoachUsageUiState.Offline else CoachUsageUiState.Error },
        )
    }
}
