package io.github.lopezjavier.appfitness.feature.onboarding

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeBodyMeasurementRepository
import io.github.lopezjavier.appfitness.app.FakeClock
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeNutritionGoalsRepository
import io.github.lopezjavier.appfitness.app.FakeProfileRepository
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.model.ProfileSummary
import io.github.lopezjavier.appfitness.domain.model.Sex
import io.github.lopezjavier.appfitness.domain.usecase.AdjustmentIntensity
import io.github.lopezjavier.appfitness.domain.usecase.CompleteOnboarding
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** US-07 goals step of the onboarding: TDEE, strategy, adjustment, floor and the final save. */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // One method per Gherkin scenario and rule.
class OnboardingGoalsViewModelTest {
    private val profile = FakeProfileRepository(Result.success(ProfileSummary("Ana", null, false)))
    private val weights = FakeBodyMeasurementRepository()
    private val goals = FakeNutritionGoalsRepository()
    private val connectivity = FakeConnectivityObserver(initial = true)
    private val handle = SavedStateHandle()

    /** Clock fixed on 2026-10-14: a person born on 2000-03-01 is 26. */
    private val born26 = LocalDate(2000, 3, 1)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(savedState: SavedStateHandle = handle) = OnboardingViewModel(
        savedState,
        profile,
        weights,
        goals,
        CompleteOnboarding(goals, profile),
        connectivity,
        FakeClock(),
    )

    private suspend fun TestScope.started(savedState: SavedStateHandle = handle): OnboardingViewModel =
        viewModel(savedState).also { advanceUntilIdle() }

    private fun OnboardingViewModel.send(vararg events: OnboardingEvent) = events.forEach(::onEvent)

    private fun OnboardingViewModel.energy(event: EnergyStrategyEvent) = onEvent(OnboardingEvent.Energy(event))

    private val OnboardingViewModel.state get() = uiState.value

    /** Walks to the goals step with the given biometrics and saves them. */
    private suspend fun TestScope.onGoalsStep(
        sex: Sex = Sex.Male,
        heightCm: Int = 178,
        weightTenths: Int = 742,
        level: ActivityLevel = ActivityLevel.Moderate,
    ): OnboardingViewModel {
        val vm = started()
        vm.send(
            OnboardingEvent.SelectSex(sex),
            OnboardingEvent.Continue,
            OnboardingEvent.SetBirthDate(born26),
            OnboardingEvent.Continue,
            OnboardingEvent.SetHeight(heightCm),
            OnboardingEvent.Continue,
            OnboardingEvent.SetWeight(weightTenths),
            OnboardingEvent.Continue,
            OnboardingEvent.SelectActivity(level),
            OnboardingEvent.Continue,
        )
        advanceUntilIdle()
        return vm
    }

    @Test
    fun goalsStep_showsTheTdeeOfTheBiometricsJustEntered() = runTest {
        val vm = onGoalsStep()

        val energy = vm.state.energy
        assertEquals(OnboardingStep.Goals, vm.state.step)
        assertEquals(2681, energy?.tdeeKcal)
        assertEquals(GoalStrategy.Maintenance, energy?.strategy)
        assertEquals(0, energy?.adjustment)
        assertEquals(2681, energy?.targetKcal)
    }

    @Test
    fun selectingDeficit_setsMinus400AndTheModerateTarget() = runTest {
        val vm = onGoalsStep()

        vm.energy(EnergyStrategyEvent.SelectStrategy(GoalStrategy.Deficit))

        assertEquals(-400, vm.state.adjustment)
        assertEquals(2281, vm.state.energy?.targetKcal)
        assertEquals(AdjustmentIntensity.Moderate, vm.state.energy?.intensity)
    }

    @Test
    fun reselectingTheSameStrategy_keepsTheAdjustment() = runTest {
        val vm = onGoalsStep()
        vm.energy(EnergyStrategyEvent.SelectStrategy(GoalStrategy.Surplus))
        vm.energy(EnergyStrategyEvent.StepUp)
        assertEquals(300, vm.state.adjustment)

        vm.energy(EnergyStrategyEvent.SelectStrategy(GoalStrategy.Surplus))

        assertEquals(300, vm.state.adjustment)
        assertEquals(2981, vm.state.energy?.targetKcal)
    }

    @Test
    fun steppingDownTwice_fromTheDeficitDefault_givesMinus500() = runTest {
        val vm = onGoalsStep()
        vm.energy(EnergyStrategyEvent.SelectStrategy(GoalStrategy.Deficit))

        vm.energy(EnergyStrategyEvent.StepDown)
        vm.energy(EnergyStrategyEvent.StepDown)

        assertEquals(-500, vm.state.adjustment)
        assertEquals(2181, vm.state.energy?.targetKcal)
    }

    @Test
    fun steppingStopsAtTheRangeEnd() = runTest {
        val vm = onGoalsStep()
        repeat(4) { vm.energy(EnergyStrategyEvent.StepUp) }

        assertEquals(100, vm.state.adjustment)
        assertFalse(vm.state.energy?.canStepUp ?: true)
    }

    @Test
    fun femaleBelowTheFloor_isFlooredAndStepDownIsDisabled() = runTest {
        // Female, 165 cm, 50 kg, sedentary, 26 y: 1.2 x (500 + 1031.25 - 130 - 161) = 1488 kcal.
        val vm = onGoalsStep(Sex.Female, heightCm = 165, weightTenths = 500, level = ActivityLevel.Sedentary)
        vm.energy(EnergyStrategyEvent.SelectStrategy(GoalStrategy.Deficit))

        val energy = vm.state.energy
        assertEquals(1488, energy?.tdeeKcal)
        assertEquals(1200, energy?.targetKcal)
        assertTrue(energy?.isFloored == true)
        assertFalse(energy.canStepDown)
        assertTrue(energy.canStepUp)
        vm.energy(EnergyStrategyEvent.StepDown)
        assertEquals(-400, vm.state.adjustment)
    }

    @Test
    fun saveGoals_upsertsTheDefaultSplit_setsTheFlag_andCompletes() = runTest {
        val vm = onGoalsStep()
        vm.energy(EnergyStrategyEvent.SelectStrategy(GoalStrategy.Deficit))

        vm.onEvent(OnboardingEvent.SaveGoals)
        assertEquals(OnboardingPhase.Saving, vm.state.phase)
        advanceUntilIdle()

        assertEquals(listOf(NutritionGoals(GoalStrategy.Deficit, -400, 2281, MacroSplit(30, 45, 25))), goals.upserts)
        assertEquals(1, profile.completedCalls)
        assertEquals(OnboardingPhase.Completed, vm.state.phase)
        assertFalse(vm.state.canGoBack)
    }

    @Test
    fun saveGoalsFailure_keepsTheChoice_andSavingAgainRepeatsTheSave() = runTest {
        val vm = onGoalsStep()
        vm.energy(EnergyStrategyEvent.SelectStrategy(GoalStrategy.Deficit))
        vm.energy(EnergyStrategyEvent.StepDown)
        goals.upsertResult = Result.failure(AppException(AppError.Network))

        vm.onEvent(OnboardingEvent.SaveGoals)
        advanceUntilIdle()

        assertEquals(OnboardingSaveError.Network, vm.state.saveError)
        assertEquals(OnboardingPhase.Content, vm.state.phase)
        assertEquals(GoalStrategy.Deficit, vm.state.strategy)
        assertEquals(-450, vm.state.adjustment)
        assertEquals(0, profile.completedCalls)

        goals.upsertResult = Result.success(Unit)
        vm.onEvent(OnboardingEvent.SaveGoals)
        advanceUntilIdle()

        assertEquals(2, goals.upserts.size)
        assertEquals(1, profile.completedCalls)
        assertEquals(OnboardingPhase.Completed, vm.state.phase)
        assertNull(vm.state.saveError)
    }

    @Test
    fun flagFailure_isASaveError_andTheGoalsStayWritten() = runTest {
        val vm = onGoalsStep()
        profile.completedResult = Result.failure(AppException(AppError.Unknown))

        vm.onEvent(OnboardingEvent.SaveGoals)
        advanceUntilIdle()

        assertEquals(OnboardingSaveError.Other, vm.state.saveError)
        assertEquals(1, goals.upserts.size)
        assertEquals(OnboardingStep.Goals, vm.state.step)
    }

    @Test
    fun offline_disablesSavingGoals_andShowsTheHint() = runTest {
        val vm = onGoalsStep()
        connectivity.state.value = false
        advanceUntilIdle()

        assertFalse(vm.state.canContinue)
        assertTrue(vm.state.showOfflineHint)
        vm.onEvent(OnboardingEvent.SaveGoals)
        advanceUntilIdle()
        assertTrue(goals.upserts.isEmpty())
    }

    @Test
    fun load_prefillsStrategyAndClampsTheAdjustmentOfAnExistingGoalsRow() = runTest {
        goals.row = NutritionGoals(GoalStrategy.Deficit, -1500, 1800)

        val vm = started()

        assertEquals(GoalStrategy.Deficit, vm.state.strategy)
        assertEquals(-1000, vm.state.adjustment)
    }

    @Test
    fun goalsLoadFailure_isALoadError() = runTest {
        goals.readResult = Result.failure(AppException(AppError.Network))

        assertEquals(OnboardingPhase.LoadError, started().state.phase)
    }

    @Test
    fun strategyAndAdjustment_surviveANewViewModelFromTheSameHandle() = runTest {
        val first = onGoalsStep()
        first.energy(EnergyStrategyEvent.SelectStrategy(GoalStrategy.Surplus))
        first.energy(EnergyStrategyEvent.StepUp)

        val restored = started(handle)

        assertEquals(OnboardingStep.Goals, restored.state.step)
        assertEquals(GoalStrategy.Surplus, restored.state.strategy)
        assertEquals(300, restored.state.adjustment)
    }
}
