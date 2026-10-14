package io.github.lopezjavier.appfitness.feature.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.BiometricBounds
import io.github.lopezjavier.appfitness.domain.model.Biometrics
import io.github.lopezjavier.appfitness.domain.model.SavedBiometrics
import io.github.lopezjavier.appfitness.domain.model.Sex
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.repository.BodyMeasurementRepository
import io.github.lopezjavier.appfitness.domain.repository.ProfileRepository
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
private const val TENTHS_PER_KG = 10.0

/**
 * Drives the five biometric steps. Every user value and the step live in [savedState], so they survive
 * rotation and process death; a new instance built from a handle that holds them skips the load.
 * Saving writes the profile biometrics and the onboarding weight row, both idempotent, then moves to the goals step.
 */
@Suppress("TooManyFunctions") // One small private function per event and step of the flow.
class OnboardingViewModel(
    private val savedState: SavedStateHandle,
    private val profileRepository: ProfileRepository,
    private val bodyMeasurementRepository: BodyMeasurementRepository,
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
            is OnboardingEvent.SelectSex -> edit { it.copy(sex = event.sex) }

            is OnboardingEvent.SetBirthDate -> edit {
                it.copy(birthDate = BiometricBounds.clampBirthDate(event.date, it.today))
            }

            is OnboardingEvent.SetHeight -> edit { it.copy(heightCm = BiometricBounds.clampHeight(event.heightCm)) }

            is OnboardingEvent.SetWeight ->
                edit { it.copy(weightTenths = BiometricBounds.clampWeightTenths(event.tenths)) }

            is OnboardingEvent.SelectActivity -> edit { it.copy(activity = event.level) }

            OnboardingEvent.Continue -> onContinue()

            OnboardingEvent.Back -> onBack()

            OnboardingEvent.RetryLoad -> if (_uiState.value.phase == OnboardingPhase.LoadError) load()

            OnboardingEvent.RetrySave -> if (_uiState.value.step == OnboardingStep.Activity) save()
        }
    }

    private fun onContinue() {
        val state = _uiState.value
        if (!state.canContinue) return
        if (state.step == OnboardingStep.Activity) save() else goTo(state.step.next())
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
                profile.await() to weight.await()
            }
            val saved = loaded.first.getOrNull()
            val weightRow = loaded.second.getOrNull()
            if (loaded.first.isFailure || loaded.second.isFailure || saved == null) {
                _uiState.update { it.copy(phase = OnboardingPhase.LoadError) }
            } else {
                val tenths = weightRow?.let { (it.weightKg * TENTHS_PER_KG).roundToInt() }
                change { prefilled(it, saved, tenths).copy(phase = OnboardingPhase.Content) }
            }
        }
    }

    private fun prefilled(state: OnboardingUiState, saved: SavedBiometrics, weightTenths: Int?): OnboardingUiState =
        state.copy(
            sex = saved.sex,
            birthDate = saved.birthDate?.let { BiometricBounds.clampBirthDate(it, state.today) } ?: state.birthDate,
            heightCm = saved.heightCm?.let(BiometricBounds::clampHeight) ?: state.heightCm,
            weightTenths = weightTenths?.let(BiometricBounds::clampWeightTenths) ?: state.weightTenths,
            activity = saved.activityLevel,
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
                val isNetwork = result.appErrorOrNull() == AppError.Network
                val error = if (isNetwork) OnboardingSaveError.Network else OnboardingSaveError.Other
                _uiState.update { it.copy(phase = OnboardingPhase.Content, saveError = error) }
            }
        }
    }

    private fun restoreOrDefault(): OnboardingUiState {
        val today = clock.today()
        val base = OnboardingUiState(birthDate = BiometricBounds.defaultBirthDate(today), today = today)
        if (savedState.get<Boolean>(KEY_READY) != true) return base
        return base.copy(
            step = savedState.get<String>(KEY_STEP)?.let(OnboardingStep::valueOf) ?: base.step,
            sex = savedState.get<String>(KEY_SEX)?.let(Sex::valueOf),
            birthDate = savedState.get<String>(KEY_BIRTH_DATE)?.let(LocalDate::parse) ?: base.birthDate,
            heightCm = savedState.get<Int>(KEY_HEIGHT) ?: base.heightCm,
            weightTenths = savedState.get<Int>(KEY_WEIGHT) ?: base.weightTenths,
            activity = savedState.get<String>(KEY_ACTIVITY)?.let(ActivityLevel::valueOf),
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
    }
}
