package io.github.lopezjavier.appfitness.feature.weight

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.model.BodyMetric
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.BodyMeasurementRepository
import io.github.lopezjavier.appfitness.domain.repository.CircumferenceRepository
import io.github.lopezjavier.appfitness.domain.repository.NutritionGoalsRepository
import io.github.lopezjavier.appfitness.domain.repository.ProfileRepository
import io.github.lopezjavier.appfitness.domain.repository.WeightChartPreferences
import io.github.lopezjavier.appfitness.domain.usecase.MetricDailyValues
import io.github.lopezjavier.appfitness.domain.usecase.TrendData
import io.github.lopezjavier.appfitness.domain.usecase.WeeklyRate
import io.github.lopezjavier.appfitness.domain.usecase.WeightChartRange
import io.github.lopezjavier.appfitness.domain.usecase.WeightTrend
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

private const val KEY_SELECTED = "weight_trend_selected"
private const val KEY_MA_HELP = "weight_trend_ma_help_open"
private const val NONE = ""

private sealed interface TrendLoad {
    data object Loading : TrendLoad

    data object Failed : TrendLoad

    data class Ready(
        val range: WeightChartRange,
        val metric: BodyMetric,
        val trend: TrendData,
        val weeklyRate: WeeklyRate,
        val targetKg: Double?,
    ) : TrendLoad
}

/**
 * The trend of the weight screen (US-27): loads the measurements of the saved range (plus the lead-in days the MA7
 * and the weekly rate need), builds the [TrendData] of the saved metric against the user's goal strategy
 * (Maintenance when there is no goals row or it cannot be read) and keeps the selected point date in [savedState]
 * as an ISO string. The range and the metric live in [preferences]; selecting one saves it and the saved value
 * drives the reload. The weekly rate and the target are always the weight's.
 */
// One dependency per port.
@Suppress("LongParameterList")
class WeightTrendViewModel(
    private val measurements: BodyMeasurementRepository,
    private val circumferences: CircumferenceRepository,
    private val goals: NutritionGoalsRepository,
    private val profile: ProfileRepository,
    private val preferences: WeightChartPreferences,
    private val weightTrend: WeightTrend,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val metricDailyValues: MetricDailyValues = MetricDailyValues(),
) : ViewModel() {
    private val load = MutableStateFlow<TrendLoad>(TrendLoad.Loading)
    private val selectedDate = MutableStateFlow(
        savedState.get<String>(KEY_SELECTED)?.takeIf { it != NONE }?.let(LocalDate::parse),
    )
    private val maHelpOpen = MutableStateFlow(savedState.get<Boolean>(KEY_MA_HELP) == true)
    private var currentRange: WeightChartRange = WeightChartRange.Default
    private var currentMetric: BodyMetric = BodyMetric.Weight
    private var loadJob: Job? = null

    val uiState: StateFlow<WeightTrendUiState> = combine(load, selectedDate, maHelpOpen, ::buildState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, WeightTrendUiState.Loading)

    init {
        viewModelScope.launch {
            combine(preferences.range, preferences.metric, ::Pair).distinctUntilChanged().collect { (range, metric) ->
                currentRange = range
                currentMetric = metric
                reload(silent = false)
            }
        }
    }

    fun onEvent(event: WeightTrendEvent) {
        when (event) {
            is WeightTrendEvent.SelectRange -> viewModelScope.launch { preferences.setRange(event.range) }

            is WeightTrendEvent.SelectMetric -> {
                // The open day may have no value in the new metric.
                selectPoint(null)
                viewModelScope.launch { preferences.setMetric(event.metric) }
            }

            is WeightTrendEvent.SelectPoint -> selectPoint(event.date)

            WeightTrendEvent.Refresh -> if (load.value is TrendLoad.Ready) reload(silent = true)

            WeightTrendEvent.ToggleMaHelp -> {
                maHelpOpen.value = !maHelpOpen.value
                savedState[KEY_MA_HELP] = maHelpOpen.value
            }

            WeightTrendEvent.Retry -> if (load.value == TrendLoad.Failed) reload(silent = false)
        }
    }

    private fun selectPoint(date: LocalDate?) {
        selectedDate.value = date
        savedState[KEY_SELECTED] = date?.toString() ?: NONE
    }

    private fun reload(silent: Boolean) {
        loadJob?.cancel()
        val range = currentRange
        val metric = currentMetric
        if (!silent) load.value = TrendLoad.Loading
        loadJob = viewModelScope.launch {
            val today = clock.today()
            val window = range.window(today)
            val rows = measurements.getMeasurements(
                window.fetchStart.atStartOfDayIn(timeZone),
                window.endExclusive.atStartOfDayIn(timeZone),
            )
            val circumferenceRows = if (metric.isCircumference) circumferences.getAll() else Result.success(emptyList())
            val targetDeferred = async { profile.getTargetWeight().getOrNull() }
            val strategy = goals.getOwn().getOrNull()?.strategy ?: GoalStrategy.Maintenance
            val target = targetDeferred.await()
            when {
                rows.isSuccess && circumferenceRows.isSuccess -> {
                    val weight = weightTrend(rows.getOrThrow(), range, strategy, timeZone, today)
                    val shown = if (metric == BodyMetric.Weight) {
                        weight
                    } else {
                        val daily =
                            metricDailyValues(rows.getOrThrow(), circumferenceRows.getOrThrow(), metric, timeZone)
                        weightTrend(daily, range, strategy, today)
                    }
                    load.value = TrendLoad.Ready(
                        range,
                        metric,
                        shown,
                        weight.rate,
                        target.takeIf { metric == BodyMetric.Weight },
                    )
                }

                !silent -> load.value = TrendLoad.Failed
            }
        }
    }

    private fun buildState(load: TrendLoad, selected: LocalDate?, maHelp: Boolean): WeightTrendUiState = when (load) {
        TrendLoad.Loading -> WeightTrendUiState.Loading

        TrendLoad.Failed -> WeightTrendUiState.Error

        is TrendLoad.Ready -> WeightTrendUiState.Content(
            range = load.range,
            metric = load.metric,
            weeklyRate = load.weeklyRate,
            trend = load.trend,
            selectedPoint = load.trend.points.firstOrNull { it.date == selected },
            targetKg = load.targetKg,
            maHelpOpen = maHelp,
        )
    }
}
