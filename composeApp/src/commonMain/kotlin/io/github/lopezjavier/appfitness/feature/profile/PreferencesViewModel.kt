package io.github.lopezjavier.appfitness.feature.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.BiometricBounds
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.FoodPreferences
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.model.ProfileSummary
import io.github.lopezjavier.appfitness.domain.model.SavedBiometrics
import io.github.lopezjavier.appfitness.domain.model.TrainingPreferences
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.repository.BodyMeasurementRepository
import io.github.lopezjavier.appfitness.domain.repository.FoodPreferencesRepository
import io.github.lopezjavier.appfitness.domain.repository.NutritionGoalsRepository
import io.github.lopezjavier.appfitness.domain.repository.ProfileRepository
import io.github.lopezjavier.appfitness.domain.repository.TrainingPreferencesRepository
import io.github.lopezjavier.appfitness.domain.usecase.BalanceMacros
import io.github.lopezjavier.appfitness.domain.usecase.EnergyStrategyRules
import io.github.lopezjavier.appfitness.domain.usecase.HealthWriteBack
import io.github.lopezjavier.appfitness.domain.usecase.SavePreferences
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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt

private const val TENTHS_PER_KG = 10.0
private const val KEY_DRAFT = "preferences_draft"

/** What [PreferencesViewModel] reads in parallel. */
@Suppress("LongParameterList") // One result per read.
private class PreferencesData(
    val profile: Result<ProfileSummary>,
    val biometrics: Result<SavedBiometrics>,
    val latestWeight: Result<BodyMeasurement?>,
    /** The newest Health Connect weight of the latest weight's local day, if any (it wins over a manual row). */
    val healthConnectWeight: BodyMeasurement?,
    val goals: Result<NutritionGoals?>,
    val training: Result<TrainingPreferences>,
    val foodPreferences: Result<FoodPreferences>,
)

/**
 * Edits the name, the profile, the current weight, the energy strategy and the macro split. Nothing is written until
 * [PreferencesEvent.Save], which stores everything atomically (one RPC) with the target recalculated from the edited
 * weight. The weight is sent only when it changed. "Today" for the weight row is the local day in [timeZone].
 */
@Suppress("LongParameterList", "TooManyFunctions") // One dependency per port; one small handler per event.
class PreferencesViewModel(
    private val profileRepository: ProfileRepository,
    private val goalsRepository: NutritionGoalsRepository,
    private val bodyMeasurementRepository: BodyMeasurementRepository,
    private val trainingPreferencesRepository: TrainingPreferencesRepository,
    private val foodPreferencesRepository: FoodPreferencesRepository,
    private val savePreferences: SavePreferences,
    connectivity: ConnectivityObserver,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val healthWriteBack: HealthWriteBack? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow<PreferencesUiState>(PreferencesUiState.Loading)
    val uiState: StateFlow<PreferencesUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<PreferencesEffect>(Channel.BUFFERED)
    val effects: Flow<PreferencesEffect> = effectChannel.receiveAsFlow()

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

    @Suppress("CyclomaticComplexMethod") // A flat dispatch over the events.
    fun onEvent(event: PreferencesEvent) {
        when (event) {
            is PreferencesEvent.SetName ->
                edit { it.copy(name = event.name.take(BiometricBounds.NAME_MAX_LENGTH)) }

            is PreferencesEvent.SelectSex -> edit { it.copy(sex = event.sex) }

            is PreferencesEvent.SetBirthDate ->
                editLoaded { state, form ->
                    form.copy(birthDate = BiometricBounds.clampBirthDate(event.date, state.today))
                }

            is PreferencesEvent.SetHeight -> edit { it.copy(heightCm = BiometricBounds.clampHeight(event.heightCm)) }

            is PreferencesEvent.SetWeight ->
                editLoaded { state, form ->
                    if (state.weightFromHealthConnect) {
                        form
                    } else {
                        form.copy(weightKg = BiometricBounds.clampWeightTenths(event.tenths) / TENTHS_PER_KG)
                    }
                }

            is PreferencesEvent.SelectActivity -> edit { it.copy(activity = event.level) }

            is PreferencesEvent.Energy -> editLoaded { state, form -> applyEnergy(state, form, event.event) }

            is PreferencesEvent.MoveMacro ->
                edit { it.copy(split = BalanceMacros(it.split, event.macro, event.percent)) }

            is PreferencesEvent.SetWarmUpIncluded -> edit { it.copy(warmUpIncluded = event.included) }

            is PreferencesEvent.SetPreviousValuesMode -> edit { it.copy(previousValuesMode = event.mode) }

            is PreferencesEvent.SetBarWeight -> edit { it.copy(calculators = it.calculators.withBar(event.kg)) }

            is PreferencesEvent.SetDumbbellHandle ->
                edit { it.copy(calculators = it.calculators.withDumbbellHandle(event.kg)) }

            is PreferencesEvent.SetPlateEnabled -> setPlate(event.kg, event.enabled)

            is PreferencesEvent.SetFatPctGriddle -> edit { it.withFood { copy(griddleText = event.text) } }

            is PreferencesEvent.SetFatPctAirFryer -> edit { it.withFood { copy(airFryerText = event.text) } }

            is PreferencesEvent.SetFatPctFried -> edit { it.withFood { copy(friedText = event.text) } }

            PreferencesEvent.Save -> save()

            PreferencesEvent.Retry -> if (_uiState.value == PreferencesUiState.LoadFailed) load()

            PreferencesEvent.Refresh -> refreshWeight()
        }
    }

    private fun PreferencesForm.withFood(change: FoodPreferencesForm.() -> FoodPreferencesForm) =
        copy(foodPreferences = foodPreferences.change())

    private fun setPlate(kg: Double, enabled: Boolean) {
        updateLoaded { state ->
            if (state.isSaving) return@updateLoaded state
            val calculators = state.form.calculators.withPlate(kg, enabled)
            if (calculators == null) {
                state.copy(platesError = true)
            } else {
                state.copy(form = state.form.copy(calculators = calculators), platesError = false)
            }
        }
    }

    private fun applyEnergy(
        state: PreferencesUiState.Loaded,
        form: PreferencesForm,
        event: EnergyStrategyEvent,
    ): PreferencesForm {
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

    private fun edit(transform: (PreferencesForm) -> PreferencesForm) = editLoaded { _, form -> transform(form) }

    /** Applies an edit unless a save is running (values are frozen while saving). */
    private fun editLoaded(transform: (PreferencesUiState.Loaded, PreferencesForm) -> PreferencesForm) {
        updateLoaded { if (it.isSaving) it else it.copy(form = transform(it, it.form)) }
    }

    private fun updateLoaded(transform: (PreferencesUiState.Loaded) -> PreferencesUiState.Loaded) {
        _uiState.update { if (it is PreferencesUiState.Loaded) transform(it).also(::persistDraft) else it }
    }

    private fun load() {
        work?.cancel()
        _uiState.value = PreferencesUiState.Loading
        work = viewModelScope.launch {
            val data = coroutineScope {
                val profile = async { profileRepository.getOwnProfile() }
                val biometrics = async { profileRepository.getBiometrics() }
                val weight = async { bodyMeasurementRepository.getLatestWeight() }
                val goals = async { goalsRepository.getOwn() }
                val training = async { trainingPreferencesRepository.get() }
                val food = async { foodPreferencesRepository.get() }
                val latest = weight.await()
                PreferencesData(
                    profile.await(),
                    biometrics.await(),
                    latest,
                    healthConnectWeightOf(latest),
                    goals.await(),
                    training.await(),
                    food.await(),
                )
            }
            _uiState.value = loadedOrNull(data) ?: PreferencesUiState.LoadFailed
        }
    }

    @Suppress("ReturnCount", "CyclomaticComplexMethod") // Guard clauses for the reads that must all have succeeded.
    private fun loadedOrNull(data: PreferencesData): PreferencesUiState.Loaded? {
        val profile = data.profile.getOrNull()
        val biometrics = data.biometrics.getOrNull()?.toBiometricsOrNull()
        val latest = data.latestWeight.getOrNull()
        val hcWeight = data.healthConnectWeight
        val weight = (hcWeight ?: latest)?.weightKg
        val fromHealthConnect = hcWeight != null
        if (profile == null || biometrics == null || weight == null) return null
        if (data.goals.isFailure) return null
        val training = data.training.getOrNull() ?: return null
        val food = data.foodPreferences.getOrNull() ?: return null
        val goals = data.goals.getOrNull()
        val today = clock.today()
        val strategy = goals?.strategy ?: GoalStrategy.Maintenance
        val form = PreferencesForm(
            name = profile.displayName.orEmpty().take(BiometricBounds.NAME_MAX_LENGTH),
            sex = biometrics.sex,
            birthDate = BiometricBounds.clampBirthDate(biometrics.birthDate, today),
            heightCm = BiometricBounds.clampHeight(biometrics.heightCm),
            weightKg = BiometricBounds.clampWeightTenths((weight * TENTHS_PER_KG).roundToInt()) / TENTHS_PER_KG,
            activity = biometrics.activityLevel,
            strategy = strategy,
            adjustment = goals?.let { EnergyStrategyRules.clampAdjustment(strategy, it.calorieAdjustment) } ?: 0,
            split = goals?.split ?: MacroSplit.DEFAULT,
            warmUpIncluded = training.warmUpIncluded,
            previousValuesMode = training.previousValuesMode,
            calculators = training.calculators,
            foodPreferences = FoodPreferencesForm.of(food),
        )
        val draft = savedState.get<String>(KEY_DRAFT)?.let { decodeDraft(it) }
        return PreferencesUiState.Loaded(
            // A Health Connect weight is never edited here, whatever an older draft holds.
            form = draft?.let { if (fromHealthConnect) it.copy(weightKg = form.weightKg) else it } ?: form,
            saved = form,
            today = today,
            isOnline = online,
            weightFromHealthConnect = fromHealthConnect,
        )
    }

    /**
     * The Health Connect weight that owns the latest weight's day (ADR-47): the latest row itself, or the newest
     * Health Connect row of the same local day when the latest one is a later manual row. Null when the weight is
     * manual only; a failed day lookup falls back to the latest row's own source.
     */
    private suspend fun healthConnectWeightOf(latest: Result<BodyMeasurement?>): BodyMeasurement? {
        val row = latest.getOrNull()
        if (row == null || row.source == MeasurementSource.HealthConnect) return row
        val day = row.measuredAt.toLocalDateTime(timeZone).date
        val rows = bodyMeasurementRepository.getMeasurements(
            day.atStartOfDayIn(timeZone),
            day.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone),
        ).getOrNull().orEmpty()
        return rows.firstOrNull { it.source == MeasurementSource.HealthConnect }
    }

    /** Reloads only the weight and its flag: other draft fields stay, nothing is saved. */
    private fun refreshWeight() {
        if ((_uiState.value as? PreferencesUiState.Loaded)?.isSaving != false) return
        viewModelScope.launch {
            val latest = bodyMeasurementRepository.getLatestWeight()
            if (latest.isFailure) return@launch
            val hcWeight = healthConnectWeightOf(latest)
            val row = hcWeight ?: latest.getOrNull() ?: return@launch
            val fromHealthConnect = hcWeight != null
            val weight = BiometricBounds.clampWeightTenths((row.weightKg * TENTHS_PER_KG).roundToInt()) / TENTHS_PER_KG
            updateLoaded { state ->
                val untouched = state.form.weightKg == state.saved.weightKg
                state.copy(
                    form = if (fromHealthConnect || untouched) state.form.copy(weightKg = weight) else state.form,
                    saved = state.saved.copy(weightKg = weight),
                    weightFromHealthConnect = fromHealthConnect,
                )
            }
        }
    }

    /** An unsaved form is kept in the saved state (process death); equal to the saved one, the draft is cleared. */
    private fun persistDraft(state: PreferencesUiState.Loaded) {
        savedState[KEY_DRAFT] = if (state.form == state.saved) null else state.form.encodeDraft()
    }

    private fun save() {
        val state = _uiState.value as? PreferencesUiState.Loaded ?: return
        if (!state.canSave) return
        updateLoaded { it.copy(isSaving = true) }
        work = viewModelScope.launch {
            val form = state.form.copy(name = state.form.name.trim())
            val result = savePreferences(
                displayName = form.name,
                biometrics = form.toBiometrics(),
                weightKg = form.weightKg,
                weightChanged = !state.weightFromHealthConnect && form.weightKg != state.saved.weightKg,
                now = clock.now(),
                timeZone = timeZone,
                strategy = form.strategy,
                adjustment = form.adjustment,
                split = form.split,
                warmUpIncluded = form.warmUpIncluded,
                previousValuesMode = form.previousValuesMode,
                calculators = form.calculators,
                foodPreferences = form.toFoodPreferences(),
            )
            if (result.isSuccess) {
                if (!state.weightFromHealthConnect && form.weightKg != state.saved.weightKg) writeWeightBack()
                updateLoaded { it.copy(form = form, saved = form, isSaving = false) }
                effectChannel.send(PreferencesEffect.Saved)
            } else {
                updateLoaded { it.copy(isSaving = false) }
                effectChannel.send(PreferencesEffect.SaveFailed(offline = result.appErrorOrNull() == AppError.Network))
            }
        }
    }

    /** `save_preferences` returns nothing, so today's manual entry is read back and written to Health Connect. */
    private suspend fun writeWeightBack() {
        val today = clock.now().toLocalDateTime(timeZone).date
        healthWriteBack?.onTodaysWeightSaved(
            today.atStartOfDayIn(timeZone),
            today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone),
        )
    }
}
