package io.github.lopezjavier.appfitness.feature.profile

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeBodyMeasurementRepository
import io.github.lopezjavier.appfitness.app.FakeClock
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeHealthDataSource
import io.github.lopezjavier.appfitness.app.FakeNutritionGoalsRepository
import io.github.lopezjavier.appfitness.app.FakeProfileRepository
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.app.ReminderCoordinator
import io.github.lopezjavier.appfitness.app.ReminderEnv
import io.github.lopezjavier.appfitness.app.SavedPreferences
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.BodyCircumference
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.CalculatorSettings
import io.github.lopezjavier.appfitness.domain.model.CircumferenceSite
import io.github.lopezjavier.appfitness.domain.model.FoodPreferences
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.HealthAccess
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.Macronutrient
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.model.PreviousValuesMode
import io.github.lopezjavier.appfitness.domain.model.ProfileSummary
import io.github.lopezjavier.appfitness.domain.model.SavedBiometrics
import io.github.lopezjavier.appfitness.domain.model.Sex
import io.github.lopezjavier.appfitness.domain.model.TrainingPreferences
import io.github.lopezjavier.appfitness.domain.usecase.CalculateTdee
import io.github.lopezjavier.appfitness.domain.usecase.ComputeReminders
import io.github.lopezjavier.appfitness.domain.usecase.HealthWriteBack
import io.github.lopezjavier.appfitness.domain.usecase.LoadReminders
import io.github.lopezjavier.appfitness.domain.usecase.SavePreferences
import io.github.lopezjavier.appfitness.feature.food.FakeFoodPreferencesRepository
import io.github.lopezjavier.appfitness.feature.goals.EnergyStrategyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** US-06 / US-07 / US-08 on the Preferencias screen. */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions", "LargeClass") // One method per Gherkin scenario and rule, one shared setup.
class PreferencesViewModelTest {
    private val today = LocalDate(2026, 10, 14)
    private val now = Instant.parse("2026-10-14T12:00:00Z")
    private val birthDate = LocalDate(2000, 3, 1)
    private val profile = FakeProfileRepository(Result.success(ProfileSummary("Ana", null, true)))
    private val weights = FakeBodyMeasurementRepository(
        BodyMeasurement("w1", Instant.parse("2026-10-10T08:00:00Z"), 80.0, MeasurementSource.Manual),
    )
    private val goals =
        FakeNutritionGoalsRepository(NutritionGoals(GoalStrategy.Deficit, -400, 2000, MacroSplit.DEFAULT))
    private val connectivity = FakeConnectivityObserver(initial = true)
    private val training = FakeTrainingPreferencesRepository()
    private val foodPreferences = FakeFoodPreferencesRepository()
    private val health = FakeHealthDataSource()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        profile.biometrics = Result.success(
            SavedBiometrics(Sex.Male, birthDate, 178, ActivityLevel.Moderate),
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        savedState: SavedStateHandle = SavedStateHandle(),
        section: PreferencesSection = PreferencesSection.PersonalData,
        reminders: ReminderCoordinator? = null,
    ) = PreferencesViewModel(
        profileRepository = profile,
        goalsRepository = goals,
        bodyMeasurementRepository = weights,
        trainingPreferencesRepository = training,
        foodPreferencesRepository = foodPreferences,
        savePreferences = SavePreferences(profile),
        connectivity = connectivity,
        clock = FakeClock(today, now),
        timeZone = TimeZone.UTC,
        savedState = savedState.also { it[KEY_PREFERENCES_SECTION] = section.key },
        healthWriteBack = HealthWriteBack(health, weights, FakeClock(today, now)),
        reminders = reminders,
    )

    private fun TestScope.loaded(
        section: PreferencesSection = PreferencesSection.PersonalData,
        reminders: ReminderCoordinator? = null,
    ): Pair<PreferencesViewModel, () -> PreferencesUiState.Loaded> {
        val viewModel = viewModel(section = section, reminders = reminders)
        advanceUntilIdle()
        return viewModel to { assertIs<PreferencesUiState.Loaded>(viewModel.uiState.value) }
    }

    private fun tdee(height: Int, weightKg: Double = 80.0) =
        CalculateTdee(Sex.Male, birthDate, height, weightKg, ActivityLevel.Moderate, today)

    // -- initial display ----------------------------------------------------------------------------------------

    @Test
    fun initialDisplay_loadsTheSavedValuesAndTheMacroTexts() = runTest {
        val (_, state) = loaded()

        val s = state()
        assertEquals("Ana", s.form.name)
        assertEquals(178, s.form.heightCm)
        assertEquals(80.0, s.form.weightKg)
        assertEquals(MacroSplit.DEFAULT, s.form.split)
        assertEquals(tdee(178), s.energy.tdeeKcal)
        assertEquals(tdee(178) - 400, s.energy.targetKcal)
        assertFalse(s.canSave)
    }

    @Test
    fun missingGoalsRow_usesDefaults() = runTest {
        goals.row = null

        val (_, state) = loaded()

        assertEquals(GoalStrategy.Maintenance, state().form.strategy)
        assertEquals(0, state().form.adjustment)
        assertEquals(MacroSplit.DEFAULT, state().form.split)
    }

    @Test
    fun readFailures_giveLoadFailed_andRetryReloads() = runTest {
        goals.readResult = Result.failure(AppException(AppError.Network))
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(PreferencesUiState.LoadFailed, viewModel.uiState.value)

        goals.readResult = null
        viewModel.onEvent(PreferencesEvent.Retry)
        advanceUntilIdle()

        assertIs<PreferencesUiState.Loaded>(viewModel.uiState.value)
    }

    @Test
    fun missingWeightOrIncompleteBiometrics_isLoadFailed() = runTest {
        weights.row = null
        val noWeight = viewModel()
        advanceUntilIdle()
        assertEquals(PreferencesUiState.LoadFailed, noWeight.uiState.value)

        weights.row = BodyMeasurement("w1", Instant.parse("2026-10-10T08:00:00Z"), 80.0, MeasurementSource.Manual)
        profile.biometrics = Result.success(SavedBiometrics(sex = Sex.Male))
        val partial = viewModel()
        advanceUntilIdle()
        assertEquals(PreferencesUiState.LoadFailed, partial.uiState.value)
    }

    @Test
    fun profileReadFailure_isLoadFailed() = runTest {
        val failing = FakeProfileRepository(Result.failure(AppException(AppError.Network)))
        failing.biometrics = profile.biometrics
        val viewModel = PreferencesViewModel(
            failing,
            goals,
            weights,
            training,
            foodPreferences,
            SavePreferences(failing),
            connectivity,
            FakeClock(today, now),
            TimeZone.UTC,
        )
        advanceUntilIdle()

        assertEquals(PreferencesUiState.LoadFailed, viewModel.uiState.value)
    }

    // -- macro split --------------------------------------------------------------------------------------------

    @Test
    fun movingASlider_autoBalancesAndNeverLeavesTheRange() = runTest {
        val (viewModel, state) = loaded(PreferencesSection.Macros)

        viewModel.onEvent(PreferencesEvent.MoveMacro(Macronutrient.Protein, 35))
        advanceUntilIdle()
        assertEquals(MacroSplit(35, 42, 23), state().form.split)

        viewModel.onEvent(PreferencesEvent.MoveMacro(Macronutrient.Protein, 99))
        advanceUntilIdle()
        val split = state().form.split
        assertEquals(90, split.proteinPct)
        assertEquals(100, split.proteinPct + split.carbsPct + split.fatPct)
        assertTrue(listOf(split.proteinPct, split.carbsPct, split.fatPct).all { it in 5..90 })
    }

    // -- saving -------------------------------------------------------------------------------------------------

    @Test
    fun editingTheHeight_recalculatesTdeeAndTarget_andSaveWritesBoth() = runTest {
        val (viewModel, state) = loaded()

        viewModel.onEvent(PreferencesEvent.SetHeight(180))
        advanceUntilIdle()
        assertEquals(tdee(180), state().energy.tdeeKcal)
        assertTrue(state().canSave)
        assertTrue(profile.preferenceSaves.isEmpty(), "nothing is written before Guardar")

        viewModel.onEvent(PreferencesEvent.Save)
        advanceUntilIdle()

        val saved = profile.preferenceSaves.single()
        assertEquals(180, saved.biometrics.heightCm)
        assertEquals(tdee(180) - 400, saved.goals.targetKcal)
        assertFalse(state().isDirty)
        assertFalse(state().canSave)
    }

    @Test
    fun aChangedWeight_isWrittenToHealthConnectKeepingTheFat() = runTest {
        val (viewModel, _) = loaded()
        // What save_preferences stores for today.
        weights.row = BodyMeasurement("w2", now, 81.5, MeasurementSource.Manual, bodyFatPct = 15.1)

        viewModel.onEvent(PreferencesEvent.SetWeight(815))
        viewModel.onEvent(PreferencesEvent.Save)
        advanceUntilIdle()

        val write = health.writes.single()
        assertEquals("w2", write.rowId)
        assertEquals(81.5, write.weightKg)
        assertEquals(15.1, write.bodyFatPct)
    }

    @Test
    fun aChangedWeightWithoutWritePermission_isNotWritten() = runTest {
        health.access = HealthAccess(emptySet(), canWrite = false)
        val (viewModel, _) = loaded()
        weights.row = BodyMeasurement("w2", now, 81.5, MeasurementSource.Manual)

        viewModel.onEvent(PreferencesEvent.SetWeight(815))
        viewModel.onEvent(PreferencesEvent.Save)
        advanceUntilIdle()

        assertTrue(health.writes.isEmpty())
        assertEquals(1, profile.preferenceSaves.size)
    }

    @Test
    fun savingWithoutChangingTheWeight_writesNothing() = runTest {
        val (viewModel, _) = loaded()

        viewModel.onEvent(PreferencesEvent.SetHeight(185))
        viewModel.onEvent(PreferencesEvent.Save)
        advanceUntilIdle()

        assertTrue(health.writes.isEmpty())
    }

    @Test
    fun energyGoal_saveSendsTheStrategyAndTheRecalculatedTarget_butNoOptionalParameters() = runTest {
        val (viewModel, _) = loaded(PreferencesSection.EnergyGoal)

        viewModel.onEvent(PreferencesEvent.Energy(EnergyStrategyEvent.SelectStrategy(GoalStrategy.Surplus)))
        viewModel.onEvent(PreferencesEvent.Save)
        advanceUntilIdle()

        val saved = profile.preferenceSaves.single()
        assertEquals(GoalStrategy.Surplus, saved.goals.strategy)
        assertEquals(250, saved.goals.calorieAdjustment)
        assertEquals(tdee(178) + 250, saved.goals.targetKcal)
        assertEquals(MacroSplit.DEFAULT, saved.goals.split)
        assertNull(saved.weight)
        assertOptionalParametersUntouched(saved)
    }

    @Test
    fun macros_saveSendsTheSplitWithTheSavedStrategy_butNoOptionalParameters() = runTest {
        val (viewModel, state) = loaded(PreferencesSection.Macros)

        viewModel.onEvent(PreferencesEvent.MoveMacro(Macronutrient.Protein, 35))
        assertTrue(state().canSave)
        viewModel.onEvent(PreferencesEvent.Save)
        advanceUntilIdle()

        val saved = profile.preferenceSaves.single()
        assertEquals(MacroSplit(35, 42, 23), saved.goals.split)
        assertEquals(GoalStrategy.Deficit, saved.goals.strategy)
        assertEquals(-400, saved.goals.calorieAdjustment)
        assertEquals(tdee(178) - 400, saved.goals.targetKcal)
        assertOptionalParametersUntouched(saved)
    }

    @Test
    fun saveSuccess_setsTheSavedFlag_andNoEffect() = runTest {
        val (viewModel, state) = loaded()
        assertFalse(state().isSaved)
        viewModel.effects.test {
            viewModel.onEvent(PreferencesEvent.SelectActivity(ActivityLevel.High))
            viewModel.onEvent(PreferencesEvent.Save)
            advanceUntilIdle()
            assertTrue(state().isSaved)
            expectNoEvents()
        }
    }

    @Test
    fun saveFailure_doesNotSetTheSavedFlag() = runTest {
        profile.settingsResult = Result.failure(AppException(AppError.Unknown))
        val (viewModel, state) = loaded()

        viewModel.onEvent(PreferencesEvent.SetHeight(185))
        viewModel.onEvent(PreferencesEvent.Save)
        advanceUntilIdle()

        assertFalse(state().isSaved)
    }

    @Test
    fun saveFailure_keepsTheEdits_andEmitsTheError() = runTest {
        profile.settingsResult = Result.failure(AppException(AppError.Unknown))
        val (viewModel, state) = loaded()
        viewModel.effects.test {
            viewModel.onEvent(PreferencesEvent.SetHeight(185))
            viewModel.onEvent(PreferencesEvent.Save)
            advanceUntilIdle()
            assertEquals(PreferencesEffect.SaveFailed(offline = false), awaitItem())
        }
        assertEquals(185, state().form.heightCm)
        assertFalse(state().isSaving)
        assertTrue(state().canSave)
    }

    @Test
    fun networkFailure_isReportedAsOffline() = runTest {
        profile.settingsResult = Result.failure(AppException(AppError.Network))
        val (viewModel, _) = loaded()
        viewModel.effects.test {
            viewModel.onEvent(PreferencesEvent.SetHeight(185))
            viewModel.onEvent(PreferencesEvent.Save)
            advanceUntilIdle()
            assertEquals(PreferencesEffect.SaveFailed(offline = true), awaitItem())
        }
    }

    @Test
    fun offline_disablesSave() = runTest {
        val (viewModel, state) = loaded()
        viewModel.onEvent(PreferencesEvent.SetHeight(185))
        connectivity.state.value = false
        advanceUntilIdle()

        assertFalse(state().canSave)
        viewModel.onEvent(PreferencesEvent.Save)
        advanceUntilIdle()
        assertTrue(profile.preferenceSaves.isEmpty())

        connectivity.state.value = true
        advanceUntilIdle()
        assertTrue(state().canSave)
    }

    // -- biometrics edits (T-040 regression) --------------------------------------------------------------------

    @Test
    fun sexChange_isPartOfTheForm() = runTest {
        val (viewModel, state) = loaded()

        viewModel.onEvent(PreferencesEvent.SelectSex(Sex.Female))
        advanceUntilIdle()

        assertEquals(Sex.Female, state().form.sex)
        assertTrue(state().isDirty)
    }

    @Test
    fun editingHeightAndBirthDate_updatesTheFormAndMakesItSavable() = runTest {
        val (viewModel, state) = loaded()
        val newBirth = LocalDate(1995, 5, 5)

        viewModel.onEvent(PreferencesEvent.SetHeight(172))
        viewModel.onEvent(PreferencesEvent.SetBirthDate(newBirth))
        advanceUntilIdle()

        assertEquals(172, state().form.heightCm)
        assertEquals(newBirth, state().form.birthDate)
        assertTrue(state().isDirty)
        assertTrue(state().canSave)
    }

    @Test
    fun editingTheBirthDate_recalculatesTheEnergyFromTheNewAge() = runTest {
        val (viewModel, state) = loaded()
        val before = state().energy.tdeeKcal

        viewModel.onEvent(PreferencesEvent.SetBirthDate(LocalDate(1990, 5, 5)))
        advanceUntilIdle()

        val expected = CalculateTdee(Sex.Male, LocalDate(1990, 5, 5), 178, 80.0, ActivityLevel.Moderate, today)
        assertEquals(expected, state().energy.tdeeKcal)
        assertTrue(before != state().energy.tdeeKcal)
    }

    @Test
    fun heightAndBirthDate_areClampedToTheAllowedRanges() = runTest {
        val (viewModel, state) = loaded()

        viewModel.onEvent(PreferencesEvent.SetHeight(50))
        assertEquals(100, state().form.heightCm)
        viewModel.onEvent(PreferencesEvent.SetHeight(300))
        assertEquals(250, state().form.heightCm)

        viewModel.onEvent(PreferencesEvent.SetBirthDate(LocalDate(2026, 1, 1)))
        assertEquals(LocalDate(2012, 10, 14), state().form.birthDate)
        viewModel.onEvent(PreferencesEvent.SetBirthDate(LocalDate(1800, 1, 1)))
        assertEquals(LocalDate(1925, 10, 15), state().form.birthDate)
    }

    @Test
    fun activityChange_isPartOfTheForm() = runTest {
        val (viewModel, state) = loaded()

        viewModel.onEvent(PreferencesEvent.SelectActivity(ActivityLevel.Light))

        assertEquals(ActivityLevel.Light, state().form.activity)
        assertTrue(state().isDirty)
    }

    @Test
    fun saving_keepsTheEditedValuesInTheForm_andWritesThem() = runTest {
        val (viewModel, state) = loaded()
        val newBirth = LocalDate(1995, 5, 5)

        viewModel.onEvent(PreferencesEvent.SetHeight(172))
        viewModel.onEvent(PreferencesEvent.SetBirthDate(newBirth))
        viewModel.onEvent(PreferencesEvent.Save)
        advanceUntilIdle()

        assertEquals(172, state().form.heightCm)
        assertEquals(newBirth, state().form.birthDate)
        assertEquals(state().form, state().saved)
        val biometrics = profile.preferenceSaves.single().biometrics
        assertEquals(172, biometrics.heightCm)
        assertEquals(newBirth, biometrics.birthDate)
    }

    @Test
    fun editsAreIgnoredWhileSaving() = runTest {
        val (viewModel, state) = loaded()
        viewModel.onEvent(PreferencesEvent.SetHeight(172))
        viewModel.onEvent(PreferencesEvent.Save)

        viewModel.onEvent(PreferencesEvent.SetHeight(190))
        advanceUntilIdle()

        assertEquals(172, state().form.heightCm)
    }

    // -- US-06 editing the weight -------------------------------------------------------------------------------

    @Test
    fun editingTheWeight_updatesTheFormAndRecalculatesTheTarget() = runTest {
        val (viewModel, state) = loaded()

        viewModel.onEvent(PreferencesEvent.SetWeight(850))
        advanceUntilIdle()

        assertEquals(85.0, state().form.weightKg)
        assertEquals(tdee(178, 85.0), state().energy.tdeeKcal)
        assertEquals(tdee(178, 85.0) - 400, state().energy.targetKcal)
        assertTrue(state().canSave)
    }

    @Test
    fun weight_isClampedToTheTableRange() = runTest {
        val (viewModel, state) = loaded()

        viewModel.onEvent(PreferencesEvent.SetWeight(10))
        assertEquals(20.0, state().form.weightKg)
        viewModel.onEvent(PreferencesEvent.SetWeight(9000))
        assertEquals(400.0, state().form.weightKg)
    }

    @Test
    fun savingAChangedWeight_sendsTodaysEntryWithTheLocalDayBounds() = runTest {
        val (viewModel, _) = loaded()

        viewModel.onEvent(PreferencesEvent.SetWeight(850))
        viewModel.onEvent(PreferencesEvent.Save)
        advanceUntilIdle()

        val weight = profile.preferenceSaves.single().weight
        assertEquals(85.0, weight?.weightKg)
        assertEquals(now, weight?.measuredAt)
        assertEquals(Instant.parse("2026-10-14T00:00:00Z"), weight?.dayStart)
        assertEquals(Instant.parse("2026-10-15T00:00:00Z"), weight?.dayEnd)
    }

    @Test
    fun savingAnUnchangedWeight_doesNotSendOne() = runTest {
        val (viewModel, _) = loaded()

        viewModel.onEvent(PreferencesEvent.SetHeight(172))
        viewModel.onEvent(PreferencesEvent.Save)
        advanceUntilIdle()

        assertNull(profile.preferenceSaves.single().weight)
    }

    @Test
    fun targetOfTheSave_usesTheEditedWeight() = runTest {
        val (viewModel, _) = loaded()

        viewModel.onEvent(PreferencesEvent.SetWeight(850))
        viewModel.onEvent(PreferencesEvent.Save)
        advanceUntilIdle()

        assertEquals(tdee(178, 85.0) - 400, profile.preferenceSaves.single().goals.targetKcal)
    }

    @Test
    fun afterSavingTheWeight_aSecondSaveOfOtherFieldsDoesNotResendIt() = runTest {
        val (viewModel, _) = loaded()
        viewModel.onEvent(PreferencesEvent.SetWeight(850))
        viewModel.onEvent(PreferencesEvent.Save)
        advanceUntilIdle()

        viewModel.onEvent(PreferencesEvent.SetHeight(172))
        viewModel.onEvent(PreferencesEvent.Save)
        advanceUntilIdle()

        assertNull(profile.preferenceSaves.last().weight)
    }

    @Test
    fun aManualLatestWeight_isEditableAndNotFlaggedAsHealthConnect() = runTest {
        val (viewModel, state) = loaded()
        assertFalse(state().weightFromHealthConnect)

        viewModel.onEvent(PreferencesEvent.SetWeight(790))
        viewModel.onEvent(PreferencesEvent.Save)
        advanceUntilIdle()

        assertEquals(79.0, profile.preferenceSaves.single().weight?.weightKg)
    }

    @Test
    fun aHealthConnectLatestWeight_isShownReadOnly() = runTest {
        weights.row =
            BodyMeasurement("w2", Instant.parse("2026-10-13T08:00:00Z"), 79.4, MeasurementSource.HealthConnect)
        val (viewModel, state) = loaded()
        assertEquals(79.4, state().form.weightKg)
        assertTrue(state().weightFromHealthConnect)
        assertFalse(state().canSave)

        viewModel.onEvent(PreferencesEvent.SetWeight(790))
        viewModel.onEvent(PreferencesEvent.SetHeight(172))
        viewModel.onEvent(PreferencesEvent.Save)
        advanceUntilIdle()

        assertEquals(79.4, state().form.weightKg)
        assertNull(profile.preferenceSaves.single().weight)
        assertTrue(health.writes.isEmpty())
    }

    @Test
    fun aRestoredDraftCannotChangeAHealthConnectWeight() = runTest {
        weights.row =
            BodyMeasurement("w2", Instant.parse("2026-10-13T08:00:00Z"), 79.4, MeasurementSource.HealthConnect)
        val saved = SavedStateHandle()
        val first = viewModel(saved)
        advanceUntilIdle()
        first.onEvent(PreferencesEvent.SetHeight(172))

        val recreated = viewModel(saved)
        advanceUntilIdle()

        val state = assertIs<PreferencesUiState.Loaded>(recreated.uiState.value)
        assertTrue(state.weightFromHealthConnect)
        assertEquals(172, state.form.heightCm)
        assertEquals(79.4, state.form.weightKg)
    }

    @Test
    fun aLaterManualRowOnADayWithAHealthConnectWeight_isShownReadOnlyWithTheHealthConnectValue() = runTest {
        val hc = BodyMeasurement("hc", Instant.parse("2026-10-13T07:00:00Z"), 79.4, MeasurementSource.HealthConnect)
        val manual = BodyMeasurement("m", Instant.parse("2026-10-13T20:00:00Z"), 81.0, MeasurementSource.Manual)
        weights.row = manual
        weights.rows = listOf(hc, manual)

        val (viewModel, state) = loaded()

        assertTrue(state().weightFromHealthConnect)
        assertEquals(79.4, state().form.weightKg)
        viewModel.onEvent(PreferencesEvent.SetWeight(700))
        assertEquals(79.4, state().form.weightKg)
    }

    @Test
    fun aManualRowOnADayWithoutHealthConnectWeight_staysEditable() = runTest {
        val manual = BodyMeasurement("m", Instant.parse("2026-10-13T20:00:00Z"), 81.0, MeasurementSource.Manual)
        val otherDayHc =
            BodyMeasurement("hc", Instant.parse("2026-10-12T07:00:00Z"), 79.4, MeasurementSource.HealthConnect)
        weights.row = manual
        weights.rows = listOf(otherDayHc, manual)

        val (_, state) = loaded()

        assertFalse(state().weightFromHealthConnect)
        assertEquals(81.0, state().form.weightKg)
    }

    @Test
    fun refresh_afterAHealthConnectWeightArrives_locksTheWeightAndKeepsTheOtherDraftFields() = runTest {
        val (viewModel, state) = loaded()
        viewModel.onEvent(PreferencesEvent.SetHeight(172))
        viewModel.onEvent(PreferencesEvent.SetWeight(750))
        assertFalse(state().weightFromHealthConnect)
        val hc = BodyMeasurement("hc", Instant.parse("2026-10-14T07:00:00Z"), 79.4, MeasurementSource.HealthConnect)
        weights.row = hc
        weights.rows = listOf(hc)

        viewModel.onEvent(PreferencesEvent.Refresh)
        advanceUntilIdle()

        assertTrue(state().weightFromHealthConnect)
        assertEquals(79.4, state().form.weightKg)
        assertEquals(79.4, state().saved.weightKg)
        assertEquals(172, state().form.heightCm)
        assertTrue(profile.preferenceSaves.isEmpty())
    }

    @Test
    fun refresh_withNothingNew_keepsTheWeightDraft() = runTest {
        val (viewModel, state) = loaded()
        viewModel.onEvent(PreferencesEvent.SetWeight(750))

        viewModel.onEvent(PreferencesEvent.Refresh)
        advanceUntilIdle()

        assertFalse(state().weightFromHealthConnect)
        assertEquals(75.0, state().form.weightKg)
    }

    @Test
    fun savingWhileHealthConnectOwnsTheWeight_neverSendsAWeightEvenIfTheFlagArrivedAfterTheEdit() = runTest {
        val (viewModel, _) = loaded()
        viewModel.onEvent(PreferencesEvent.SetWeight(750))
        val hc = BodyMeasurement("hc", Instant.parse("2026-10-14T07:00:00Z"), 79.4, MeasurementSource.HealthConnect)
        weights.row = hc
        weights.rows = listOf(hc)
        viewModel.onEvent(PreferencesEvent.Refresh)
        advanceUntilIdle()
        viewModel.onEvent(PreferencesEvent.SetHeight(172))

        viewModel.onEvent(PreferencesEvent.Save)
        advanceUntilIdle()

        assertNull(profile.preferenceSaves.single().weight)
    }

    @Test
    fun aRecreatedViewModelWithAHealthConnectWeight_keepsTheFlagAndDiscardsAStaleDraftWeight() = runTest {
        val saved = SavedStateHandle()
        val first = viewModel(saved)
        advanceUntilIdle()
        first.onEvent(PreferencesEvent.SetWeight(750))
        val hc = BodyMeasurement("hc", Instant.parse("2026-10-14T07:00:00Z"), 79.4, MeasurementSource.HealthConnect)
        weights.row = hc
        weights.rows = listOf(hc)

        val recreated = viewModel(saved)
        advanceUntilIdle()

        val state = assertIs<PreferencesUiState.Loaded>(recreated.uiState.value)
        assertTrue(state.weightFromHealthConnect)
        assertEquals(79.4, state.form.weightKg)
    }

    @Test
    fun revertingTheWeight_makesTheFormCleanAgain() = runTest {
        val (viewModel, state) = loaded()

        viewModel.onEvent(PreferencesEvent.SetWeight(850))
        assertTrue(state().isDirty)
        viewModel.onEvent(PreferencesEvent.SetWeight(800))

        assertFalse(state().isDirty)
        assertFalse(state().canSave)
    }

    // -- name -----------------------------------------------------------------------------------------------------

    @Test
    fun editingTheName_makesTheFormSavable_andSaveWritesTheTrimmedName() = runTest {
        val (viewModel, state) = loaded()

        viewModel.onEvent(PreferencesEvent.SetName("  Ana María "))
        assertTrue(state().canSave)
        viewModel.onEvent(PreferencesEvent.Save)
        advanceUntilIdle()

        assertEquals("Ana María", profile.preferenceSaves.single().displayName)
        assertEquals("Ana María", state().form.name)
        assertFalse(state().isDirty)
    }

    @Test
    fun aBlankName_blocksSaving() = runTest {
        val (viewModel, state) = loaded()

        viewModel.onEvent(PreferencesEvent.SetHeight(172))
        viewModel.onEvent(PreferencesEvent.SetName("   "))

        assertFalse(state().canSave)
        viewModel.onEvent(PreferencesEvent.Save)
        advanceUntilIdle()
        assertTrue(profile.preferenceSaves.isEmpty())
    }

    @Test
    fun aProfileWithoutName_startsWithAnEmptyField_andCannotSaveUntilFilled() = runTest {
        profile.profileOverride = Result.success(ProfileSummary(null, null, true))
        val (viewModel, state) = loaded()
        assertEquals("", state().form.name)

        viewModel.onEvent(PreferencesEvent.SetHeight(172))
        assertFalse(state().canSave)
        viewModel.onEvent(PreferencesEvent.SetName("Ana"))
        assertTrue(state().canSave)
    }

    @Test
    fun theName_isCutAtTheMaxLength() = runTest {
        val (viewModel, state) = loaded()

        viewModel.onEvent(PreferencesEvent.SetName("a".repeat(150)))

        assertEquals(100, state().form.name.length)
    }

    @Test
    fun unsavedEdits_surviveARecreatedViewModel_andStayDirty() = runTest {
        val handle = SavedStateHandle()
        val first = viewModel(handle)
        advanceUntilIdle()
        first.onEvent(PreferencesEvent.SetName("Beatriz"))
        first.onEvent(PreferencesEvent.SetHeight(165))
        first.onEvent(PreferencesEvent.SetWeight(755))
        first.onEvent(PreferencesEvent.SelectActivity(ActivityLevel.Light))
        first.onEvent(PreferencesEvent.MoveMacro(Macronutrient.Protein, 40))

        val second = viewModel(handle)
        advanceUntilIdle()

        val restored = assertIs<PreferencesUiState.Loaded>(second.uiState.value)
        assertEquals(assertIs<PreferencesUiState.Loaded>(first.uiState.value).form, restored.form)
        assertEquals("Ana", restored.saved.name)
        assertTrue(restored.canSave)
        assertEquals(0, profile.preferenceSaves.size)
    }

    @Test
    fun aSavedForm_clearsTheDraft_soTheNextLoadShowsTheServerValues() = runTest {
        val handle = SavedStateHandle()
        val first = viewModel(handle)
        advanceUntilIdle()
        first.onEvent(PreferencesEvent.SetName("Beatriz"))
        first.onEvent(PreferencesEvent.Save)
        advanceUntilIdle()

        val second = viewModel(handle)
        advanceUntilIdle()

        val restored = assertIs<PreferencesUiState.Loaded>(second.uiState.value)
        assertEquals("Ana", restored.form.name)
        assertFalse(restored.canSave)
    }

    // -- warm-up inclusion --------------------------------------------------------------------------------------

    @Test
    fun warmUpSwitch_loadsTheSavedValue() = runTest {
        training.result = Result.success(TrainingPreferences(warmUpIncluded = true))

        val (_, state) = loaded(PreferencesSection.Training)

        assertTrue(state().form.warmUpIncluded)
        assertFalse(state().canSave)
    }

    @Test
    fun warmUpSwitch_editsTheDraftOnly_andSaveSendsIt() = runTest {
        val (viewModel, state) = loaded(PreferencesSection.Training)

        viewModel.onEvent(PreferencesEvent.SetWarmUpIncluded(true))
        advanceUntilIdle()
        assertTrue(state().form.warmUpIncluded)
        assertTrue(state().canSave)
        assertTrue(profile.preferenceSaves.isEmpty(), "nothing is written before Guardar")

        viewModel.onEvent(PreferencesEvent.Save)
        advanceUntilIdle()

        assertEquals(true, profile.preferenceSaves.single().warmUpIncluded)
        assertFalse(state().canSave)
    }

    @Test
    fun warmUpSwitch_aFailedSaveKeepsTheDraft() = runTest {
        profile.settingsResult = Result.failure(AppException(AppError.Network))
        val (viewModel, state) = loaded(PreferencesSection.Training)

        viewModel.onEvent(PreferencesEvent.SetWarmUpIncluded(true))
        viewModel.onEvent(PreferencesEvent.Save)
        advanceUntilIdle()

        assertTrue(state().form.warmUpIncluded)
        assertTrue(state().canSave)
    }

    @Test
    fun warmUpSwitch_survivesARecreatedViewModel() = runTest {
        val handle = SavedStateHandle()
        val first = viewModel(handle, PreferencesSection.Training)
        advanceUntilIdle()
        first.onEvent(PreferencesEvent.SetWarmUpIncluded(true))

        val second = viewModel(handle, PreferencesSection.Training)
        advanceUntilIdle()

        val restored = assertIs<PreferencesUiState.Loaded>(second.uiState.value)
        assertTrue(restored.form.warmUpIncluded)
        assertFalse(restored.saved.warmUpIncluded)
        assertTrue(restored.canSave)
    }

    @Test
    fun warmUpSwitch_aDraftFromAnOlderVersionDecodesAsOff() {
        val old = """{"name":"Ana","sex":"Male","birthDate":"2000-03-01","heightCm":178,"weightKg":80.0,""" +
            """"activity":"Moderate","strategy":"Deficit","adjustment":-400,""" +
            """"proteinPct":30,"carbsPct":40,"fatPct":30}"""

        assertFalse(decodeDraft(old)!!.warmUpIncluded)
    }

    // -- auto update routine (ADR-70) ---------------------------------------------------------------------------

    @Test
    fun autoUpdateSwitch_isOffByDefault() = runTest {
        val (_, state) = loaded(PreferencesSection.Training)

        assertFalse(state().form.autoUpdateRoutine)
        assertFalse(state().canSave)
    }

    @Test
    fun autoUpdateSwitch_loadsTheSavedValue() = runTest {
        training.result = Result.success(TrainingPreferences(autoUpdateRoutine = true))

        val (_, state) = loaded(PreferencesSection.Training)

        assertTrue(state().form.autoUpdateRoutine)
        assertFalse(state().canSave)
    }

    @Test
    fun autoUpdateSwitch_editsTheDraftOnly_andSaveSendsIt() = runTest {
        val (viewModel, state) = loaded(PreferencesSection.Training)

        viewModel.onEvent(PreferencesEvent.SetAutoUpdateRoutine(true))
        advanceUntilIdle()
        assertTrue(state().form.autoUpdateRoutine)
        assertTrue(state().canSave)
        assertTrue(profile.preferenceSaves.isEmpty(), "nothing is written before Guardar")

        viewModel.onEvent(PreferencesEvent.Save)
        advanceUntilIdle()

        assertEquals(true, profile.preferenceSaves.single().autoUpdateRoutine)
        assertFalse(state().canSave)
    }

    @Test
    fun autoUpdateSwitch_survivesARecreatedViewModel() = runTest {
        val handle = SavedStateHandle()
        val first = viewModel(handle, PreferencesSection.Training)
        advanceUntilIdle()
        first.onEvent(PreferencesEvent.SetAutoUpdateRoutine(true))

        val second = viewModel(handle, PreferencesSection.Training)
        advanceUntilIdle()

        val restored = assertIs<PreferencesUiState.Loaded>(second.uiState.value)
        assertTrue(restored.form.autoUpdateRoutine)
        assertFalse(restored.saved.autoUpdateRoutine)
        assertTrue(restored.canSave)
    }

    @Test
    fun autoUpdateSwitch_aDraftFromAnOlderVersionDecodesAsOff() {
        val old = """{"name":"Ana","sex":"Male","birthDate":"2000-03-01","heightCm":178,"weightKg":80.0,""" +
            """"activity":"Moderate","strategy":"Deficit","adjustment":-400,""" +
            """"proteinPct":30,"carbsPct":40,"fatPct":30}"""

        assertFalse(decodeDraft(old)!!.autoUpdateRoutine)
    }

    // -- previous values mode -----------------------------------------------------------------------------------

    @Test
    fun previousValuesMode_loadsTheSavedValue() = runTest {
        training.result = Result.success(TrainingPreferences(previousValuesMode = PreviousValuesMode.Routine))

        val (_, state) = loaded(PreferencesSection.Training)

        assertEquals(PreviousValuesMode.Routine, state().form.previousValuesMode)
        assertFalse(state().canSave)
    }

    @Test
    fun previousValuesMode_editsTheDraftOnly_andSaveSendsIt() = runTest {
        val (viewModel, state) = loaded(PreferencesSection.Training)

        viewModel.onEvent(PreferencesEvent.SetPreviousValuesMode(PreviousValuesMode.Routine))
        advanceUntilIdle()
        assertEquals(PreviousValuesMode.Routine, state().form.previousValuesMode)
        assertTrue(state().canSave)
        assertTrue(profile.preferenceSaves.isEmpty(), "nothing is written before Guardar")

        viewModel.onEvent(PreferencesEvent.Save)
        advanceUntilIdle()

        assertEquals(PreviousValuesMode.Routine, profile.preferenceSaves.single().previousValuesMode)
        assertFalse(state().canSave)
    }

    @Test
    fun previousValuesMode_choosingTheSavedValueAgainIsNotDirty() = runTest {
        val (viewModel, state) = loaded(PreferencesSection.Training)

        viewModel.onEvent(PreferencesEvent.SetPreviousValuesMode(PreviousValuesMode.Routine))
        viewModel.onEvent(PreferencesEvent.SetPreviousValuesMode(PreviousValuesMode.Overall))

        assertFalse(state().canSave)
    }

    @Test
    fun previousValuesMode_survivesARecreatedViewModel() = runTest {
        val handle = SavedStateHandle()
        val first = viewModel(handle, PreferencesSection.Training)
        advanceUntilIdle()
        first.onEvent(PreferencesEvent.SetPreviousValuesMode(PreviousValuesMode.Routine))

        val second = viewModel(handle, PreferencesSection.Training)
        advanceUntilIdle()

        val restored = assertIs<PreferencesUiState.Loaded>(second.uiState.value)
        assertEquals(PreviousValuesMode.Routine, restored.form.previousValuesMode)
        assertEquals(PreviousValuesMode.Overall, restored.saved.previousValuesMode)
        assertTrue(restored.canSave)
    }

    @Test
    fun previousValuesMode_aDraftFromAnOlderVersionDecodesAsOverall() {
        val old = """{"name":"Ana","sex":"Male","birthDate":"2000-03-01","heightCm":178,"weightKg":80.0,""" +
            """"activity":"Moderate","strategy":"Deficit","adjustment":-400,""" +
            """"proteinPct":30,"carbsPct":40,"fatPct":30}"""

        assertEquals(PreviousValuesMode.Overall, decodeDraft(old)!!.previousValuesMode)
    }

    // -- calculator settings ------------------------------------------------------------------------------------

    @Test
    fun calculators_loadTheSavedValues() = runTest {
        training.result = Result.success(
            TrainingPreferences(barWeightKg = 15.0, plateKg = listOf(20.0, 5.0)),
        )

        val (_, state) = loaded(PreferencesSection.Training)

        assertEquals(CalculatorSettings(15.0, listOf(20.0, 5.0)), state().form.calculators)
        assertEquals(10.0, state().form.calculators.roundingKg)
        assertFalse(state().canSave)
    }

    @Test
    fun calculators_editTheDraftOnly_andSaveSendsThem() = runTest {
        val (viewModel, state) = loaded(PreferencesSection.Training)

        viewModel.onEvent(PreferencesEvent.SetBarWeight(7.5))
        viewModel.onEvent(PreferencesEvent.SetPlateEnabled(25.0, enabled = false))
        advanceUntilIdle()

        assertTrue(state().canSave)
        assertTrue(profile.preferenceSaves.isEmpty(), "nothing is written before Guardar")
        viewModel.onEvent(PreferencesEvent.Save)
        advanceUntilIdle()

        val expected = CalculatorSettings(7.5, CalculatorSettings.PLATE_CATALOGUE - 25.0)
        assertEquals(expected, profile.preferenceSaves.single().calculators)
        assertFalse(state().canSave)
    }

    @Test
    fun aDraftSavedBeforeAdr56_withRoundingFields_stillRestores() {
        val old = """{"name":"Ana","sex":"Male","birthDate":"2000-03-01","heightCm":178,"weightKg":80.0,""" +
            """"activity":"Moderate","strategy":"Deficit","adjustment":-400,""" +
            """"proteinPct":30,"carbsPct":40,"fatPct":30,"plateRoundingKg":5.0,"dumbbellRoundingKg":1.0}"""

        assertEquals("Ana", decodeDraft(old)?.name)
    }

    @Test
    fun dumbbellHandle_isClampedAndStepsInHalfKilos() = runTest {
        val (viewModel, state) = loaded(PreferencesSection.Training)

        viewModel.onEvent(PreferencesEvent.SetDumbbellHandle(0.0))
        assertEquals(0.5, state().form.calculators.dumbbellHandleKg)
        viewModel.onEvent(PreferencesEvent.SetDumbbellHandle(40.0))
        assertEquals(10.0, state().form.calculators.dumbbellHandleKg)
        viewModel.onEvent(PreferencesEvent.SetDumbbellHandle(2.5))
        assertEquals(2.5, state().form.calculators.dumbbellHandleKg)
    }

    @Test
    fun dumbbellHandle_loadsMakesTheFormDirty_andSaveSendsIt() = runTest {
        training.result = Result.success(TrainingPreferences(dumbbellHandleKg = 1.5))
        val (viewModel, state) = loaded(PreferencesSection.Training)

        assertEquals(1.5, state().form.calculators.dumbbellHandleKg)
        assertFalse(state().canSave)
        viewModel.onEvent(PreferencesEvent.SetDumbbellHandle(2.0))
        assertTrue(state().canSave)
        viewModel.onEvent(PreferencesEvent.Save)
        advanceUntilIdle()

        assertEquals(2.0, profile.preferenceSaves.single().calculators?.dumbbellHandleKg)
    }

    @Test
    fun dumbbellHandle_survivesARecreatedViewModel() = runTest {
        val handle = SavedStateHandle()
        val first = viewModel(handle, PreferencesSection.Training)
        advanceUntilIdle()
        first.onEvent(PreferencesEvent.SetDumbbellHandle(3.0))

        val second = viewModel(handle, PreferencesSection.Training)
        advanceUntilIdle()

        val restored = assertIs<PreferencesUiState.Loaded>(second.uiState.value)
        assertEquals(3.0, restored.form.calculators.dumbbellHandleKg)
        assertTrue(restored.canSave)
    }

    @Test
    fun plates_stayOrderedHeaviestFirst_whenReEnabled() = runTest {
        val (viewModel, state) = loaded(PreferencesSection.Training)

        viewModel.onEvent(PreferencesEvent.SetPlateEnabled(20.0, enabled = false))
        viewModel.onEvent(PreferencesEvent.SetPlateEnabled(20.0, enabled = true))

        assertEquals(CalculatorSettings.PLATE_CATALOGUE, state().form.calculators.plateKg)
        assertFalse(state().canSave)
    }

    @Test
    fun plates_theLastOneCannotBeSwitchedOff_andShowsTheError() = runTest {
        training.result = Result.success(TrainingPreferences(plateKg = listOf(2.5)))
        val (viewModel, state) = loaded(PreferencesSection.Training)

        viewModel.onEvent(PreferencesEvent.SetPlateEnabled(2.5, enabled = false))

        assertEquals(listOf(2.5), state().form.calculators.plateKg)
        assertTrue(state().platesError)
        assertFalse(state().canSave)

        viewModel.onEvent(PreferencesEvent.SetPlateEnabled(5.0, enabled = true))
        assertFalse(state().platesError)
        assertEquals(listOf(5.0, 2.5), state().form.calculators.plateKg)
    }

    @Test
    fun calculators_surviveARecreatedViewModel() = runTest {
        val handle = SavedStateHandle()
        val first = viewModel(handle, PreferencesSection.Training)
        advanceUntilIdle()
        first.onEvent(PreferencesEvent.SetBarWeight(10.0))
        first.onEvent(PreferencesEvent.SetPlateEnabled(0.5, enabled = false))

        val second = viewModel(handle, PreferencesSection.Training)
        advanceUntilIdle()

        val restored = assertIs<PreferencesUiState.Loaded>(second.uiState.value)
        assertEquals(10.0, restored.form.calculators.barWeightKg)
        assertFalse(0.5 in restored.form.calculators.plateKg)
        assertEquals(20.0, restored.saved.calculators.barWeightKg)
        assertTrue(restored.canSave)
    }

    @Test
    fun calculators_aDraftFromAnOlderVersionDecodesToTheDefaults() {
        val old = """{"name":"Ana","sex":"Male","birthDate":"2000-03-01","heightCm":178,"weightKg":80.0,""" +
            """"activity":"Moderate","strategy":"Deficit","adjustment":-400,""" +
            """"proteinPct":30,"carbsPct":40,"fatPct":30}"""

        assertEquals(CalculatorSettings(), decodeDraft(old)!!.calculators)
    }

    @Test
    fun warmUpSwitch_aFailedReadIsLoadFailed_andRetryReloads() = runTest {
        training.result = Result.failure(AppException(AppError.Network))
        val viewModel = viewModel(section = PreferencesSection.Training)
        advanceUntilIdle()
        assertEquals(PreferencesUiState.LoadFailed, viewModel.uiState.value)

        training.result = Result.success(TrainingPreferences())
        viewModel.onEvent(PreferencesEvent.Retry)
        advanceUntilIdle()

        assertIs<PreferencesUiState.Loaded>(viewModel.uiState.value)
    }

    // -- cooking ("Cocina", US-50) ------------------------------------------------------------------------------

    @Test
    fun cooking_loadsTheSavedAmounts_andAMissingRowGivesTheDefaults() = runTest {
        val (_, defaults) = loaded(PreferencesSection.Cooking)
        assertEquals(FoodPreferences(), defaults().form.toFoodPreferences())
        assertFalse(defaults().canSave)

        foodPreferences.result = Result.success(FoodPreferences(10.0, 7.5, 20.0))
        val (_, custom) = loaded(PreferencesSection.Cooking)

        assertEquals(FoodPreferences(10.0, 7.5, 20.0), custom().form.toFoodPreferences())
        assertEquals("7.5", custom().form.foodPreferences.airFryerText)
    }

    @Test
    fun cooking_aFailedRead_isLoadFailed_andRetryReloads() = runTest {
        foodPreferences.result = Result.failure(AppException(AppError.Network))
        val viewModel = viewModel(section = PreferencesSection.Cooking)
        advanceUntilIdle()
        assertEquals(PreferencesUiState.LoadFailed, viewModel.uiState.value)

        foodPreferences.result = Result.success(FoodPreferences())
        viewModel.onEvent(PreferencesEvent.Retry)
        advanceUntilIdle()

        assertIs<PreferencesUiState.Loaded>(viewModel.uiState.value)
    }

    @Test
    fun cooking_editsMakeTheFormDirty_andSaveSendsThemOnce() = runTest {
        val (viewModel, state) = loaded(PreferencesSection.Cooking)

        viewModel.onEvent(PreferencesEvent.SetFatPctGriddle("8,5"))
        viewModel.onEvent(PreferencesEvent.SetFatPctAirFryer("0"))
        viewModel.onEvent(PreferencesEvent.SetFatPctFried("20"))
        advanceUntilIdle()
        assertTrue(state().canSave)
        assertTrue(profile.preferenceSaves.isEmpty(), "nothing is written before Guardar")

        viewModel.onEvent(PreferencesEvent.Save)
        advanceUntilIdle()

        assertEquals(FoodPreferences(8.5, 0.0, 20.0), profile.preferenceSaves.single().foodPreferences)
        assertFalse(state().canSave)
    }

    @Test
    fun cooking_invalidAmounts_showTheFieldErrorAndBlockSaving() = runTest {
        val (viewModel, state) = loaded(PreferencesSection.Cooking)

        for (bad in listOf("-1", "20.1", "", "abc")) {
            viewModel.onEvent(PreferencesEvent.SetFatPctGriddle(bad))
            advanceUntilIdle()
            assertTrue(state().form.foodPreferences.griddleError, "'$bad' must be an error")
            assertFalse(state().form.foodPreferences.airFryerError)
            assertFalse(state().canSave, "'$bad' must block saving")
        }
        viewModel.onEvent(PreferencesEvent.SetFatPctGriddle("5"))
        viewModel.onEvent(PreferencesEvent.SetFatPctFried("-0.5"))
        advanceUntilIdle()
        assertTrue(state().form.foodPreferences.friedError)
        assertFalse(state().canSave)
    }

    @Test
    fun cooking_theBoundsZeroAndTwenty_areValid() = runTest {
        val (viewModel, state) = loaded(PreferencesSection.Cooking)

        viewModel.onEvent(PreferencesEvent.SetFatPctFried("0"))
        advanceUntilIdle()
        assertTrue(state().canSave)

        viewModel.onEvent(PreferencesEvent.SetFatPctFried("20"))
        advanceUntilIdle()
        assertTrue(state().canSave)
        assertFalse(state().form.foodPreferences.friedError)
    }

    @Test
    fun cooking_revertingTheAmount_makesTheFormCleanAgain() = runTest {
        val (viewModel, state) = loaded(PreferencesSection.Cooking)

        viewModel.onEvent(PreferencesEvent.SetFatPctGriddle("6"))
        viewModel.onEvent(PreferencesEvent.SetFatPctGriddle("1"))
        advanceUntilIdle()

        assertFalse(state().isDirty)
    }

    @Test
    fun cooking_editedAmountsSurviveARecreatedViewModel_andAnEqualFormClearsTheDraft() = runTest {
        val handle = SavedStateHandle()
        val first = viewModel(handle, PreferencesSection.Cooking)
        advanceUntilIdle()
        first.onEvent(PreferencesEvent.SetFatPctAirFryer("12"))
        first.onEvent(PreferencesEvent.SetFatPctFried("abc"))

        val second = viewModel(handle, PreferencesSection.Cooking)
        advanceUntilIdle()

        val restored = assertIs<PreferencesUiState.Loaded>(second.uiState.value)
        assertEquals("12", restored.form.foodPreferences.airFryerText)
        assertEquals("abc", restored.form.foodPreferences.friedText)
        assertEquals("2", restored.saved.foodPreferences.airFryerText)
        assertFalse(restored.canSave)

        second.onEvent(PreferencesEvent.SetFatPctAirFryer("2"))
        second.onEvent(PreferencesEvent.SetFatPctFried("8"))
        assertEquals(null, handle.get<String>("preferences_draft"))
    }

    @Test
    fun cooking_aDraftFromAnOlderVersionDecodesToTheDefaults() {
        val old = """{"name":"Ana","sex":"Male","birthDate":"2000-03-01","heightCm":178,"weightKg":80.0,""" +
            """"activity":"Moderate","strategy":"Deficit","adjustment":-400,""" +
            """"proteinPct":30,"carbsPct":40,"fatPct":30}"""

        assertEquals(FoodPreferences(), decodeDraft(old)!!.toFoodPreferences())
    }

    // -- sections (US-56) ---------------------------------------------------------------------------------------

    private fun assertOptionalParametersUntouched(saved: SavedPreferences) {
        assertNull(saved.warmUpIncluded)
        assertNull(saved.previousValuesMode)
        assertNull(saved.calculators)
        assertNull(saved.foodPreferences)
        assertNull(saved.autoUpdateRoutine)
    }

    @Test
    fun theSectionComesFromTheSavedStateHandle() = runTest {
        for (section in PreferencesSection.entries) {
            val (_, state) = loaded(section)

            assertEquals(section, state().section)
        }
    }

    @Test
    fun anEditOfAnotherSection_isIgnored_andNeverMakesTheFormDirty() = runTest {
        val edits = mapOf(
            PreferencesSection.PersonalData to PreferencesEvent.SetHeight(190),
            PreferencesSection.EnergyGoal to PreferencesEvent.Energy(EnergyStrategyEvent.StepDown),
            PreferencesSection.Macros to PreferencesEvent.MoveMacro(Macronutrient.Protein, 40),
            PreferencesSection.Cooking to PreferencesEvent.SetFatPctFried("9"),
            PreferencesSection.Training to PreferencesEvent.SetWarmUpIncluded(true),
            PreferencesSection.CheckIn to PreferencesEvent.SetCheckInWeekday(3),
        )
        for (section in PreferencesSection.entries) {
            val (viewModel, state) = loaded(section)
            val before = state().form

            edits.filterKeys { it != section }.values.forEach(viewModel::onEvent)
            advanceUntilIdle()

            assertEquals(before, state().form, "form of $section")
            assertFalse(state().isDirty)
            assertFalse(state().canSave)
        }
    }

    @Test
    fun personalData_saveSendsTheEditedFieldsAndTheWeight_butNoOptionalParameters() = runTest {
        val (viewModel, _) = loaded(PreferencesSection.PersonalData)

        viewModel.onEvent(PreferencesEvent.SetName("Bea"))
        viewModel.onEvent(PreferencesEvent.SetWeight(850))
        viewModel.onEvent(PreferencesEvent.Save)
        advanceUntilIdle()

        val saved = profile.preferenceSaves.single()
        assertEquals("Bea", saved.displayName)
        assertEquals(85.0, saved.weight?.weightKg)
        assertEquals(tdee(178, 85.0) - 400, saved.goals.targetKcal)
        assertOptionalParametersUntouched(saved)
    }

    @Test
    fun personalData_anUnchangedWeightIsNotSent() = runTest {
        val (viewModel, _) = loaded(PreferencesSection.PersonalData)

        viewModel.onEvent(PreferencesEvent.SetName("Bea"))
        viewModel.onEvent(PreferencesEvent.Save)
        advanceUntilIdle()

        assertNull(profile.preferenceSaves.single().weight)
    }

    @Test
    fun otherSections_neverSendAWeight_andResendTheSavedPersonalData() = runTest {
        for (section in listOf(PreferencesSection.EnergyGoal, PreferencesSection.Macros, PreferencesSection.Cooking)) {
            profile.preferenceSaves.clear()
            val (viewModel, _) = loaded(section)
            when (section) {
                PreferencesSection.EnergyGoal ->
                    viewModel.onEvent(PreferencesEvent.Energy(EnergyStrategyEvent.SelectStrategy(GoalStrategy.Surplus)))

                PreferencesSection.Macros -> viewModel.onEvent(PreferencesEvent.MoveMacro(Macronutrient.Protein, 35))

                else -> viewModel.onEvent(PreferencesEvent.SetFatPctFried("9"))
            }
            viewModel.onEvent(PreferencesEvent.Save)
            advanceUntilIdle()

            val saved = profile.preferenceSaves.single()
            assertNull(saved.weight, "weight of $section")
            assertEquals("Ana", saved.displayName)
            assertEquals(178, saved.biometrics.heightCm)
            assertEquals(Sex.Male, saved.biometrics.sex)
        }
    }

    @Test
    fun training_saveSendsOnlyTheTrainingParameters() = runTest {
        val (viewModel, _) = loaded(PreferencesSection.Training)

        viewModel.onEvent(PreferencesEvent.SetWarmUpIncluded(true))
        viewModel.onEvent(PreferencesEvent.SetPreviousValuesMode(PreviousValuesMode.Routine))
        viewModel.onEvent(PreferencesEvent.SetAutoUpdateRoutine(true))
        viewModel.onEvent(PreferencesEvent.Save)
        advanceUntilIdle()

        val saved = profile.preferenceSaves.single()
        assertEquals(true, saved.warmUpIncluded)
        assertEquals(PreviousValuesMode.Routine, saved.previousValuesMode)
        assertEquals(true, saved.autoUpdateRoutine)
        assertEquals(CalculatorSettings(), saved.calculators)
        assertNull(saved.foodPreferences)
        assertNull(saved.weight)
        assertEquals(GoalStrategy.Deficit, saved.goals.strategy)
    }

    @Test
    fun checkIn_loadsTheSavedWeekdayAndIsCleanUntilEdited() = runTest {
        profile.checkInWeekday = Result.success(5)

        val (_, state) = loaded(PreferencesSection.CheckIn)

        assertEquals(5, state().form.checkInWeekday)
        assertFalse(state().isDirty)
        assertFalse(state().canSave)
    }

    @Test
    fun checkIn_saveWritesOnlyTheWeekday_andRefreshesTheCenter() = runTest {
        val env = ReminderEnv(today)
        val instant = Instant.parse("2026-10-14T08:00:00Z")
        env.weight.rows += BodyMeasurement("t", instant, 80.0, MeasurementSource.Manual)
        env.weight.onboarding =
            BodyMeasurement("o", Instant.parse("2026-10-01T08:00:00Z"), 80.0, MeasurementSource.Manual)
        env.circumferences.rows += BodyCircumference("c", CircumferenceSite.Waist, 90.0, instant, instant)
        val load = LoadReminders(
            env.weight,
            env.circumferences,
            env.settings,
            profile,
            env.checkIns,
            env.clock,
            env.timeZone,
            ComputeReminders(),
        )
        val coordinator = ReminderCoordinator(load, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        coordinator.refresh()
        assertEquals(LocalDate(2026, 10, 11), coordinator.checkIn.value?.day)
        val (viewModel, state) = loaded(PreferencesSection.CheckIn, coordinator)

        viewModel.onEvent(PreferencesEvent.SetCheckInWeekday(3))
        assertTrue(state().canSave)
        viewModel.onEvent(PreferencesEvent.Save)
        advanceUntilIdle()

        assertEquals(listOf(3), profile.checkInWeekdaySaves)
        assertTrue(profile.preferenceSaves.isEmpty(), "the preferences RPC is not involved")
        assertTrue(state().isSaved)
        assertEquals(3, state().saved.checkInWeekday)
        assertEquals(today, coordinator.checkIn.value?.day, "the center follows the new day")
    }

    @Test
    fun otherSections_neverWriteTheWeekday() = runTest {
        for (section in PreferencesSection.entries.filter { it != PreferencesSection.CheckIn }) {
            val (viewModel, _) = loaded(section)
            when (section) {
                PreferencesSection.PersonalData -> viewModel.onEvent(PreferencesEvent.SetName("Bea"))

                PreferencesSection.EnergyGoal ->
                    viewModel.onEvent(PreferencesEvent.Energy(EnergyStrategyEvent.StepDown))

                PreferencesSection.Macros -> viewModel.onEvent(PreferencesEvent.MoveMacro(Macronutrient.Protein, 35))

                PreferencesSection.Cooking -> viewModel.onEvent(PreferencesEvent.SetFatPctFried("9"))

                else -> viewModel.onEvent(PreferencesEvent.SetWarmUpIncluded(true))
            }
            viewModel.onEvent(PreferencesEvent.Save)
            advanceUntilIdle()
        }

        assertTrue(profile.checkInWeekdaySaves.isEmpty())
    }

    @Test
    fun checkIn_failedSaveKeepsTheDraftAndEmitsAnEffect() = runTest {
        profile.checkInWeekdaySaveResult = Result.failure(AppException(AppError.Network))
        val (viewModel, state) = loaded(PreferencesSection.CheckIn)

        viewModel.onEvent(PreferencesEvent.SetCheckInWeekday(2))
        viewModel.effects.test {
            viewModel.onEvent(PreferencesEvent.Save)
            advanceUntilIdle()
            assertEquals(PreferencesEffect.SaveFailed(offline = true), awaitItem())
        }

        assertFalse(state().isSaved)
        assertEquals(2, state().form.checkInWeekday)
        assertTrue(state().isDirty)
    }

    @Test
    fun checkIn_discardingLeavesTheSavedDay() = runTest {
        profile.checkInWeekday = Result.success(7)
        val handle = SavedStateHandle()
        val first = viewModel(handle, PreferencesSection.CheckIn)
        advanceUntilIdle()
        first.onEvent(PreferencesEvent.SetCheckInWeekday(1))

        first.onEvent(PreferencesEvent.SetCheckInWeekday(7))

        val state = assertIs<PreferencesUiState.Loaded>(first.uiState.value)
        assertFalse(state.isDirty)
        assertTrue(profile.checkInWeekdaySaves.isEmpty())
    }

    @Test
    fun cooking_saveSendsOnlyTheFatPercentages() = runTest {
        val (viewModel, _) = loaded(PreferencesSection.Cooking)

        viewModel.onEvent(PreferencesEvent.SetFatPctGriddle("8,5"))
        viewModel.onEvent(PreferencesEvent.Save)
        advanceUntilIdle()

        val saved = profile.preferenceSaves.single()
        assertEquals(8.5, saved.foodPreferences?.fatPctGriddle)
        assertNull(saved.warmUpIncluded)
        assertNull(saved.previousValuesMode)
        assertNull(saved.calculators)
        assertNull(saved.autoUpdateRoutine)
    }

    @Test
    fun canSave_blankName_blocksPersonalData() = runTest {
        val (viewModel, state) = loaded(PreferencesSection.PersonalData)

        viewModel.onEvent(PreferencesEvent.SetHeight(172))
        viewModel.onEvent(PreferencesEvent.SetName("  "))

        assertFalse(state().canSave)
    }

    @Test
    fun canSave_badFatPercentage_blocksCooking() = runTest {
        val (viewModel, state) = loaded(PreferencesSection.Cooking)

        viewModel.onEvent(PreferencesEvent.SetFatPctFried("21"))

        assertFalse(state().canSave)
    }

    @Test
    fun canSave_offlineAndSaving_blockTraining() = runTest {
        val (viewModel, state) = loaded(PreferencesSection.Training)
        viewModel.onEvent(PreferencesEvent.SetWarmUpIncluded(true))
        assertTrue(state().canSave)

        connectivity.state.value = false
        advanceUntilIdle()
        assertFalse(state().canSave, "offline")
        connectivity.state.value = true
        advanceUntilIdle()

        viewModel.onEvent(PreferencesEvent.Save)
        assertTrue(state().isSaving)
        assertFalse(state().canSave, "saving")
        advanceUntilIdle()
    }

    @Test
    fun canSave_energyGoalNeedsAChange() = runTest {
        val (viewModel, state) = loaded(PreferencesSection.EnergyGoal)
        assertFalse(state().canSave)

        viewModel.onEvent(PreferencesEvent.Energy(EnergyStrategyEvent.StepDown))

        assertTrue(state().canSave)
    }

    @Test
    fun training_theLastPlateCannotBeSwitchedOff_butOtherEditsStillSave() = runTest {
        training.result = Result.success(TrainingPreferences(plateKg = listOf(2.5)))
        val (viewModel, state) = loaded(PreferencesSection.Training)

        viewModel.onEvent(PreferencesEvent.SetPlateEnabled(2.5, enabled = false))
        viewModel.onEvent(PreferencesEvent.SetWarmUpIncluded(true))

        assertTrue(state().canSave)
        assertEquals(listOf(2.5), state().form.calculators.plateKg)
    }

    @Test
    fun aSectionDraft_survivesARecreatedViewModel_andOtherSectionsStayFresh() = runTest {
        val freshSplit = MacroSplit(25, 45, 30)
        for (section in PreferencesSection.entries) {
            val handle = SavedStateHandle()
            val first = viewModel(handle, section)
            advanceUntilIdle()
            when (section) {
                PreferencesSection.PersonalData -> first.onEvent(PreferencesEvent.SetName("Bea"))
                PreferencesSection.EnergyGoal -> first.onEvent(PreferencesEvent.Energy(EnergyStrategyEvent.StepDown))
                PreferencesSection.Macros -> first.onEvent(PreferencesEvent.MoveMacro(Macronutrient.Protein, 40))
                PreferencesSection.Cooking -> first.onEvent(PreferencesEvent.SetFatPctFried("9"))
                PreferencesSection.Training -> first.onEvent(PreferencesEvent.SetWarmUpIncluded(true))
                PreferencesSection.CheckIn -> first.onEvent(PreferencesEvent.SetCheckInWeekday(3))
            }
            val edited = assertIs<PreferencesUiState.Loaded>(first.uiState.value).form
            // The server value of another section changes while the editor is away.
            goals.row = NutritionGoals(GoalStrategy.Deficit, -400, 2000, freshSplit)

            val second = viewModel(handle, section)
            advanceUntilIdle()

            val restored = assertIs<PreferencesUiState.Loaded>(second.uiState.value)
            assertTrue(restored.form.sectionEquals(section, edited), "$section keeps its draft")
            assertTrue(restored.canSave, "$section still dirty")
            if (section != PreferencesSection.Macros) {
                assertEquals(freshSplit, restored.form.split, "$section sees the fresh split")
            }
            goals.row = NutritionGoals(GoalStrategy.Deficit, -400, 2000, MacroSplit.DEFAULT)
        }
    }

    @Test
    fun aDraftEqualToTheSavedValues_isDroppedOnRestore() = runTest {
        val handle = SavedStateHandle()
        val first = viewModel(handle, PreferencesSection.Macros)
        advanceUntilIdle()
        first.onEvent(PreferencesEvent.MoveMacro(Macronutrient.Protein, 40))
        val editedSplit = assertIs<PreferencesUiState.Loaded>(first.uiState.value).form.split
        // The same split is saved elsewhere meanwhile.
        goals.row = NutritionGoals(GoalStrategy.Deficit, -400, 2000, editedSplit)

        val second = viewModel(handle, PreferencesSection.Macros)
        advanceUntilIdle()

        assertFalse(assertIs<PreferencesUiState.Loaded>(second.uiState.value).isDirty)
        assertNull(handle.get<String>("preferences_draft"))
    }

    @Test
    fun anOldWholeFormDraft_onlyRestoresTheFieldsOfTheSection() = runTest {
        val handle = SavedStateHandle()
        val whole = PreferencesForm(
            name = "Old",
            sex = Sex.Female,
            birthDate = birthDate,
            heightCm = 150,
            weightKg = 55.0,
            activity = ActivityLevel.High,
            strategy = GoalStrategy.Surplus,
            adjustment = 250,
            split = MacroSplit(40, 30, 30),
            warmUpIncluded = true,
        )
        handle["preferences_draft"] = whole.encodeDraft()

        val viewModel = viewModel(handle, PreferencesSection.Training)
        advanceUntilIdle()

        val state = assertIs<PreferencesUiState.Loaded>(viewModel.uiState.value)
        assertTrue(state.form.warmUpIncluded)
        assertEquals("Ana", state.form.name)
        assertEquals(178, state.form.heightCm)
        assertEquals(GoalStrategy.Deficit, state.form.strategy)
        assertEquals(MacroSplit.DEFAULT, state.form.split)
    }

    @Test
    fun refresh_inAnotherSection_isIgnored() = runTest {
        val (viewModel, state) = loaded(PreferencesSection.Macros)
        val hc = BodyMeasurement("hc", Instant.parse("2026-10-14T07:00:00Z"), 79.4, MeasurementSource.HealthConnect)
        weights.row = hc
        weights.rows = listOf(hc)

        viewModel.onEvent(PreferencesEvent.Refresh)
        advanceUntilIdle()

        assertFalse(state().weightFromHealthConnect)
        assertEquals(80.0, state().form.weightKg)
    }
}
