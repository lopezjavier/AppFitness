package io.github.lopezjavier.appfitness.feature.weight

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.model.BodyCircumference
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.BodyMetric
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.BodyMeasurementRepository
import io.github.lopezjavier.appfitness.domain.repository.CircumferenceRepository
import io.github.lopezjavier.appfitness.domain.repository.NutritionGoalsRepository
import io.github.lopezjavier.appfitness.domain.repository.ProfileRepository
import io.github.lopezjavier.appfitness.domain.repository.WeightChartPreferences
import io.github.lopezjavier.appfitness.domain.usecase.MetricDailyValues
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
import kotlinx.coroutines.flow.update
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

    /** Everything every range and metric needs, fetched once; the trend of the selection is derived from it. */
    data class Ready(
        val rows: List<BodyMeasurement>,
        /** Null when the circumference read failed: only the circumference metrics depend on it. */
        val circumferenceRows: List<BodyCircumference>?,
        val targetKg: Double?,
        val strategy: GoalStrategy,
    ) : TrendLoad
}

private data class TrendSelection(val range: WeightChartRange, val metric: BodyMetric)

/**
 * The trend of the weight screen (US-27): loads once everything any range or metric needs (the measurements of the
 * widest range plus the MA7 lead-in, all circumferences, the target and the goal strategy, Maintenance when there is
 * no goals row or it cannot be read) and derives the trend of the selected range and metric from memory, so switching
 * tabs makes no call, shows no loading state and works offline. The selected point date is kept in [savedState] as an
 * ISO string. The range and the metric live in [preferences]; selecting one saves it and updates the in-memory
 * selection at once. Refresh and Retry reload the data. The weekly rate and the target are always the weight's.
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
    private val selection = MutableStateFlow<TrendSelection?>(null)
    private var loadJob: Job? = null

    val uiState: StateFlow<WeightTrendUiState> = combine(load, selection, selectedDate, maHelpOpen, ::buildState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, WeightTrendUiState.Loading)

    init {
        viewModelScope.launch {
            combine(preferences.range, preferences.metric, ::TrendSelection).distinctUntilChanged().collect {
                selection.value = it
            }
        }
        reload(silent = false)
    }

    fun onEvent(event: WeightTrendEvent) {
        when (event) {
            is WeightTrendEvent.SelectRange -> {
                selection.update { it?.copy(range = event.range) }
                viewModelScope.launch { preferences.setRange(event.range) }
            }

            is WeightTrendEvent.SelectMetric -> {
                // The open day may have no value in the new metric.
                selectPoint(null)
                selection.update { it?.copy(metric = event.metric) }
                viewModelScope.launch { preferences.setMetric(event.metric) }
            }

            is WeightTrendEvent.SelectPoint -> selectPoint(event.date)

            WeightTrendEvent.Refresh -> if (load.value is TrendLoad.Ready) reload(silent = true)

            WeightTrendEvent.ToggleMaHelp -> {
                maHelpOpen.value = !maHelpOpen.value
                savedState[KEY_MA_HELP] = maHelpOpen.value
            }

            WeightTrendEvent.Retry -> if (uiState.value == WeightTrendUiState.Error) reload(silent = false)
        }
    }

    private fun selectPoint(date: LocalDate?) {
        selectedDate.value = date
        savedState[KEY_SELECTED] = date?.toString() ?: NONE
    }

    private fun reload(silent: Boolean) {
        loadJob?.cancel()
        if (!silent) load.value = TrendLoad.Loading
        loadJob = viewModelScope.launch {
            val today = clock.today()
            val window = WeightChartRange.entries.maxBy { it.days }.window(today)
            val rows = measurements.getMeasurements(
                window.fetchStart.atStartOfDayIn(timeZone),
                window.endExclusive.atStartOfDayIn(timeZone),
            )
            val circumferenceRows = circumferences.getAll()
            val targetDeferred = async { profile.getTargetWeight().getOrNull() }
            val strategy = goals.getOwn().getOrNull()?.strategy ?: GoalStrategy.Maintenance
            val target = targetDeferred.await()
            when {
                rows.isSuccess -> load.value = TrendLoad.Ready(
                    rows.getOrThrow(),
                    circumferenceRows.getOrNull(),
                    target,
                    strategy,
                )

                !silent -> load.value = TrendLoad.Failed
            }
        }
    }

    private fun buildState(
        load: TrendLoad,
        selection: TrendSelection?,
        selected: LocalDate?,
        maHelp: Boolean,
    ): WeightTrendUiState = when {
        load is TrendLoad.Failed -> WeightTrendUiState.Error

        load !is TrendLoad.Ready || selection == null -> WeightTrendUiState.Loading

        else -> {
            val (range, metric) = selection
            // Today is read on every derivation so a state kept past midnight windows on the right day.
            val today = clock.today()
            val weight = weightTrend(load.rows, range, load.strategy, timeZone, today)
            val circumferenceRows = load.circumferenceRows
            val shown = when {
                metric == BodyMetric.Weight -> weight

                circumferenceRows == null && metric.isCircumference -> return WeightTrendUiState.Error

                else -> {
                    val daily = metricDailyValues(load.rows, circumferenceRows.orEmpty(), metric, timeZone)
                    weightTrend(daily, range, load.strategy, today)
                }
            }
            WeightTrendUiState.Content(
                range = range,
                metric = metric,
                weeklyRate = weight.rate,
                trend = shown,
                selectedPoint = shown.points.firstOrNull { it.date == selected },
                targetKg = load.targetKg.takeIf { metric == BodyMetric.Weight },
                maHelpOpen = maHelp,
            )
        }
    }
}
