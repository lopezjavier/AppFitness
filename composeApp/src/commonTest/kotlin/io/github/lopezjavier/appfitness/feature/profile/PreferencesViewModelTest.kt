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
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.CalculatorSettings
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
import io.github.lopezjavier.appfitness.domain.usecase.HealthWriteBack
import io.github.lopezjavier.appfitness.domain.usecase.SavePreferences
import io.github.lopezjavier.appfitness.feature.food.FakeFoodPreferencesRepository
import io.github.lopezjavier.appfitness.feature.goals.EnergyStrategyEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) = PreferencesViewModel(
        profileRepository = profile,
        goalsRepository = goals,
        bodyMeasurementRepository = weights,
        trainingPreferencesRepository = training,
        foodPreferencesRepository = foodPreferences,
        savePreferences = SavePreferences(profile),
        connectivity = connectivity,
        clock = FakeClock(today, now),
        timeZone = TimeZone.UTC,
        savedState = savedState,
        healthWriteBack = HealthWriteBack(health, weights, FakeClock(today, now)),
    )

    private fun TestScope.loaded(): Pair<PreferencesViewModel, () -> PreferencesUiState.Loaded> {
        val viewModel = viewModel()
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
        val (viewModel, state) = loaded()

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
    fun strategyChangeAndSplit_areSavedByTheSingleSave() = runTest {
        val (viewModel, _) = loaded()

        viewModel.onEvent(PreferencesEvent.Energy(EnergyStrategyEvent.SelectStrategy(GoalStrategy.Surplus)))
        viewModel.onEvent(PreferencesEvent.MoveMacro(Macronutrient.Protein, 35))
        viewModel.onEvent(PreferencesEvent.Save)
        advanceUntilIdle()

        val saved = profile.preferenceSaves.single().goals
        assertEquals(GoalStrategy.Surplus, saved.strategy)
        assertEquals(250, saved.calorieAdjustment)
        assertEquals(MacroSplit(35, 42, 23), saved.split)
    }

    @Test
    fun saveSuccess_emitsSaved() = runTest {
        val (viewModel, _) = loaded()
        viewModel.effects.test {
            viewModel.onEvent(PreferencesEvent.SelectActivity(ActivityLevel.High))
            viewModel.onEvent(PreferencesEvent.Save)
            advanceUntilIdle()
            assertEquals(PreferencesEffect.Saved, awaitItem())
        }
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

        val (_, state) = loaded()

        assertTrue(state().form.warmUpIncluded)
        assertFalse(state().canSave)
    }

    @Test
    fun warmUpSwitch_editsTheDraftOnly_andSaveSendsIt() = runTest {
        val (viewModel, state) = loaded()

        viewModel.onEvent(PreferencesEvent.SetWarmUpIncluded(true))
        advanceUntilIdle()
        assertTrue(state().form.warmUpIncluded)
        assertTrue(state().canSave)
        assertTrue(profile.preferenceSaves.isEmpty(), "nothing is written before Guardar")

        viewModel.onEvent(PreferencesEvent.Save)
        advanceUntilIdle()

        assertTrue(profile.preferenceSaves.single().warmUpIncluded)
        assertFalse(state().canSave)
    }

    @Test
    fun warmUpSwitch_aFailedSaveKeepsTheDraft() = runTest {
        profile.settingsResult = Result.failure(AppException(AppError.Network))
        val (viewModel, state) = loaded()

        viewModel.onEvent(PreferencesEvent.SetWarmUpIncluded(true))
        viewModel.onEvent(PreferencesEvent.Save)
        advanceUntilIdle()

        assertTrue(state().form.warmUpIncluded)
        assertTrue(state().canSave)
    }

    @Test
    fun warmUpSwitch_survivesARecreatedViewModel() = runTest {
        val handle = SavedStateHandle()
        val first = viewModel(handle)
        advanceUntilIdle()
        first.onEvent(PreferencesEvent.SetWarmUpIncluded(true))

        val second = viewModel(handle)
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

    // -- previous values mode -----------------------------------------------------------------------------------

    @Test
    fun previousValuesMode_loadsTheSavedValue() = runTest {
        training.result = Result.success(TrainingPreferences(previousValuesMode = PreviousValuesMode.Routine))

        val (_, state) = loaded()

        assertEquals(PreviousValuesMode.Routine, state().form.previousValuesMode)
        assertFalse(state().canSave)
    }

    @Test
    fun previousValuesMode_editsTheDraftOnly_andSaveSendsIt() = runTest {
        val (viewModel, state) = loaded()

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
        val (viewModel, state) = loaded()

        viewModel.onEvent(PreferencesEvent.SetPreviousValuesMode(PreviousValuesMode.Routine))
        viewModel.onEvent(PreferencesEvent.SetPreviousValuesMode(PreviousValuesMode.Overall))

        assertFalse(state().canSave)
    }

    @Test
    fun previousValuesMode_survivesARecreatedViewModel() = runTest {
        val handle = SavedStateHandle()
        val first = viewModel(handle)
        advanceUntilIdle()
        first.onEvent(PreferencesEvent.SetPreviousValuesMode(PreviousValuesMode.Routine))

        val second = viewModel(handle)
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

        val (_, state) = loaded()

        assertEquals(CalculatorSettings(15.0, listOf(20.0, 5.0)), state().form.calculators)
        assertEquals(10.0, state().form.calculators.roundingKg)
        assertFalse(state().canSave)
    }

    @Test
    fun calculators_editTheDraftOnly_andSaveSendsThem() = runTest {
        val (viewModel, state) = loaded()

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
        val (viewModel, state) = loaded()

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
        val (viewModel, state) = loaded()

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
        val first = viewModel(handle)
        advanceUntilIdle()
        first.onEvent(PreferencesEvent.SetDumbbellHandle(3.0))

        val second = viewModel(handle)
        advanceUntilIdle()

        val restored = assertIs<PreferencesUiState.Loaded>(second.uiState.value)
        assertEquals(3.0, restored.form.calculators.dumbbellHandleKg)
        assertTrue(restored.canSave)
    }

    @Test
    fun plates_stayOrderedHeaviestFirst_whenReEnabled() = runTest {
        val (viewModel, state) = loaded()

        viewModel.onEvent(PreferencesEvent.SetPlateEnabled(20.0, enabled = false))
        viewModel.onEvent(PreferencesEvent.SetPlateEnabled(20.0, enabled = true))

        assertEquals(CalculatorSettings.PLATE_CATALOGUE, state().form.calculators.plateKg)
        assertFalse(state().canSave)
    }

    @Test
    fun plates_theLastOneCannotBeSwitchedOff_andShowsTheError() = runTest {
        training.result = Result.success(TrainingPreferences(plateKg = listOf(2.5)))
        val (viewModel, state) = loaded()

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
        val first = viewModel(handle)
        advanceUntilIdle()
        first.onEvent(PreferencesEvent.SetBarWeight(10.0))
        first.onEvent(PreferencesEvent.SetPlateEnabled(0.5, enabled = false))

        val second = viewModel(handle)
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
        val viewModel = viewModel()
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
        val (_, defaults) = loaded()
        assertEquals(FoodPreferences(), defaults().form.toFoodPreferences())
        assertFalse(defaults().canSave)

        foodPreferences.result = Result.success(FoodPreferences(10.0, 7.5, 20.0))
        val (_, custom) = loaded()

        assertEquals(FoodPreferences(10.0, 7.5, 20.0), custom().form.toFoodPreferences())
        assertEquals("7.5", custom().form.foodPreferences.airFryerText)
    }

    @Test
    fun cooking_aFailedRead_isLoadFailed_andRetryReloads() = runTest {
        foodPreferences.result = Result.failure(AppException(AppError.Network))
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(PreferencesUiState.LoadFailed, viewModel.uiState.value)

        foodPreferences.result = Result.success(FoodPreferences())
        viewModel.onEvent(PreferencesEvent.Retry)
        advanceUntilIdle()

        assertIs<PreferencesUiState.Loaded>(viewModel.uiState.value)
    }

    @Test
    fun cooking_editsMakeTheFormDirty_andSaveSendsThemOnce() = runTest {
        val (viewModel, state) = loaded()

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
        val (viewModel, state) = loaded()

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
        val (viewModel, state) = loaded()

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
        val (viewModel, state) = loaded()

        viewModel.onEvent(PreferencesEvent.SetFatPctGriddle("6"))
        viewModel.onEvent(PreferencesEvent.SetFatPctGriddle("1"))
        advanceUntilIdle()

        assertFalse(state().isDirty)
    }

    @Test
    fun cooking_editedAmountsSurviveARecreatedViewModel_andAnEqualFormClearsTheDraft() = runTest {
        val handle = SavedStateHandle()
        val first = viewModel(handle)
        advanceUntilIdle()
        first.onEvent(PreferencesEvent.SetFatPctAirFryer("12"))
        first.onEvent(PreferencesEvent.SetFatPctFried("abc"))

        val second = viewModel(handle)
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
}
