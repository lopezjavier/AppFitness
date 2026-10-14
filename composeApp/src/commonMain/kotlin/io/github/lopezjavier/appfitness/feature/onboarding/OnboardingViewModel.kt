package io.github.lopezjavier.appfitness.feature.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.BiometricBounds
import io.github.lopezjavier.appfitness.domain.model.Biometrics
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.model.SavedBiometrics
import io.github.lopezjavier.appfitness.domain.model.Sex
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.repository.BodyMeasurementRepository
import io.github.lopezjavier.appfitness.domain.repository.NutritionGoalsRepository
import io.github.lopezjavier.appfitness.domain.repository.ProfileRepository
import io.github.lopezjavier.appfitness.domain.usecase.CompleteOnboarding
import io.github.lopezjavier.appfitness.domain.usecase.EnergyStrategyRules
import io.github.lopezjavier.appfitness.feature.goals.EnergyStrategyEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt

private const val KEY_READY = "onboarding_ready"
private const val KEY_STEP = "onboarding_step"
private const val KEY_SEX = "onboarding_sex"
private const val KEY_BIRTH_DATE = "onboarding_birth_date"
private const val KEY_HEIGHT = "onboarding_height_cm"
private const val KEY_WEIGHT = "onboarding_weight_tenths"
private const val KEY_ACTIVITY = "onboarding_activity"
private const val KEY_STRATEGY = "onboarding_strategy"
private const val KEY_ADJUSTMENT = "onboarding_adjustment"
private const val TENTHS_PER_KG = 10.0

/** What [OnboardingViewModel.load] reads in parallel. */
private class Loaded(
    val biometrics: Result<SavedBiometrics>,
    val weightTenths: Result<Int?>,
    val goals: Result<NutritionGoals?>,
)

/**
 * Drives the three biometric steps and the goals step. Every user value and the step live in [savedState], so they
 * survive rotation and process death; a new instance built from a handle that holds them skips the load.
 * Saving the biometrics writes the profile and the onboarding weight row, both idempotent, then moves to the goals
 * step; saving the goals upserts them and sets `onboarding_completed`, then the phase becomes Completed.
 */
@Suppress("TooManyFunctions", "LongParameterList") // One small private function per event; one dependency per port.
class OnboardingViewModel(
    private val savedState: SavedStateHandle,
    private val profileRepository: ProfileRepository,
    private val bodyMeasurementRepository: BodyMeasurementRepository,
    private val goalsRepository: NutritionGoalsRepository,
    private val completeOnboarding: CompleteOnboarding,
    connectivity: ConnectivityObserver,
    private val clock: Clock,
) : ViewModel() {
    private val _uiState = MutableStateFlow(restoreOrDefault())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private var work: Job? = null

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect { online -> _uiState.update { it.copy(isOffline = !online) } }
        }
        if (_uiState.value.phase == OnboardingPhase.Loading) load()
    }

    fun onEvent(event: OnboardingEvent) {
        when (event) {
            is OnboardingEvent.SelectSex,
            is OnboardingEvent.SetBirthDate,
            is OnboardingEvent.SetHeight,
            is OnboardingEvent.SetWeight,
            is OnboardingEvent.SelectActivity,
            is OnboardingEvent.Energy,
            -> edit { applyValueEvent(it, event) }

            OnboardingEvent.SaveGoals -> saveGoals()

            OnboardingEvent.Continue -> onContinue()

            OnboardingEvent.Back -> onBack()

            OnboardingEvent.RetryLoad -> if (_uiState.value.phase == OnboardingPhase.LoadError) load()
        }
    }

    /** The new state after an event that edits a user value; other events leave it as it is. */
    private fun applyValueEvent(state: OnboardingUiState, event: OnboardingEvent): OnboardingUiState = when (event) {
        is OnboardingEvent.SelectSex -> state.copy(sex = event.sex)

        is OnboardingEvent.SetBirthDate ->
            state.copy(birthDate = BiometricBounds.clampBirthDate(event.date, state.today))

        is OnboardingEvent.SetHeight -> state.copy(heightCm = BiometricBounds.clampHeight(event.heightCm))

        is OnboardingEvent.SetWeight -> state.copy(weightTenths = BiometricBounds.clampWeightTenths(event.tenths))

        is OnboardingEvent.SelectActivity -> state.copy(activity = event.level)

        is OnboardingEvent.Energy -> applyEnergy(state, event.event)

        else -> state
    }

    private fun applyEnergy(state: OnboardingUiState, event: EnergyStrategyEvent): OnboardingUiState {
        val energy = state.energy ?: return state
        return when (event) {
            is EnergyStrategyEvent.SelectStrategy ->
                if (event.strategy == state.strategy) {
                    state
                } else {
                    state.copy(
                        strategy = event.strategy,
                        adjustment = EnergyStrategyRules.defaultAdjustment(event.strategy),
                    )
                }

            EnergyStrategyEvent.StepDown ->
                if (energy.canStepDown) {
                    state.copy(adjustment = EnergyStrategyRules.stepDown(state.strategy, state.adjustment))
                } else {
                    state
                }

            EnergyStrategyEvent.StepUp ->
                if (energy.canStepUp) {
                    state.copy(adjustment = EnergyStrategyRules.stepUp(state.strategy, state.adjustment))
                } else {
                    state
                }
        }
    }

    private fun onContinue() {
        val state = _uiState.value
        if (!state.canContinue) return
        when (state.step) {
            OnboardingStep.Activity -> save()
            OnboardingStep.Goals -> Unit
            else -> goTo(state.step.next())
        }
    }

    private fun onBack() {
        val state = _uiState.value
        if (state.canGoBack) goTo(state.step.previous())
    }

    private fun goTo(step: OnboardingStep?) {
        if (step != null) change { it.copy(step = step, saveError = null) }
    }

    /** Applies an edit of a user value unless a save is running (values are frozen while saving). */
    private fun edit(transform: (OnboardingUiState) -> OnboardingUiState) {
        if (_uiState.value.phase == OnboardingPhase.Content) change(transform)
    }

    /** Applies an edit of a user value or the step, then keeps the saved state in sync. */
    private fun change(transform: (OnboardingUiState) -> OnboardingUiState) {
        _uiState.update(transform)
        persist(_uiState.value)
    }

    private fun load() {
        work?.cancel()
        _uiState.update { it.copy(phase = OnboardingPhase.Loading) }
        work = viewModelScope.launch {
            val loaded = coroutineScope {
                val profile = async { profileRepository.getBiometrics() }
                val weight = async { bodyMeasurementRepository.getOnboardingWeight() }
                val goals = async { goalsRepository.getOwn() }
                Loaded(
                    biometrics = profile.await(),
                    weightTenths = weight.await().map { row ->
                        row?.let { (it.weightKg * TENTHS_PER_KG).roundToInt() }
                    },
                    goals = goals.await(),
                )
            }
            val saved = loaded.biometrics.getOrNull()
            if (saved == null || loaded.weightTenths.isFailure || loaded.goals.isFailure) {
                _uiState.update { it.copy(phase = OnboardingPhase.LoadError) }
            } else {
                change {
                    prefilled(it, saved, loaded.weightTenths.getOrNull(), loaded.goals.getOrNull())
                        .copy(phase = OnboardingPhase.Content)
                }
            }
        }
    }

    private fun prefilled(
        state: OnboardingUiState,
        saved: SavedBiometrics,
        weightTenths: Int?,
        goals: NutritionGoals?,
    ): OnboardingUiState = state.copy(
        sex = saved.sex,
        birthDate = saved.birthDate?.let { BiometricBounds.clampBirthDate(it, state.today) } ?: state.birthDate,
        heightCm = saved.heightCm?.let(BiometricBounds::clampHeight) ?: state.heightCm,
        weightTenths = weightTenths?.let(BiometricBounds::clampWeightTenths) ?: state.weightTenths,
        activity = saved.activityLevel,
        strategy = goals?.strategy ?: state.strategy,
        adjustment = goals?.let { EnergyStrategyRules.clampAdjustment(it.strategy, it.calorieAdjustment) }
            ?: state.adjustment,
    )

    private fun save() {
        val state = _uiState.value
        val sex = state.sex
        val activity = state.activity
        if (state.phase != OnboardingPhase.Content || sex == null || activity == null) return
        _uiState.update { it.copy(phase = OnboardingPhase.Saving, saveError = null) }
        work = viewModelScope.launch {
            val biometrics = Biometrics(sex, state.birthDate, state.heightCm, activity)
            val profileSaved = profileRepository.updateBiometrics(biometrics)
            val result =
                if (profileSaved.isFailure) {
                    profileSaved
                } else {
                    bodyMeasurementRepository.saveOnboardingWeight(state.weightTenths / TENTHS_PER_KG, clock.now())
                }
            if (result.isSuccess) {
                change { it.copy(step = OnboardingStep.Goals, phase = OnboardingPhase.Content) }
            } else {
                _uiState.update { it.copy(phase = OnboardingPhase.Content, saveError = saveErrorOf(result)) }
            }
        }
    }

    private fun saveGoals() {
        val state = _uiState.value
        val energy = state.energy
        if (!state.canContinue || state.step != OnboardingStep.Goals || energy == null) return
        _uiState.update { it.copy(phase = OnboardingPhase.Saving, saveError = null) }
        work = viewModelScope.launch {
            val goals = NutritionGoals(state.strategy, state.adjustment, energy.targetKcal, MacroSplit.DEFAULT)
            val result = completeOnboarding(goals)
            if (result.isSuccess) {
                change { it.copy(phase = OnboardingPhase.Completed) }
            } else {
                _uiState.update { it.copy(phase = OnboardingPhase.Content, saveError = saveErrorOf(result)) }
            }
        }
    }

    private fun saveErrorOf(result: Result<Unit>): OnboardingSaveError =
        if (result.appErrorOrNull() == AppError.Network) OnboardingSaveError.Network else OnboardingSaveError.Other

    private fun restoreOrDefault(): OnboardingUiState {
        val today = clock.today()
        val base = OnboardingUiState(birthDate = BiometricBounds.defaultBirthDate(today), today = today)
        if (savedState.get<Boolean>(KEY_READY) != true) return base
        return base.copy(
            step = OnboardingStep.fromSavedName(savedState.get<String>(KEY_STEP)),
            sex = savedState.get<String>(KEY_SEX)?.let(Sex::valueOf),
            birthDate = savedState.get<String>(KEY_BIRTH_DATE)?.let(LocalDate::parse) ?: base.birthDate,
            heightCm = savedState.get<Int>(KEY_HEIGHT) ?: base.heightCm,
            weightTenths = savedState.get<Int>(KEY_WEIGHT) ?: base.weightTenths,
            activity = savedState.get<String>(KEY_ACTIVITY)?.let(ActivityLevel::valueOf),
            strategy = savedState.get<String>(KEY_STRATEGY)?.let(GoalStrategy::valueOf) ?: base.strategy,
            adjustment = savedState.get<Int>(KEY_ADJUSTMENT) ?: base.adjustment,
            phase = OnboardingPhase.Content,
        )
    }

    private fun persist(state: OnboardingUiState) {
        if (state.phase == OnboardingPhase.Loading || state.phase == OnboardingPhase.LoadError) return
        savedState[KEY_READY] = true
        savedState[KEY_STEP] = state.step.name
        savedState[KEY_SEX] = state.sex?.name
        savedState[KEY_BIRTH_DATE] = state.birthDate.toString()
        savedState[KEY_HEIGHT] = state.heightCm
        savedState[KEY_WEIGHT] = state.weightTenths
        savedState[KEY_ACTIVITY] = state.activity?.name
        savedState[KEY_STRATEGY] = state.strategy.name
        savedState[KEY_ADJUSTMENT] = state.adjustment
    }
}
