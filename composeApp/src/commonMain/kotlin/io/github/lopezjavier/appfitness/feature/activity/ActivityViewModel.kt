package io.github.lopezjavier.appfitness.feature.activity

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.HealthActivityDay
import io.github.lopezjavier.appfitness.domain.model.HealthActivityHistory
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.repository.HealthActivityRepository
import io.github.lopezjavier.appfitness.domain.usecase.ActivityDayValues
import io.github.lopezjavier.appfitness.domain.usecase.ActivityMetric
import io.github.lopezjavier.appfitness.domain.usecase.ActivitySeries
import io.github.lopezjavier.appfitness.domain.usecase.ActivitySeriesPoint
import io.github.lopezjavier.appfitness.domain.usecase.SleepStageSummary
import io.github.lopezjavier.appfitness.domain.usecase.WeightChartRange
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private const val KEY_METRIC = "activity_metric"
private const val KEY_RANGE = "activity_range"
private const val KEY_DAY = "activity_day"
private const val TODAY = ""

/** The chart of the selected metric over the selected range; [points] has one entry per day with data. */
data class ActivityChartState(
    val metric: ActivityMetric,
    val range: WeightChartRange,
    val points: List<ActivitySeriesPoint>,
)

sealed interface ActivityUiState {
    data object Loading : ActivityUiState

    /** Nothing stored for any day of the loaded window. */
    data object Empty : ActivityUiState

    data object Error : ActivityUiState

    /** The load failed for lack of connection. */
    data object Offline : ActivityUiState

    /**
     * The selected [day] with its five values ([summary], each null when missing), the chart, the sleep stages of that
     * day (null without stages), the days with data for the calendar and the inline day states: [dayLoading] while a
     * day outside the loaded window is fetched, [dayError] when that fetch failed (retry with [ActivityEvent.Retry]).
     */
    data class Content(
        val day: LocalDate,
        val isToday: Boolean,
        val summary: HealthActivityDay,
        val chart: ActivityChartState,
        val sleepStages: SleepStageSummary?,
        val markedDays: Set<LocalDate>,
        val today: LocalDate,
        val dayLoading: Boolean = false,
        val dayError: Boolean = false,
    ) : ActivityUiState
}

sealed interface ActivityEvent {
    /** Reloads after an error, or fetches again the day that failed. */
    data object Retry : ActivityEvent

    /** Silent reload (after a sync or on resume): keeps the selection and never shows Loading. */
    data object Refresh : ActivityEvent

    data class SelectMetric(val metric: ActivityMetric) : ActivityEvent

    data class SelectRange(val range: WeightChartRange) : ActivityEvent

    /** Future days are ignored. */
    data class SelectDay(val day: LocalDate) : ActivityEvent

    data object GoToToday : ActivityEvent
}

private class Loaded(val history: HealthActivityHistory, val today: LocalDate)

/**
 * "Actividad" (US-64): loads the stored watch data of the last 365 days once and derives the summary of the selected
 * day, the chart of the selected metric and range and the sleep stages from memory, so switching metric or range makes
 * no call and works offline. A day outside the loaded window is fetched alone; if that fails only an inline error is
 * shown. Metric, range and day live in [savedState] (ADR-40). Read only: nothing to persist.
 */
class ActivityViewModel(
    private val repository: HealthActivityRepository,
    private val clock: Clock,
    private val zone: TimeZone,
    connectivity: ConnectivityObserver,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    /** Drives the automatic reload of the offline state. */
    val isOnline: Flow<Boolean> = connectivity.isOnline

    private val _uiState = MutableStateFlow<ActivityUiState>(ActivityUiState.Loading)
    val uiState: StateFlow<ActivityUiState> = _uiState.asStateFlow()

    private var metric = savedState.get<String>(KEY_METRIC)?.let { name ->
        ActivityMetric.entries.firstOrNull { it.name == name }
    } ?: ActivityMetric.Default
    private var range = savedState.get<String>(KEY_RANGE)?.let { name ->
        WeightChartRange.entries.firstOrNull { it.name == name }
    } ?: DEFAULT_RANGE
    private var selectedDay: LocalDate? = savedState.get<String>(KEY_DAY)?.takeIf { it != TODAY }?.let(LocalDate::parse)

    private var loaded: Loaded? = null
    private var fetchedDay: HealthActivityDay? = null
    private var dayFetching = false
    private var dayFailed = false
    private var loadJob: Job? = null
    private var dayJob: Job? = null

    init {
        reload(silent = false)
    }

    fun onEvent(event: ActivityEvent) {
        when (event) {
            is ActivityEvent.SelectMetric -> {
                metric = event.metric
                savedState[KEY_METRIC] = metric.name
                publish()
            }

            is ActivityEvent.SelectRange -> {
                range = event.range
                savedState[KEY_RANGE] = range.name
                publish()
            }

            is ActivityEvent.SelectDay -> loaded?.let { if (event.day <= it.today) selectDay(event.day) }

            ActivityEvent.GoToToday -> if (loaded != null) selectDay(null)

            ActivityEvent.Refresh -> if (loaded != null) reload(silent = true)

            ActivityEvent.Retry -> when {
                loaded == null -> reload(silent = false)
                dayFailed -> resolveDay()
            }
        }
    }

    private fun selectDay(day: LocalDate?) {
        selectedDay = day?.takeIf { it != loaded?.today }
        savedState[KEY_DAY] = selectedDay?.toString() ?: TODAY
        fetchedDay = null
        dayFailed = false
        resolveDay()
    }

    private fun reload(silent: Boolean) {
        loadJob?.cancel()
        if (!silent) {
            loaded = null
            _uiState.value = ActivityUiState.Loading
        }
        loadJob = viewModelScope.launch {
            val today = clock.today()
            val window = WeightChartRange.entries.maxBy { it.days }.window(today)
            val result = repository.history(window.visibleStart, window.endExclusive)
            result.fold(
                onSuccess = {
                    loaded = Loaded(it, today)
                    resolveDay()
                },
                onFailure = {
                    if (!silent) {
                        _uiState.value =
                            if (result.appErrorOrNull() ==
                                AppError.Network
                            ) {
                                ActivityUiState.Offline
                            } else {
                                ActivityUiState.Error
                            }
                    }
                },
            )
        }
    }

    /** Fetches the selected day when it is outside the loaded window; otherwise just publishes. */
    private fun resolveDay() {
        dayJob?.cancel()
        val day = selectedDay
        if (day == null || isInWindow(day)) {
            dayFetching = false
            dayFailed = false
            publish()
            return
        }
        dayFetching = fetchedDay?.day != day
        dayFailed = false
        publish()
        dayJob = viewModelScope.launch {
            repository.day(day).fold(
                onSuccess = { fetchedDay = it },
                onFailure = { dayFailed = true },
            )
            dayFetching = false
            publish()
        }
    }

    private fun isInWindow(day: LocalDate): Boolean {
        val window = WeightChartRange.entries.maxBy { it.days }.window(loaded?.today ?: return false)
        return day >= window.visibleStart && day < window.endExclusive
    }

    private fun publish() {
        val data = loaded ?: return
        val history = data.history
        val marked = markedDays(history)
        if (marked.isEmpty()) {
            _uiState.value = ActivityUiState.Empty
            return
        }
        val day = selectedDay ?: data.today
        val summary = if (isInWindow(day)) {
            ActivityDayValues.of(history, day, zone)
        } else {
            fetchedDay?.takeIf { it.day == day } ?: HealthActivityDay(day, null, null, null, null, null)
        }
        val window = range.window(data.today)
        _uiState.value = ActivityUiState.Content(
            day = day,
            isToday = day == data.today,
            summary = summary,
            chart = ActivityChartState(metric, range, ActivitySeries.of(history, metric, window, zone)),
            sleepStages = summary.sleep?.let(SleepStageSummary::of),
            markedDays = marked,
            today = data.today,
            dayLoading = dayFetching,
            dayError = dayFailed,
        )
    }

    private fun markedDays(history: HealthActivityHistory): Set<LocalDate> = buildSet {
        history.daily.filter { it.steps != null || it.activeKcal != null || it.totalKcal != null }
            .mapTo(this) { it.day }
        history.sleep.mapTo(this) { it.endedAt.toLocalDateTime(zone).date }
        history.restingHeartRate.mapTo(this) { it.day }
    }

    private companion object {
        val DEFAULT_RANGE = WeightChartRange.Month
    }
}
