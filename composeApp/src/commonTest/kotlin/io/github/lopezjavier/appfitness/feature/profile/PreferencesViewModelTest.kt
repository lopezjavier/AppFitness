package io.github.lopezjavier.appfitness.feature.profile

import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeBodyMeasurementRepository
import io.github.lopezjavier.appfitness.app.FakeClock
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeNutritionGoalsRepository
import io.github.lopezjavier.appfitness.app.FakeProfileRepository
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.Macronutrient
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.model.ProfileSummary
import io.github.lopezjavier.appfitness.domain.model.SavedBiometrics
import io.github.lopezjavier.appfitness.domain.model.Sex
import io.github.lopezjavier.appfitness.domain.usecase.CalculateTdee
import io.github.lopezjavier.appfitness.domain.usecase.SavePreferences
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
@Suppress("TooManyFunctions") // One method per Gherkin scenario and rule.
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

    private fun viewModel() = PreferencesViewModel(
        profileRepository = profile,
        goalsRepository = goals,
        bodyMeasurementRepository = weights,
        savePreferences = SavePreferences(profile),
        connectivity = connectivity,
        clock = FakeClock(today, now),
        timeZone = TimeZone.UTC,
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
    fun theLatestWeightFromAnySource_isShownAndEditable() = runTest {
        weights.row =
            BodyMeasurement("w2", Instant.parse("2026-10-13T08:00:00Z"), 79.4, MeasurementSource.HealthConnect)
        val (viewModel, state) = loaded()
        assertEquals(79.4, state().form.weightKg)
        assertFalse(state().canSave)

        viewModel.onEvent(PreferencesEvent.SetWeight(790))
        viewModel.onEvent(PreferencesEvent.Save)
        advanceUntilIdle()

        assertEquals(79.0, profile.preferenceSaves.single().weight?.weightKg)
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
}
