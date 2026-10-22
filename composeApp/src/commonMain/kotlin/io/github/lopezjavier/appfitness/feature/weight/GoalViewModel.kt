package io.github.lopezjavier.appfitness.feature.weight

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.SavedBiometrics
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.repository.BodyMeasurementRepository
import io.github.lopezjavier.appfitness.domain.repository.CircumferenceRepository
import io.github.lopezjavier.appfitness.domain.repository.NutritionGoalsRepository
import io.github.lopezjavier.appfitness.domain.repository.ProfileRepository
import io.github.lopezjavier.appfitness.domain.usecase.Bmi
import io.github.lopezjavier.appfitness.domain.usecase.BodyMeasurementCheck
import io.github.lopezjavier.appfitness.domain.usecase.BodyMeasurementProblem
import io.github.lopezjavier.appfitness.domain.usecase.InterpretMetric
import io.github.lopezjavier.appfitness.domain.usecase.MetricKind
import io.github.lopezjavier.appfitness.domain.usecase.TargetDistance
import io.github.lopezjavier.appfitness.domain.usecase.ValidateBodyMeasurement
import io.github.lopezjavier.appfitness.domain.usecase.WaistHipRatio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val KEY_SHEET_OPEN = "goal_target_sheet_open"
private const val KEY_SHEET_TEXT = "goal_target_sheet_text"
private const val KEY_METRIC = "goal_open_metric"
private const val NONE = ""
private const val TENTHS = 10.0
private const val IDX_DRAFT = 1
private const val IDX_METRIC = 2
private const val IDX_ONLINE = 3

/** What the goal section reads once loaded. */
private data class GoalLoad(
    val targetKg: Double?,
    val biometrics: SavedBiometrics,
    val strategy: GoalStrategy,
    val latest: BodyMeasurement?,
    val onboarding: BodyMeasurement?,
    val waistToHip: Double?,
)

private sealed interface GoalLoadState {
    data object Loading : GoalLoadState

    data object Failed : GoalLoadState

    data class Ready(val load: GoalLoad) : GoalLoadState
}

private data class TargetDraft(val text: String, val saving: Boolean = false, val error: WeightSaveError? = null)

/**
 * The goal card and metric tiles of the weight screen (US-27): target weight, distance, progress, BMI, body fat and
 * waist-to-hip interpretations, the target sheet and the open metric detail. Required reads (target, biometrics,
 * latest weight) failing gives the Error state; the strategy (Maintenance fallback), the onboarding weight and the
 * circumferences are optional. The target draft and the open metric live in [savedState] so they survive
 * recreation; closing the target sheet discards
 * the draft. Saving needs connectivity.
 */
// One dependency per port or rule; one small handler per event.
@Suppress("LongParameterList", "TooManyFunctions", "ReturnCount", "ComplexCondition")
class GoalViewModel(
    private val profile: ProfileRepository,
    private val measurements: BodyMeasurementRepository,
    private val goals: NutritionGoalsRepository,
    private val circumferences: CircumferenceRepository,
    private val bmi: Bmi,
    private val targetDistance: TargetDistance,
    private val interpret: InterpretMetric,
    private val waistHipRatio: WaistHipRatio,
    private val validate: ValidateBodyMeasurement,
    connectivity: ConnectivityObserver,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val load = MutableStateFlow<GoalLoadState>(GoalLoadState.Loading)
    private val draft = MutableStateFlow(restoreDraft())
    private val openMetric = MutableStateFlow(restoreMetric())
    private val online = MutableStateFlow(true)

    val uiState: StateFlow<GoalUiState> =
        combine(load, draft, openMetric, online) { values ->
            buildState(values)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, GoalUiState.Loading)

    init {
        viewModelScope.launch { connectivity.isOnline.collect { online.value = it } }
        reload(silent = false)
    }

    fun onEvent(event: GoalEvent) {
        when (event) {
            GoalEvent.OpenTargetSheet -> openSheet()
            is GoalEvent.EditTarget -> editDraft { it.copy(text = event.text, error = null) }
            GoalEvent.SaveTarget -> saveTarget()
            GoalEvent.Dismiss -> setDraft(null)
            is GoalEvent.OpenMetric -> setMetric(event.kind)
            GoalEvent.CloseMetric -> setMetric(null)
            GoalEvent.Refresh -> if (load.value is GoalLoadState.Ready) reload(silent = true)
            GoalEvent.Retry -> if (load.value == GoalLoadState.Failed) reload(silent = false)
        }
    }

    // Loading

    private fun reload(silent: Boolean) {
        if (!silent) load.value = GoalLoadState.Loading
        viewModelScope.launch {
            val target = profile.getTargetWeight()
            val biometrics = profile.getBiometrics()
            val latest = measurements.getLatestWeight()
            if (target.isFailure || biometrics.isFailure || latest.isFailure) {
                if (!silent) load.value = GoalLoadState.Failed
                return@launch
            }
            load.value = GoalLoadState.Ready(
                GoalLoad(
                    targetKg = target.getOrThrow(),
                    biometrics = biometrics.getOrThrow(),
                    strategy = goals.getOwn().getOrNull()?.strategy ?: GoalStrategy.Maintenance,
                    latest = latest.getOrThrow(),
                    onboarding = measurements.getOnboardingWeight().getOrNull(),
                    waistToHip = circumferences.getAll().getOrNull()?.let(waistHipRatio::invoke),
                ),
            )
        }
    }

    // Target sheet

    private fun openSheet() {
        val ready = load.value as? GoalLoadState.Ready ?: return
        setDraft(TargetDraft(ready.load.targetKg?.let(::decimalText).orEmpty()))
    }

    private fun editDraft(change: (TargetDraft) -> TargetDraft) {
        val current = draft.value ?: return
        if (current.saving) return
        setDraft(change(current))
    }

    private fun setDraft(value: TargetDraft?) {
        draft.value = value
        savedState[KEY_SHEET_OPEN] = value != null
        savedState[KEY_SHEET_TEXT] = value?.text ?: NONE
    }

    private fun restoreDraft(): TargetDraft? = if (savedState.get<Boolean>(KEY_SHEET_OPEN) ==
        true
    ) {
        TargetDraft(savedState.get<String>(KEY_SHEET_TEXT).orEmpty())
    } else {
        null
    }

    private fun saveTarget() {
        val current = draft.value ?: return
        val ready = load.value as? GoalLoadState.Ready ?: return
        val value = parseDecimal(current.text)
        if (value == null || problemOf(current.text) != null || !online.value || current.saving) return
        if (sameTenths(value, ready.load.targetKg)) return
        setDraft(current.copy(saving = true, error = null))
        viewModelScope.launch {
            val result = profile.setTargetWeight(value)
            if (result.isSuccess) {
                setDraft(null)
                reload(silent = true)
            } else {
                val error = if (result.appErrorOrNull() ==
                    AppError.Network
                ) {
                    WeightSaveError.Offline
                } else {
                    WeightSaveError.Failed
                }
                draft.value?.let { setDraft(it.copy(saving = false, error = error)) }
            }
        }
    }

    private fun problemOf(text: String): BodyMeasurementProblem? {
        if (text.isBlank()) return BodyMeasurementProblem.NotANumber
        val value = parseDecimal(text) ?: return BodyMeasurementProblem.NotANumber
        return (validate(value, null) as? BodyMeasurementCheck.Invalid)?.problem
    }

    private fun sameTenths(value: Double, saved: Double?): Boolean =
        saved != null && (value * TENTHS).roundToInt() == (saved * TENTHS).roundToInt()

    // Metric

    private fun setMetric(kind: MetricKind?) {
        openMetric.value = kind
        savedState[KEY_METRIC] = kind?.name ?: NONE
    }

    private fun restoreMetric(): MetricKind? =
        MetricKind.entries.firstOrNull { it.name == savedState.get<String>(KEY_METRIC) }

    // State

    private fun buildState(values: Array<Any?>): GoalUiState {
        val state = values[0] as GoalLoadState
        return when (state) {
            GoalLoadState.Loading -> GoalUiState.Loading

            GoalLoadState.Failed -> GoalUiState.Error

            is GoalLoadState.Ready -> GoalUiState.Content(
                contentOf(
                    state.load,
                    values[IDX_DRAFT] as TargetDraft?,
                    values[IDX_METRIC] as MetricKind?,
                    values[IDX_ONLINE] as Boolean,
                ),
            )
        }
    }

    private fun contentOf(data: GoalLoad, sheet: TargetDraft?, metric: MetricKind?, isOnline: Boolean): GoalContent {
        val current = data.latest?.weightKg
        val target = data.targetKg
        val bmiValue = current?.let { bmi.compute(it, data.biometrics.heightCm) }
        return GoalContent(
            targetKg = target,
            currentKg = current,
            strategy = data.strategy,
            distance = if (current != null && target != null) targetDistance(current, target, data.strategy) else null,
            sinceFirstKg = sinceFirstOf(data, current),
            bmi = bmiValue?.let(interpret::bmi),
            bodyFat = data.latest?.bodyFatPct?.let { interpret.bodyFat(it, data.biometrics.sex) },
            waistToHip = data.waistToHip?.let { interpret.waistToHip(it, data.biometrics.sex) },
            isOnline = isOnline,
            targetSheet = sheet?.let { sheetOf(it, data.targetKg, isOnline) },
            openMetric = metric,
        )
    }

    /** Signed change from the onboarding weight to the current one (current - first), rounded to one decimal. */
    private fun sinceFirstOf(data: GoalLoad, current: Double?): Double? {
        val start = data.onboarding?.weightKg ?: return null
        if (current == null) return null
        return ((current - start) * TENTHS).roundToInt() / TENTHS
    }

    private fun sheetOf(sheet: TargetDraft, saved: Double?, isOnline: Boolean): TargetSheetState {
        val problem = if (sheet.text.isBlank()) null else problemOf(sheet.text)
        val value = parseDecimal(sheet.text)
        return TargetSheetState(
            text = sheet.text,
            problem = problem,
            canSave = value != null && problemOf(sheet.text) == null && !sameTenths(value, saved) && isOnline &&
                !sheet.saving,
            isSaving = sheet.saving,
            saveError = sheet.error,
        )
    }
}
