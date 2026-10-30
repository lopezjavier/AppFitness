package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.DevOptions
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.CoachPingRepository
import io.github.lopezjavier.appfitness.domain.repository.CoachUsageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * "Uso de IA" screen: this month's AI spending (read-only, always re-fetched, never persisted) plus the dev-only ping
 * button ([showPing], from [DevOptions]; `false` in release builds) that calls the `coach` function once.
 */
class CoachUsageViewModel(
    private val repository: CoachUsageRepository,
    private val clock: Clock,
    private val pingRepository: CoachPingRepository,
    devOptions: DevOptions,
) : ViewModel() {
    private val _uiState = MutableStateFlow<CoachUsageUiState>(CoachUsageUiState.Loading)
    val uiState: StateFlow<CoachUsageUiState> = _uiState.asStateFlow()

    private val _pingState = MutableStateFlow<CoachPingUiState>(CoachPingUiState.Idle)
    val pingState: StateFlow<CoachPingUiState> = _pingState.asStateFlow()

    /** Whether the dev ping button is shown at all. */
    val showPing: Boolean = devOptions.showCoachPing

    init {
        load()
    }

    fun onEvent(event: CoachUsageEvent) {
        when (event) {
            CoachUsageEvent.Retry -> load()
            CoachUsageEvent.Ping -> ping()
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

    private fun ping() {
        if (!showPing || _pingState.value == CoachPingUiState.Running) return
        _pingState.value = CoachPingUiState.Running
        viewModelScope.launch {
            val result = pingRepository.ping()
            _pingState.value = result.fold(
                onSuccess = { CoachPingUiState.Success },
                onFailure = { failure ->
                    when {
                        failure is CoachException -> CoachPingUiState.Failed(failure.failure)
                        result.appErrorOrNull() == AppError.Network -> CoachPingUiState.Offline
                        else -> CoachPingUiState.Failed(CoachFailure.Other)
                    }
                },
            )
            // The call costs money: refresh the figure in place (no Loading flash), refusals included.
            if (_pingState.value == CoachPingUiState.Success) _uiState.value = fetchUsage()
        }
    }
}
