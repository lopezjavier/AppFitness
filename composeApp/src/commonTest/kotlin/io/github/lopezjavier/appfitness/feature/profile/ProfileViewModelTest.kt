package io.github.lopezjavier.appfitness.feature.profile

import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeAuthRepository
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
import io.github.lopezjavier.appfitness.domain.usecase.SaveProfileSettings
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

/** US-06 / US-07 / US-08 on the Profile screen. */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // One method per Gherkin scenario and rule.
class ProfileViewModelTest {
    private val today = LocalDate(2026, 10, 14)
    private val birthDate = LocalDate(2000, 3, 1)
    private val profile = FakeProfileRepository(Result.success(ProfileSummary("Ana", null, true)))
    private val weights = FakeBodyMeasurementRepository(
        BodyMeasurement("w1", Instant.parse("2026-10-10T08:00:00Z"), 80.0, MeasurementSource.Manual),
    )
    private val goals =
        FakeNutritionGoalsRepository(NutritionGoals(GoalStrategy.Deficit, -400, 2000, MacroSplit.DEFAULT))
    private val auth = FakeAuthRepository()
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

    private fun viewModel() = ProfileViewModel(
        profileRepository = profile,
        goalsRepository = goals,
        bodyMeasurementRepository = weights,
        saveProfileSettings = SaveProfileSettings(profile),
        authRepository = auth,
        connectivity = connectivity,
        clock = FakeClock(today),
    )

    private fun TestScope.loaded(): Pair<ProfileViewModel, () -> ProfileUiState.Loaded> {
        val viewModel = viewModel()
        advanceUntilIdle()
        return viewModel to { assertIs<ProfileUiState.Loaded>(viewModel.uiState.value) }
    }

    private fun tdee(height: Int) = CalculateTdee(Sex.Male, birthDate, height, 80.0, ActivityLevel.Moderate, today)

    @Test
    fun initialDisplay_loadsTheSavedValuesAndTheMacroTexts() = runTest {
        val (_, state) = loaded()

        val s = state()
        assertEquals(178, s.form.heightCm)
        assertEquals(80.0, s.weightKg)
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
        assertEquals(ProfileUiState.LoadFailed, viewModel.uiState.value)

        goals.readResult = null
        viewModel.onEvent(ProfileEvent.Retry)
        advanceUntilIdle()

        assertIs<ProfileUiState.Loaded>(viewModel.uiState.value)
    }

    @Test
    fun missingWeightOrIncompleteBiometrics_isLoadFailed() = runTest {
        weights.row = null
        val noWeight = viewModel()
        advanceUntilIdle()
        assertEquals(ProfileUiState.LoadFailed, noWeight.uiState.value)

        weights.row = BodyMeasurement("w1", Instant.parse("2026-10-10T08:00:00Z"), 80.0, MeasurementSource.Manual)
        profile.biometrics = Result.success(SavedBiometrics(sex = Sex.Male))
        val partial = viewModel()
        advanceUntilIdle()
        assertEquals(ProfileUiState.LoadFailed, partial.uiState.value)
    }

    @Test
    fun movingASlider_autoBalancesAndNeverLeavesTheRange() = runTest {
        val (viewModel, state) = loaded()

        viewModel.onEvent(ProfileEvent.MoveMacro(Macronutrient.Protein, 35))
        advanceUntilIdle()
        assertEquals(MacroSplit(35, 42, 23), state().form.split)

        viewModel.onEvent(ProfileEvent.MoveMacro(Macronutrient.Protein, 99))
        advanceUntilIdle()
        val split = state().form.split
        assertEquals(90, split.proteinPct)
        assertEquals(100, split.proteinPct + split.carbsPct + split.fatPct)
        assertTrue(listOf(split.proteinPct, split.carbsPct, split.fatPct).all { it in 5..90 })
    }

    @Test
    fun editingTheHeight_recalculatesTdeeAndTarget_andSaveWritesBoth() = runTest {
        val (viewModel, state) = loaded()

        viewModel.onEvent(ProfileEvent.SetHeight(180))
        advanceUntilIdle()
        assertEquals(tdee(180), state().energy.tdeeKcal)
        assertTrue(state().canSave)
        assertTrue(profile.settingsSaves.isEmpty(), "nothing is written before Guardar")

        viewModel.onEvent(ProfileEvent.Save)
        advanceUntilIdle()

        val (biometrics, saved) = profile.settingsSaves.single()
        assertEquals(180, biometrics.heightCm)
        assertEquals(tdee(180) - 400, saved.targetKcal)
        assertFalse(state().isDirty)
        assertFalse(state().canSave)
    }

    @Test
    fun strategyChangeAndSplit_areSavedByTheSingleSave() = runTest {
        val (viewModel, _) = loaded()

        viewModel.onEvent(ProfileEvent.Energy(EnergyStrategyEvent.SelectStrategy(GoalStrategy.Surplus)))
        viewModel.onEvent(ProfileEvent.MoveMacro(Macronutrient.Protein, 35))
        viewModel.onEvent(ProfileEvent.Save)
        advanceUntilIdle()

        val saved = profile.settingsSaves.single().second
        assertEquals(GoalStrategy.Surplus, saved.strategy)
        assertEquals(250, saved.calorieAdjustment)
        assertEquals(MacroSplit(35, 42, 23), saved.split)
    }

    @Test
    fun saveSuccess_emitsSaved() = runTest {
        val (viewModel, _) = loaded()
        viewModel.effects.test {
            viewModel.onEvent(ProfileEvent.SelectActivity(ActivityLevel.High))
            viewModel.onEvent(ProfileEvent.Save)
            advanceUntilIdle()
            assertEquals(ProfileEffect.Saved, awaitItem())
        }
    }

    @Test
    fun saveFailure_keepsTheEdits_andEmitsTheError() = runTest {
        profile.settingsResult = Result.failure(AppException(AppError.Unknown))
        val (viewModel, state) = loaded()
        viewModel.effects.test {
            viewModel.onEvent(ProfileEvent.SetHeight(185))
            viewModel.onEvent(ProfileEvent.Save)
            advanceUntilIdle()
            assertEquals(ProfileEffect.SaveFailed(offline = false), awaitItem())
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
            viewModel.onEvent(ProfileEvent.SetHeight(185))
            viewModel.onEvent(ProfileEvent.Save)
            advanceUntilIdle()
            assertEquals(ProfileEffect.SaveFailed(offline = true), awaitItem())
        }
    }

    @Test
    fun offline_disablesSave() = runTest {
        val (viewModel, state) = loaded()
        viewModel.onEvent(ProfileEvent.SetHeight(185))
        connectivity.state.value = false
        advanceUntilIdle()

        assertFalse(state().canSave)
        viewModel.onEvent(ProfileEvent.Save)
        advanceUntilIdle()
        assertTrue(profile.settingsSaves.isEmpty())

        connectivity.state.value = true
        advanceUntilIdle()
        assertTrue(state().canSave)
    }

    @Test
    fun sexChange_isPartOfTheForm() = runTest {
        val (viewModel, state) = loaded()

        viewModel.onEvent(ProfileEvent.SelectSex(Sex.Female))
        advanceUntilIdle()

        assertEquals(Sex.Female, state().form.sex)
        assertTrue(state().isDirty)
    }

    @Test
    fun signOutConfirmed_signsOutOnce() = runTest {
        val viewModel = viewModel()

        viewModel.onEvent(ProfileEvent.SignOutConfirmed)
        advanceUntilIdle()

        assertEquals(1, auth.signOutCalls)
    }
}
