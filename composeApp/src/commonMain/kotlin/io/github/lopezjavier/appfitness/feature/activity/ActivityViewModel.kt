package io.github.lopezjavier.appfitness.feature.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.HealthActivitySummary
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.repository.HealthActivityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt

/** A value together with the local day it belongs to. */
data class DayValue(val value: Int, val day: LocalDate)

sealed interface ActivityUiState {
    data object Loading : ActivityUiState

    /** Nothing stored for any of the five values. */
    data object Empty : ActivityUiState

    data object Error : ActivityUiState

    /** The load failed for lack of connection. */
    data object Offline : ActivityUiState

    /** One entry per value; null when the watch gave none in the last seven days (no card is shown). */
    data class Content(
        val sleep: DayValue?,
        val steps: DayValue?,
        val activeKcal: DayValue?,
        val totalKcal: DayValue?,
        val restingHeartRate: DayValue?,
        val today: LocalDate,
    ) : ActivityUiState
}

sealed interface ActivityEvent {
    data object Retry : ActivityEvent
}

/**
 * "Actividad" (US-61): the latest sleep, steps, active and total calories and resting heart rate stored by the sync.
 * Each daily value is the most recent of the last seven stored days that has one; sleep is dated by the local day it
 * ended on. Read only: nothing to persist.
 */
class ActivityViewModel(
    private val repository: HealthActivityRepository,
    private val clock: Clock,
    private val zone: TimeZone,
    connectivity: ConnectivityObserver,
) : ViewModel() {
    /** Drives the automatic reload of the offline state. */
    val isOnline: Flow<Boolean> = connectivity.isOnline

    private val _uiState = MutableStateFlow<ActivityUiState>(ActivityUiState.Loading)
    val uiState: StateFlow<ActivityUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: ActivityEvent) {
        when (event) {
            ActivityEvent.Retry -> {
                val state = _uiState.value
                if (state == ActivityUiState.Error || state == ActivityUiState.Offline) load()
            }
        }
    }

    private fun load() {
        _uiState.value = ActivityUiState.Loading
        viewModelScope.launch {
            val result = repository.latestSummary()
            _uiState.value = result.fold(
                onSuccess = { build(it) },
                onFailure = {
                    if (result.appErrorOrNull() == AppError.Network) ActivityUiState.Offline else ActivityUiState.Error
                },
            )
        }
    }

    private fun build(summary: HealthActivitySummary): ActivityUiState {
        val days = summary.recentDays
        val content = ActivityUiState.Content(
            sleep = summary.lastSleep?.let { DayValue(it.minutes, it.endedAt.toLocalDateTime(zone).date) },
            steps = days.firstNotNullOfOrNull { d -> d.steps?.let { DayValue(it, d.day) } },
            activeKcal = days.firstNotNullOfOrNull { d -> d.activeKcal?.let { DayValue(it.roundToInt(), d.day) } },
            totalKcal = days.firstNotNullOfOrNull { d -> d.totalKcal?.let { DayValue(it.roundToInt(), d.day) } },
            restingHeartRate = summary.restingHeartRate?.let { DayValue(it.bpm, it.day) },
            today = clock.today(),
        )
        val empty = content.sleep == null && content.steps == null && content.activeKcal == null &&
            content.totalKcal == null && content.restingHeartRate == null
        return if (empty) ActivityUiState.Empty else content
    }
}
