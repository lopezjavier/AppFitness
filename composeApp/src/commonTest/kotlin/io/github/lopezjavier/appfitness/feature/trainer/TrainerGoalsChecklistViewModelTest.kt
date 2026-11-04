package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.app.FakeBodyMeasurementRepository
import io.github.lopezjavier.appfitness.app.FakeNutritionGoalsRepository
import io.github.lopezjavier.appfitness.app.FakeProfileRepository
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.domain.model.SavedBiometrics
import io.github.lopezjavier.appfitness.domain.model.Sex
import io.github.lopezjavier.appfitness.domain.usecase.GoalCheckKey
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** US-72: the N1-N4 checklist is evaluated for the accepted plan's goals only after the user edited them. */
@OptIn(ExperimentalCoroutinesApi::class)
class TrainerGoalsChecklistViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val plans = FakeCoachPlanRepository()
    private val goals = FakeNutritionGoalsRepository()
    private val profile = FakeProfileRepository(Result.failure(AppException(AppError.Unknown)))
    private val weight = FakeBodyMeasurementRepository(
        BodyMeasurement("w", Instant.fromEpochSeconds(0), 80.0, MeasurementSource.Manual),
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        profile.biometrics = Result.success(SavedBiometrics(sex = Sex.Male))
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = TrainerViewModel(
        FakeCoachRepository(),
        MutableClock(),
        plans = plans,
        blocks = FakeCoachBlockRepository(),
        coachWeeks = FakeCoachWeeksRepository(),
        goals = goals,
        profile = profile,
        measurements = weight,
    )

    private fun TrainerViewModel.content() = assertIs<TrainerUiState.Content>(uiState.value)

    private fun userGoals(kcal: Int, split: MacroSplit) = NutritionGoals(GoalStrategy.Deficit, -400, kcal, split)

    @Test
    fun goalsNotEdited_haveNoChecklist() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan(status = PlanStatus.Accepted, editedByUser = false))
        goals.row = userGoals(1000, MacroSplit(10, 70, 20))
        val model = viewModel()
        advanceUntilIdle()

        assertNull(model.content().goalsChecklist)
    }

    @Test
    fun editedGoalsBelowTheMinimums_failTheRows() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan(status = PlanStatus.Accepted, editedByUser = true))
        // 1000 kcal: protein 10 % = 25 g, fat 10 % = 11 g; male floor 1500, 80 kg needs 128 g protein.
        goals.row = userGoals(1000, MacroSplit(10, 80, 10))
        val model = viewModel()
        advanceUntilIdle()

        val checklist = assertNotNull(model.content().goalsChecklist)
        assertFalse(checklist.allPassed)
        assertEquals(
            listOf(GoalCheckKey.N1Floor, GoalCheckKey.N2Rate, GoalCheckKey.N3Protein, GoalCheckKey.N4Fat),
            checklist.checks.filter { !it.passed }.map { it.key },
        )
        assertEquals(25, checklist.checks.first { it.key == GoalCheckKey.N3Protein }.grams)
        assertEquals(11, checklist.checks.first { it.key == GoalCheckKey.N4Fat }.grams)
    }

    @Test
    fun editedGoalsMeetingTheMinimums_passAllRows() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan(status = PlanStatus.Accepted, editedByUser = true))
        // 1960 kcal vs expenditure 2400 (cut, 80 kg: 440 kcal = 0.5 %/week): protein 147 g, fat 54 g.
        goals.row = userGoals(1960, MacroSplit(30, 45, 25))
        val model = viewModel()
        advanceUntilIdle()

        assertEquals(true, assertNotNull(model.content().goalsChecklist).allPassed)
    }

    @Test
    fun missingWeightOrSex_haveNoChecklist() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan(status = PlanStatus.Accepted, editedByUser = true))
        goals.row = userGoals(1000, MacroSplit(10, 80, 10))
        weight.row = null
        val withoutWeight = viewModel()
        advanceUntilIdle()
        assertNull(withoutWeight.content().goalsChecklist)

        weight.row = BodyMeasurement("w", Instant.fromEpochSeconds(0), 80.0, MeasurementSource.Manual)
        profile.biometrics = Result.success(SavedBiometrics())
        val withoutSex = viewModel()
        advanceUntilIdle()
        assertNull(withoutSex.content().goalsChecklist)
    }

    @Test
    fun failedGoalsRead_hasNoChecklistAndStillShowsThePlan() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan(status = PlanStatus.Accepted, editedByUser = true))
        goals.readResult = Result.failure(AppException(AppError.Network))
        val model = viewModel()
        advanceUntilIdle()

        assertNull(model.content().goalsChecklist)
        assertIs<PlanSummary.Accepted>(model.content().plan)
    }
}
