package io.github.lopezjavier.appfitness.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.BiometricBounds
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.model.SavedBiometrics
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.repository.AuthRepository
import io.github.lopezjavier.appfitness.domain.repository.BodyMeasurementRepository
import io.github.lopezjavier.appfitness.domain.repository.NutritionGoalsRepository
import io.github.lopezjavier.appfitness.domain.repository.ProfileRepository
import io.github.lopezjavier.appfitness.domain.usecase.BalanceMacros
import io.github.lopezjavier.appfitness.domain.usecase.EnergyStrategyRules
import io.github.lopezjavier.appfitness.domain.usecase.SaveProfileSettings
import io.github.lopezjavier.appfitness.feature.goals.EnergyStrategyEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What [ProfileViewModel] reads in parallel. */
private class ProfileData(
    val biometrics: Result<SavedBiometrics>,
    val weightKg: Result<Double?>,
    val goals: Result<NutritionGoals?>,
)

/**
 * Edits the profile, the energy strategy and the macro split. Nothing is written until [ProfileEvent.Save], which
 * stores biometrics and goals atomically (one RPC) with the recalculated daily target. Signing out needs no
 * navigation: the root reacts to the auth state.
 */
@Suppress("LongParameterList") // One dependency per port.
class ProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val goalsRepository: NutritionGoalsRepository,
    private val bodyMeasurementRepository: BodyMeasurementRepository,
    private val saveProfileSettings: SaveProfileSettings,
    private val authRepository: AuthRepository,
    connectivity: ConnectivityObserver,
    private val clock: Clock,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<ProfileEffect>(Channel.BUFFERED)
    val effects: Flow<ProfileEffect> = effectChannel.receiveAsFlow()

    private var online = true
    private var work: Job? = null

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect { isOnline ->
                online = isOnline
                updateLoaded { it.copy(isOnline = isOnline) }
            }
        }
        load()
    }

    fun onEvent(event: ProfileEvent) {
        when (event) {
            is ProfileEvent.SelectSex -> edit { it.copy(sex = event.sex) }

            is ProfileEvent.SetBirthDate ->
                editLoaded { state, form ->
                    form.copy(birthDate = BiometricBounds.clampBirthDate(event.date, state.today))
                }

            is ProfileEvent.SetHeight -> edit { it.copy(heightCm = BiometricBounds.clampHeight(event.heightCm)) }

            is ProfileEvent.SelectActivity -> edit { it.copy(activity = event.level) }

            is ProfileEvent.Energy -> editLoaded { state, form -> applyEnergy(state, form, event.event) }

            is ProfileEvent.MoveMacro ->
                edit { it.copy(split = BalanceMacros(it.split, event.macro, event.percent)) }

            ProfileEvent.Save -> save()

            ProfileEvent.Retry -> if (_uiState.value == ProfileUiState.LoadFailed) load()

            ProfileEvent.SignOutConfirmed -> viewModelScope.launch { authRepository.signOut() }
        }
    }

    private fun applyEnergy(state: ProfileUiState.Loaded, form: ProfileForm, event: EnergyStrategyEvent): ProfileForm {
        val energy = state.energy
        return when (event) {
            is EnergyStrategyEvent.SelectStrategy ->
                if (event.strategy == form.strategy) {
                    form
                } else {
                    form.copy(
                        strategy = event.strategy,
                        adjustment = EnergyStrategyRules.defaultAdjustment(event.strategy),
                    )
                }

            EnergyStrategyEvent.StepDown ->
                if (energy.canStepDown) {
                    form.copy(adjustment = EnergyStrategyRules.stepDown(form.strategy, form.adjustment))
                } else {
                    form
                }

            EnergyStrategyEvent.StepUp ->
                if (energy.canStepUp) {
                    form.copy(adjustment = EnergyStrategyRules.stepUp(form.strategy, form.adjustment))
                } else {
                    form
                }
        }
    }

    private fun edit(transform: (ProfileForm) -> ProfileForm) = editLoaded { _, form -> transform(form) }

    /** Applies an edit unless a save is running (values are frozen while saving). */
    private fun editLoaded(transform: (ProfileUiState.Loaded, ProfileForm) -> ProfileForm) {
        updateLoaded { if (it.isSaving) it else it.copy(form = transform(it, it.form)) }
    }

    private fun updateLoaded(transform: (ProfileUiState.Loaded) -> ProfileUiState.Loaded) {
        _uiState.update { if (it is ProfileUiState.Loaded) transform(it) else it }
    }

    private fun load() {
        work?.cancel()
        _uiState.value = ProfileUiState.Loading
        work = viewModelScope.launch {
            val data = coroutineScope {
                val biometrics = async { profileRepository.getBiometrics() }
                val weight = async { bodyMeasurementRepository.getLatestWeight() }
                val goals = async { goalsRepository.getOwn() }
                ProfileData(biometrics.await(), weight.await().map { it?.weightKg }, goals.await())
            }
            _uiState.value = loadedOrNull(data) ?: ProfileUiState.LoadFailed
        }
    }

    private fun loadedOrNull(data: ProfileData): ProfileUiState.Loaded? {
        val biometrics = data.biometrics.getOrNull()?.toBiometricsOrNull()
        val weight = data.weightKg.getOrNull()
        if (biometrics == null || weight == null || data.goals.isFailure) return null
        val goals = data.goals.getOrNull()
        val today = clock.today()
        val strategy = goals?.strategy ?: GoalStrategy.Maintenance
        val form = ProfileForm(
            sex = biometrics.sex,
            birthDate = BiometricBounds.clampBirthDate(biometrics.birthDate, today),
            heightCm = BiometricBounds.clampHeight(biometrics.heightCm),
            activity = biometrics.activityLevel,
            strategy = strategy,
            adjustment = goals?.let { EnergyStrategyRules.clampAdjustment(strategy, it.calorieAdjustment) } ?: 0,
            split = goals?.split ?: MacroSplit.DEFAULT,
        )
        return ProfileUiState.Loaded(form = form, saved = form, weightKg = weight, today = today, isOnline = online)
    }

    /** The biometrics when all four fields are saved; an interrupted onboarding leaves some missing. */
    private fun save() {
        val state = _uiState.value as? ProfileUiState.Loaded ?: return
        if (!state.canSave) return
        updateLoaded { it.copy(isSaving = true) }
        work = viewModelScope.launch {
            val form = state.form
            val result = saveProfileSettings(
                biometrics = form.toBiometrics(),
                weightKg = state.weightKg,
                today = state.today,
                strategy = form.strategy,
                adjustment = form.adjustment,
                split = form.split,
            )
            if (result.isSuccess) {
                updateLoaded { it.copy(saved = form, isSaving = false) }
                effectChannel.send(ProfileEffect.Saved)
            } else {
                updateLoaded { it.copy(isSaving = false) }
                effectChannel.send(ProfileEffect.SaveFailed(offline = result.appErrorOrNull() == AppError.Network))
            }
        }
    }
}
