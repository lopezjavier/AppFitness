package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeNutritionGoalsRepository
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.ActivitySources
import io.github.lopezjavier.appfitness.domain.model.CoachDerivedValues
import io.github.lopezjavier.appfitness.domain.model.DerivedActivityLevel
import io.github.lopezjavier.appfitness.domain.model.DerivedExperienceValue
import io.github.lopezjavier.appfitness.domain.model.DerivedIntake
import io.github.lopezjavier.appfitness.domain.model.DerivedSource
import io.github.lopezjavier.appfitness.domain.model.DerivedTotalExpenditure
import io.github.lopezjavier.appfitness.domain.model.DerivedWeightTrend
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.domain.model.WeekPhase
import io.github.lopezjavier.appfitness.domain.repository.CoachRepository
import io.github.lopezjavier.appfitness.domain.repository.NutritionGoalsRepository
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val TODAY = LocalDate(2026, 10, 19)

internal fun coachValues(
    provisional: Boolean = true,
    reask: Boolean = false,
    level: ActivityLevel = ActivityLevel.Light,
    edited: Boolean = false,
) = CoachDerivedValues(
    asOf = TODAY,
    activityLevel = DerivedActivityLevel(
        value = level,
        estimate = null,
        factor = 1.375,
        source = DerivedSource.Profile,
        provisional = provisional,
        confirmed = !provisional,
        edited = edited,
        reask = reask,
        uncertainty = null,
        sources = ActivitySources(null, 0, null, 0, null),
        missing = emptyList(),
    ),
    totalExpenditure = DerivedTotalExpenditure(
        2400.0, DerivedSource.Formula, false, false, provisional, !provisional, reask, null, emptyList(),
    ),
    weightTrend = DerivedWeightTrend(-0.4, -0.6, -0.2, 6, false, provisional, !provisional, reask, null, emptyList()),
    intake = DerivedIntake(12, 28, 0.43, true),
    experience = DerivedExperienceValue(null, 0, 0, provisional, !provisional, reask, null, emptyList()),
)

internal class FakeCoachRepository(var loaded: Result<CoachDerivedValues> = Result.success(coachValues())) :
    CoachRepository {
    val confirmCalls = mutableListOf<Pair<LocalDate, ActivityLevel?>>()
    var loadCalls = 0
    var confirmResult: (ActivityLevel?) -> Result<CoachDerivedValues> = { level ->
        Result.success(coachValues(provisional = false, level = level ?: ActivityLevel.Light, edited = level != null))
    }
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun derivedValues(today: LocalDate): Result<CoachDerivedValues> {
        loadCalls++
        return loaded
    }

    override suspend fun confirm(today: LocalDate, activityLevel: ActivityLevel?): Result<CoachDerivedValues> {
        confirmCalls += today to activityLevel
        gate?.await()
        return confirmResult(activityLevel)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TrainerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeCoachRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val plans = FakeCoachPlanRepository()

    private val blocks = FakeCoachBlockRepository()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle(), goals: NutritionGoalsRepository? = null) =
        TrainerViewModel(repository, MutableClock(), saved, goals, plans, blocks, weeks)

    private val weeks = FakeCoachWeeksRepository()

    private fun openSavedBlock() {
        plans.current = Result.success(coachPlan(status = PlanStatus.Accepted))
        blocks.current = Result.success(coachBlock(saved = true))
    }

    @Test
    fun withoutAPlan_theBlockSummaryIsNone_andNoBlockIsRead() = runTest(dispatcher) {
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(BlockSummary.None, model.content().block)
        assertTrue(blocks.reads.isEmpty())
    }

    @Test
    fun openPlanWithoutStoredSessions_hasNoBlock() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan())
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(BlockSummary.None, model.content().block)
        assertEquals(listOf("plan-1"), blocks.reads)
    }

    @Test
    fun storedUnsavedBlock_isSummarisedAsProposed_forAProposedOrAcceptedPlan() = runTest(dispatcher) {
        for (status in listOf(PlanStatus.Proposed, PlanStatus.Accepted)) {
            plans.current = Result.success(coachPlan(status = status))
            blocks.current = Result.success(coachBlock())
            val model = viewModel()

            advanceUntilIdle()

            assertEquals(BlockSummary.Proposed(coachBlock()), model.content().block)
        }
    }

    @Test
    fun storedSavedBlock_isSummarisedAsSaved() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan(status = PlanStatus.Accepted))
        blocks.current = Result.success(coachBlock(saved = true))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(BlockSummary.Saved(coachBlock(saved = true)), model.content().block)
    }

    @Test
    fun blockReadFailure_degradesToNone_withoutBlockingThePlan() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan())
        blocks.current = Result.failure(AppException(AppError.Network))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(BlockSummary.None, model.content().block)
        assertEquals(PlanSummary.Proposed(coachPlan()), model.content().plan)
    }

    private fun TrainerViewModel.content() = assertIs<TrainerUiState.Content>(uiState.value)

    @Test
    fun startsLoadingThenShowsContent() = runTest(dispatcher) {
        val model = viewModel()
        assertEquals(TrainerUiState.Loading, model.uiState.value)

        advanceUntilIdle()

        assertEquals(coachValues(), model.content().values)
        assertFalse(model.content().allConfirmed)
    }

    @Test
    fun loadFailure_isError_andRetryRecovers() = runTest(dispatcher) {
        repository.loaded = Result.failure(AppException(AppError.Unknown))
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(TrainerUiState.Error, model.uiState.value)

        repository.loaded = Result.success(coachValues())
        model.onEvent(TrainerEvent.Retry)
        advanceUntilIdle()

        assertIs<TrainerUiState.Content>(model.uiState.value)
    }

    @Test
    fun networkFailure_isOffline() = runTest(dispatcher) {
        repository.loaded = Result.failure(AppException(AppError.Network))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(TrainerUiState.Offline, model.uiState.value)
    }

    @Test
    fun confirmWithoutEdit_passesNullAndTodayAndReplacesTheState() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainerEvent.Confirm)
        advanceUntilIdle()

        assertEquals(listOf<Pair<LocalDate, ActivityLevel?>>(TODAY to null), repository.confirmCalls)
        assertTrue(model.content().allConfirmed)
        assertFalse(model.content().confirming)
    }

    @Test
    fun confirmEmitsConfirmedEffect() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(TrainerEvent.Confirm)
            advanceUntilIdle()
            assertEquals(TrainerEffect.Confirmed, awaitItem())
        }
    }

    @Test
    fun pickLevel_commitsAtOnce_closesTheSheetAndShowsEdited() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainerEvent.OpenEdit)
        assertEquals(ActivityLevel.Light, model.content().editSheet)
        model.onEvent(TrainerEvent.PickLevel(ActivityLevel.Moderate))
        advanceUntilIdle()

        assertEquals(listOf<Pair<LocalDate, ActivityLevel?>>(TODAY to ActivityLevel.Moderate), repository.confirmCalls)
        assertNull(model.content().editSheet)
        assertEquals(ActivityLevel.Moderate, model.content().values.activityLevel.value)
        assertTrue(model.content().values.activityLevel.edited)
    }

    @Test
    fun confirmFailure_keepsStateAndEmitsFailure() = runTest(dispatcher) {
        repository.confirmResult = { _ -> Result.failure<CoachDerivedValues>(AppException(AppError.Unknown)) }
        val model = viewModel()
        advanceUntilIdle()
        val before = model.content().values

        model.effects.test {
            model.onEvent(TrainerEvent.Confirm)
            advanceUntilIdle()
            assertEquals(TrainerEffect.ConfirmFailed(fromSheet = false, offline = false), awaitItem())
        }

        assertEquals(before, model.content().values)
        assertFalse(model.content().confirming)
    }

    @Test
    fun offlineConfirmFailure_flagsOffline_andKeepsTheSheetOpen() = runTest(dispatcher) {
        repository.confirmResult = { _ -> Result.failure<CoachDerivedValues>(AppException(AppError.Network)) }
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(TrainerEvent.OpenEdit)

        model.effects.test {
            model.onEvent(TrainerEvent.PickLevel(ActivityLevel.High))
            advanceUntilIdle()
            assertEquals(TrainerEffect.ConfirmFailed(fromSheet = true, offline = true), awaitItem())
        }

        assertEquals(ActivityLevel.Light, model.content().editSheet)
        assertFalse(model.content().confirming)
    }

    @Test
    fun pickLevel_emitsConfirmed() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(TrainerEvent.OpenEdit)

        model.effects.test {
            model.onEvent(TrainerEvent.PickLevel(ActivityLevel.High))
            advanceUntilIdle()
            assertEquals(TrainerEffect.Confirmed, awaitItem())
        }
    }

    @Test
    fun pickLevel_ofTheAlreadySelectedLevel_stillConfirms() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(TrainerEvent.OpenEdit)

        model.onEvent(TrainerEvent.PickLevel(ActivityLevel.Light))
        advanceUntilIdle()

        assertEquals(listOf<Pair<LocalDate, ActivityLevel?>>(TODAY to ActivityLevel.Light), repository.confirmCalls)
    }

    @Test
    fun secondPickWhileConfirming_isIgnored() = runTest(dispatcher) {
        repository.gate = CompletableDeferred()
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(TrainerEvent.OpenEdit)

        model.onEvent(TrainerEvent.PickLevel(ActivityLevel.High))
        advanceUntilIdle()
        model.onEvent(TrainerEvent.PickLevel(ActivityLevel.VeryHigh))
        repository.gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf<Pair<LocalDate, ActivityLevel?>>(TODAY to ActivityLevel.High), repository.confirmCalls)
    }

    @Test
    fun doubleTapWhileConfirming_isIgnored() = runTest(dispatcher) {
        repository.gate = CompletableDeferred()
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainerEvent.Confirm)
        advanceUntilIdle()
        assertTrue(model.content().confirming)
        model.onEvent(TrainerEvent.Confirm)
        repository.gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repository.confirmCalls.size)
    }

    @Test
    fun reaskedValues_stayProvisionalAfterReload() = runTest(dispatcher) {
        repository.loaded = Result.success(coachValues(provisional = true, reask = true))
        val model = viewModel()
        advanceUntilIdle()

        assertTrue(model.content().values.anyReask)
        assertFalse(model.content().allConfirmed)
    }

    @Test
    fun allConfirmed_isTrueOnlyWhenNothingIsProvisional() = runTest(dispatcher) {
        repository.loaded = Result.success(coachValues(provisional = false))
        val model = viewModel()
        advanceUntilIdle()

        assertTrue(model.content().allConfirmed)
    }

    @Test
    fun closeEdit_closesTheSheetWithoutConfirming() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val model = viewModel(saved)
        advanceUntilIdle()
        model.onEvent(TrainerEvent.OpenEdit)

        model.onEvent(TrainerEvent.CloseEdit)

        assertNull(model.content().editSheet)
        assertTrue(repository.confirmCalls.isEmpty())
        assertNull(viewModel(saved).also { advanceUntilIdle() }.content().editSheet)
    }

    @Test
    fun recreatedFromTheSameSavedState_keepsTheOpenSheet() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val first = viewModel(saved)
        advanceUntilIdle()
        first.onEvent(TrainerEvent.OpenEdit)

        val second = viewModel(saved)
        advanceUntilIdle()

        assertEquals(ActivityLevel.Light, second.content().editSheet)
    }

    @Test
    fun recreatedAfterASuccessfulPick_startsWithTheSheetClosed() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val first = viewModel(saved)
        advanceUntilIdle()
        first.onEvent(TrainerEvent.OpenEdit)
        first.onEvent(TrainerEvent.PickLevel(ActivityLevel.High))
        advanceUntilIdle()

        val second = viewModel(saved)
        advanceUntilIdle()

        assertNull(second.content().editSheet)
    }

    @Test
    fun pickLevelWithoutOpenSheet_isIgnored() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainerEvent.PickLevel(ActivityLevel.High))
        advanceUntilIdle()

        assertTrue(repository.confirmCalls.isEmpty())
        assertNull(model.content().editSheet)
    }

    @Test
    fun content_carriesTheGoalStrategyForTheTrendTone() = runTest(dispatcher) {
        val goals = FakeNutritionGoalsRepository(NutritionGoals(GoalStrategy.Deficit, -300, 2000))
        val model = viewModel(goals = goals)

        advanceUntilIdle()

        assertEquals(GoalStrategy.Deficit, model.content().strategy)
    }

    @Test
    fun content_hasNoStrategyWhenTheGoalsCannotBeRead() = runTest(dispatcher) {
        val goals = FakeNutritionGoalsRepository().apply {
            readResult = Result.failure(AppException(AppError.Network))
        }
        val model = viewModel(goals = goals)

        advanceUntilIdle()

        assertNull(model.content().strategy)
    }

    @Test
    fun content_hasNoStrategyWithoutAGoalsRow() = runTest(dispatcher) {
        val model = viewModel(goals = FakeNutritionGoalsRepository(null))

        advanceUntilIdle()

        assertNull(model.content().strategy)
    }

    @Test
    fun withoutAPlan_theSummaryIsNone_andNoCalorieChangeIsEvaluated() = runTest(dispatcher) {
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(PlanSummary.None, model.content().plan)
        assertNull(model.content().calorieChange)
        assertEquals(0, plans.pendingReads)
    }

    @Test
    fun proposedPlan_isSummarisedAsProposed_withoutEvaluatingCalories() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan())
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(PlanSummary.Proposed(coachPlan()), model.content().plan)
        assertEquals(0, plans.pendingReads)
    }

    @Test
    fun acceptedPlan_isSummarised_andItsPendingCalorieChangeIsShown() = runTest(dispatcher) {
        val accepted = coachPlan(status = PlanStatus.Accepted)
        plans.current = Result.success(accepted)
        plans.pending = Result.success(calorieChange())
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(PlanSummary.Accepted(accepted), model.content().plan)
        assertEquals(calorieChange(), model.content().calorieChange)
    }

    @Test
    fun planReadFailure_degradesToNone_withoutBlockingTheDerivedValues() = runTest(dispatcher) {
        plans.current = Result.failure(AppException(AppError.Network))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(coachValues(), model.content().values)
        assertEquals(PlanSummary.None, model.content().plan)
    }

    @Test
    fun calorieChangeFailure_degradesToNoCard() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan(status = PlanStatus.Accepted))
        plans.pending = Result.failure(AppException(AppError.Unknown))
        val model = viewModel()

        advanceUntilIdle()

        assertIs<PlanSummary.Accepted>(model.content().plan)
        assertNull(model.content().calorieChange)
    }

    @Test
    fun acceptingTheCalorieChange_sendsTheDecision_hidesTheCard_andReloadsThePlan() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan(status = PlanStatus.Accepted))
        plans.pending = Result.success(calorieChange())
        val model = viewModel()
        advanceUntilIdle()
        val readsBefore = plans.currentReads

        model.effects.test {
            model.onEvent(TrainerEvent.DecideCalorieChange(accept = true))
            advanceUntilIdle()
            assertEquals(TrainerEffect.CalorieChangeDecided(accepted = true), awaitItem())
        }

        assertEquals(listOf("change-1" to true), plans.decisions)
        assertNull(model.content().calorieChange)
        assertFalse(model.content().decidingChange)
        assertEquals(readsBefore + 1, plans.currentReads)
    }

    @Test
    fun discardingTheCalorieChange_sendsTheDiscard() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan(status = PlanStatus.Accepted))
        plans.pending = Result.success(calorieChange())
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(TrainerEvent.DecideCalorieChange(accept = false))
            advanceUntilIdle()
            assertEquals(TrainerEffect.CalorieChangeDecided(accepted = false), awaitItem())
        }

        assertEquals(listOf("change-1" to false), plans.decisions)
        assertNull(model.content().calorieChange)
    }

    @Test
    fun failedCalorieDecision_keepsTheCard_andEmitsTheFailure() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan(status = PlanStatus.Accepted))
        plans.pending = Result.success(calorieChange())
        plans.decideResult = Result.failure(AppException(AppError.Network))
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(TrainerEvent.DecideCalorieChange(accept = true))
            advanceUntilIdle()
            assertEquals(TrainerEffect.CalorieChangeFailed(accept = true, offline = true), awaitItem())
        }

        assertEquals(calorieChange(), model.content().calorieChange)
        assertFalse(model.content().decidingChange)
    }

    @Test
    fun decidingWithoutACard_isIgnored() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainerEvent.DecideCalorieChange(accept = true))
        advanceUntilIdle()

        assertTrue(plans.decisions.isEmpty())
    }

    @Test
    fun savedBlock_loadsTheWeeksWithTheirAdherence() = runTest(dispatcher) {
        openSavedBlock()
        weeks.weeksResult = Result.success(listOf(planWeek(1), planWeek(2, WeekPhase.Upcoming, completed = 0)))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(listOf(1, 2), model.content().weeks.map { it.week })
        assertEquals(75, model.content().weeks.first().adherencePct)
        assertEquals(listOf("plan-1"), weeks.weeksReads.map { it.first })
    }

    @Test
    fun withoutASavedBlock_noWeeksAreReadAndNoDeloadIsEvaluated() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan(status = PlanStatus.Accepted))
        blocks.current = Result.success(coachBlock(saved = false))
        weeks.weeksResult = Result.success(listOf(planWeek()))
        weeks.pending = Result.success(deloadProposal())
        val model = viewModel()

        advanceUntilIdle()

        assertTrue(model.content().weeks.isEmpty())
        assertNull(model.content().deload)
        assertTrue(weeks.weeksReads.isEmpty())
        assertEquals(0, weeks.pendingReads)
    }

    @Test
    fun proposedPlanWithSavedBlock_loadsNeitherWeeksNorDeload() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan(status = PlanStatus.Proposed))
        blocks.current = Result.success(coachBlock(saved = true))
        val model = viewModel()

        advanceUntilIdle()

        assertTrue(model.content().weeks.isEmpty())
        assertEquals(0, weeks.pendingReads)
    }

    @Test
    fun pendingDeload_isShownAsTheCard() = runTest(dispatcher) {
        openSavedBlock()
        weeks.pending = Result.success(deloadProposal())
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(deloadProposal(), model.content().deload)
    }

    @Test
    fun noPendingDeload_hasNoCard() = runTest(dispatcher) {
        openSavedBlock()
        val model = viewModel()

        advanceUntilIdle()

        assertNull(model.content().deload)
        assertEquals(1, weeks.pendingReads)
    }

    @Test
    fun weeksAndDeloadReadFailures_neverBlockTheDerivedValues() = runTest(dispatcher) {
        openSavedBlock()
        weeks.weeksResult = Result.failure(AppException(AppError.Network))
        weeks.pending = Result.failure(AppException(AppError.Network))
        val model = viewModel()

        advanceUntilIdle()

        assertTrue(model.content().weeks.isEmpty())
        assertNull(model.content().deload)
        assertEquals(PlanSummary.Accepted(coachPlan(status = PlanStatus.Accepted)), model.content().plan)
    }

    @Test
    fun acceptingTheDeload_sendsTheDecision_clearsTheCard_reloadsTheWeeks_andEmitsTheEffect() = runTest(dispatcher) {
        openSavedBlock()
        weeks.pending = Result.success(deloadProposal())
        val model = viewModel()
        advanceUntilIdle()
        weeks.weeksResult = Result.success(listOf(planWeek(4, WeekPhase.Upcoming, deload = true)))

        model.effects.test {
            model.onEvent(TrainerEvent.DecideDeload(accept = true))
            advanceUntilIdle()
            assertEquals(TrainerEffect.DeloadDecided(accepted = true), awaitItem())
        }

        assertEquals(listOf("deload-1" to true), weeks.decisions)
        assertNull(model.content().deload)
        assertFalse(model.content().decidingDeload)
        assertTrue(model.content().weeks.single().deload)
    }

    @Test
    fun discardingTheDeload_sendsTheDiscard_andLeavesTheWeeksAsTheyWere() = runTest(dispatcher) {
        openSavedBlock()
        weeks.pending = Result.success(deloadProposal())
        weeks.weeksResult = Result.success(listOf(planWeek(4, WeekPhase.Upcoming)))
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(TrainerEvent.DecideDeload(accept = false))
            advanceUntilIdle()
            assertEquals(TrainerEffect.DeloadDecided(accepted = false), awaitItem())
        }

        assertEquals(listOf("deload-1" to false), weeks.decisions)
        assertNull(model.content().deload)
        assertFalse(model.content().weeks.single().deload)
    }

    @Test
    fun failedDeloadDecision_keepsTheCard_andEmitsTheFailure() = runTest(dispatcher) {
        openSavedBlock()
        weeks.pending = Result.success(deloadProposal())
        weeks.decideResult = Result.failure(AppException(AppError.Unknown))
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(TrainerEvent.DecideDeload(accept = true))
            advanceUntilIdle()
            assertEquals(TrainerEffect.DeloadFailed(accept = true, offline = false), awaitItem())
        }

        assertEquals(deloadProposal(), model.content().deload)
        assertFalse(model.content().decidingDeload)
    }

    @Test
    fun offlineDeloadDecision_flagsOffline() = runTest(dispatcher) {
        openSavedBlock()
        weeks.pending = Result.success(deloadProposal())
        weeks.decideResult = Result.failure(AppException(AppError.Network))
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(TrainerEvent.DecideDeload(accept = false))
            advanceUntilIdle()
            assertEquals(TrainerEffect.DeloadFailed(accept = false, offline = true), awaitItem())
        }
    }

    @Test
    fun doubleTapOnTheDeloadCard_sendsOneDecision() = runTest(dispatcher) {
        openSavedBlock()
        weeks.pending = Result.success(deloadProposal())
        weeks.decideGate = CompletableDeferred()
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainerEvent.DecideDeload(accept = true))
        model.onEvent(TrainerEvent.DecideDeload(accept = true))
        advanceUntilIdle()

        assertEquals(1, weeks.decisions.size)
        assertTrue(model.content().decidingDeload)
        weeks.decideGate?.complete(Unit)
    }

    @Test
    fun decidingTheDeloadWithoutACard_isIgnored() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainerEvent.DecideDeload(accept = true))
        advanceUntilIdle()

        assertTrue(weeks.decisions.isEmpty())
    }

    @Test
    fun recreatedViewModel_reloadsWeeksAndPendingDeloadFromTheServer() = runTest(dispatcher) {
        openSavedBlock()
        weeks.weeksResult = Result.success(listOf(planWeek()))
        weeks.pending = Result.success(deloadProposal())
        val saved = SavedStateHandle()
        viewModel(saved)
        advanceUntilIdle()

        val recreated = viewModel(saved)
        advanceUntilIdle()

        assertEquals(deloadProposal(), recreated.content().deload)
        assertEquals(1, recreated.content().weeks.size)
        assertFalse(recreated.content().decidingDeload)
    }
}
