package io.github.lopezjavier.appfitness.feature.weight

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.SavedBiometrics
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.repository.BodyMeasurementRepository
import io.github.lopezjavier.appfitness.domain.repository.CircumferenceRepository
import io.github.lopezjavier.appfitness.domain.repository.NutritionGoalsRepository
import io.github.lopezjavier.appfitness.domain.repository.ProfileRepository
import io.github.lopezjavier.appfitness.domain.usecase.Bmi
import io.github.lopezjavier.appfitness.domain.usecase.InterpretMetric
import io.github.lopezjavier.appfitness.domain.usecase.MetricKind
import io.github.lopezjavier.appfitness.domain.usecase.WaistHipRatio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val KEY_METRIC = "goal_open_metric"
private const val NONE = ""

/** What the metric cards read once loaded. */
private data class GoalLoad(
    val biometrics: SavedBiometrics,
    val strategy: GoalStrategy,
    val latest: BodyMeasurement?,
    val waistToHip: Double?,
)

private sealed interface GoalLoadState {
    data object Loading : GoalLoadState

    data object Failed : GoalLoadState

    data class Ready(val load: GoalLoad) : GoalLoadState
}

/**
 * The metric cards of the weight screen (US-27): BMI, body-fat and waist-to-hip interpretations and the open metric
 * detail. The target weight is no longer an input (ADR-78): the plan's weight outcome is shown by the trend. Required
 * reads (biometrics, latest weight) failing gives the Error state; the strategy (Maintenance fallback) and the
 * circumferences are optional. The open metric lives in [savedState] so it survives recreation.
 */
// One dependency per port or rule.
@Suppress("LongParameterList")
class GoalViewModel(
    private val profile: ProfileRepository,
    private val measurements: BodyMeasurementRepository,
    private val goals: NutritionGoalsRepository,
    private val circumferences: CircumferenceRepository,
    private val bmi: Bmi,
    private val interpret: InterpretMetric,
    private val waistHipRatio: WaistHipRatio,
    connectivity: ConnectivityObserver,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val load = MutableStateFlow<GoalLoadState>(GoalLoadState.Loading)
    private val openMetric = MutableStateFlow(restoreMetric())
    private val online = MutableStateFlow(true)

    val uiState: StateFlow<GoalUiState> =
        combine(load, openMetric, online, ::buildState)
            .stateIn(viewModelScope, SharingStarted.Eagerly, GoalUiState.Loading)

    init {
        viewModelScope.launch { connectivity.isOnline.collect { online.value = it } }
        reload(silent = false)
    }

    fun onEvent(event: GoalEvent) {
        when (event) {
            is GoalEvent.OpenMetric -> setMetric(event.kind)
            GoalEvent.CloseMetric -> setMetric(null)
            GoalEvent.Refresh -> if (load.value is GoalLoadState.Ready) reload(silent = true)
            GoalEvent.Retry -> if (load.value == GoalLoadState.Failed) reload(silent = false)
        }
    }

    private fun reload(silent: Boolean) {
        if (!silent) load.value = GoalLoadState.Loading
        viewModelScope.launch {
            val biometrics = profile.getBiometrics()
            val latest = measurements.getLatestWeight()
            if (biometrics.isFailure || latest.isFailure) {
                if (!silent) load.value = GoalLoadState.Failed
                return@launch
            }
            load.value = GoalLoadState.Ready(
                GoalLoad(
                    biometrics = biometrics.getOrThrow(),
                    strategy = goals.getOwn().getOrNull()?.strategy ?: GoalStrategy.Maintenance,
                    latest = latest.getOrThrow(),
                    waistToHip = circumferences.getAll().getOrNull()?.let(waistHipRatio::invoke),
                ),
            )
        }
    }

    private fun setMetric(kind: MetricKind?) {
        openMetric.value = kind
        savedState[KEY_METRIC] = kind?.name ?: NONE
    }

    private fun restoreMetric(): MetricKind? =
        MetricKind.entries.firstOrNull { it.name == savedState.get<String>(KEY_METRIC) }

    private fun buildState(state: GoalLoadState, metric: MetricKind?, isOnline: Boolean): GoalUiState = when (state) {
        GoalLoadState.Loading -> GoalUiState.Loading
        GoalLoadState.Failed -> GoalUiState.Error
        is GoalLoadState.Ready -> GoalUiState.Content(contentOf(state.load, metric, isOnline))
    }

    private fun contentOf(data: GoalLoad, metric: MetricKind?, isOnline: Boolean): GoalContent {
        val current = data.latest?.weightKg
        return GoalContent(
            currentKg = current,
            strategy = data.strategy,
            bmi = current?.let { bmi.compute(it, data.biometrics.heightCm) }?.let(interpret::bmi),
            bodyFat = data.latest?.bodyFatPct?.let { interpret.bodyFat(it, data.biometrics.sex) },
            waistToHip = data.waistToHip?.let { interpret.waistToHip(it, data.biometrics.sex) },
            isOnline = isOnline,
            openMetric = metric,
        )
    }
}
