package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
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
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.PhaseReason
import io.github.lopezjavier.appfitness.domain.model.PlanInputKind
import io.github.lopezjavier.appfitness.domain.model.PlanInputStatus
import io.github.lopezjavier.appfitness.domain.model.PlanInputs
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.domain.model.WeekPhase
import io.github.lopezjavier.appfitness.domain.repository.CoachRepository
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

// coachValues(provisional = true) leaves the four derived values unconfirmed.
private const val PROVISIONAL_VALUES = 4

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

    /** The answer of [planInputs] per kind; an empty list when not set. A successful confirm confirms them all. */
    val inputs = mutableMapOf<PlanInputKind, Result<PlanInputs>>()
    val inputsCalls = mutableListOf<PlanInputKind>()

    override suspend fun derivedValues(today: LocalDate): Result<CoachDerivedValues> {
        loadCalls++
        return loaded
    }

    override suspend fun confirm(today: LocalDate, activityLevel: ActivityLevel?): Result<CoachDerivedValues> {
        confirmCalls += today to activityLevel
        gate?.await()
        val result = confirmResult(activityLevel)
        if (result.isSuccess) {
            for ((kind, stored) in inputs.toMap()) {
                stored.getOrNull()?.let { list ->
                    inputs[kind] = Result.success(
                        list.copy(
                            inputs = list.inputs.map {
                                if (it.status ==
                                    PlanInputStatus.Provisional
                                ) {
                                    it.copy(status = PlanInputStatus.Ok)
                                } else {
                                    it
                                }
                            },
                        ),
                    )
                }
            }
        }
        return result
    }

    override suspend fun planInputs(kind: PlanInputKind, today: LocalDate): Result<PlanInputs> {
        inputsCalls += kind
        return inputs[kind] ?: Result.success(PlanInputs(kind, null, emptyList()))
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

    private fun viewModel() = TrainerViewModel(repository, MutableClock(), plans, blocks, weeks)

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
    fun startsLoadingThenShowsTheHub() = runTest(dispatcher) {
        val model = viewModel()
        assertEquals(TrainerUiState.Loading, model.uiState.value)

        advanceUntilIdle()

        assertEquals(PlanSummary.None, model.content().plan)
    }

    @Test
    fun provisionalCount_isTheNumberOfUnconfirmedDerivedValues() = runTest(dispatcher) {
        repository.loaded = Result.success(coachValues(provisional = true))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(PROVISIONAL_VALUES, model.content().provisionalCount)
    }

    @Test
    fun provisionalCount_isZeroWhenEverythingIsConfirmed() = runTest(dispatcher) {
        repository.loaded = Result.success(coachValues(provisional = false))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(0, model.content().provisionalCount)
    }

    @Test
    fun failedDerivedRead_isNotAnErrorScreen_justARowWithoutCount() = runTest(dispatcher) {
        repository.loaded = Result.failure(AppException(AppError.Network))
        plans.current = Result.success(coachPlan(status = PlanStatus.Accepted))
        val model = viewModel()

        advanceUntilIdle()

        assertNull(model.content().provisionalCount)
        assertIs<PlanSummary.Accepted>(model.content().plan)
    }

    @Test
    fun trainerNeverConfirmsAnything() = runTest(dispatcher) {
        val model = viewModel()

        advanceUntilIdle()
        model.onEvent(TrainerEvent.Refresh)
        advanceUntilIdle()

        assertTrue(repository.confirmCalls.isEmpty())
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
    fun acceptedPlan_exposesItsPhaseAndReason_andNoPlanExposesNone() = runTest(dispatcher) {
        plans.current = Result.success(
            coachPlan(status = PlanStatus.Accepted, phase = Phase.Regain, phaseReason = PhaseReason.ReturningBreak),
        )
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(Phase.Regain, model.content().planPhase)
        assertEquals(PhaseReason.ReturningBreak, model.content().planPhaseReason)
        assertNull(TrainerUiState.Content().planPhase)
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
    fun acceptedPlan_appliesTheDietBreakSwitchOnOpen_andAFailureDoesNotBlockTheScreen() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan(status = PlanStatus.Accepted))
        plans.dietBreakResult = Result.failure(IllegalStateException("offline"))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(1, plans.dietBreakCalls.size)
        assertEquals(PlanSummary.Accepted(coachPlan(status = PlanStatus.Accepted)), model.content().plan)
    }

    @Test
    fun proposedPlan_doesNotApplyTheDietBreakSwitch() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan())
        viewModel()

        advanceUntilIdle()

        assertEquals(0, plans.dietBreakCalls.size)
    }

    @Test
    fun planReadFailure_degradesToNone() = runTest(dispatcher) {
        plans.current = Result.failure(AppException(AppError.Network))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(PROVISIONAL_VALUES, model.content().provisionalCount)
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
    fun savedBlock_carriesTheCardioAndStepFiguresOfEachWeek() = runTest(dispatcher) {
        openSavedBlock()
        weeks.weeksResult = Result.success(
            listOf(
                planWeek(1, cardioMinDone = 100, cardioMinTarget = 150, stepsAvg = 7800, stepsTarget = 7000),
                planWeek(2, WeekPhase.Upcoming, completed = 0, cardioMinTarget = 150),
            ),
        )
        val model = viewModel()

        advanceUntilIdle()

        val (first, second) = model.content().weeks
        assertEquals(100, first.cardioMinDone)
        assertEquals(150, first.cardioMinTarget)
        assertEquals(7800, first.stepsAvg)
        assertEquals(7000, first.stepsTarget)
        assertEquals(0, second.cardioMinDone)
        assertNull(second.stepsAvg)
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

        viewModel()
        advanceUntilIdle()

        val recreated = viewModel()
        advanceUntilIdle()

        assertEquals(deloadProposal(), recreated.content().deload)
        assertEquals(1, recreated.content().weeks.size)
        assertFalse(recreated.content().decidingDeload)
    }

    private fun acceptedWithActivity(minutes: Int = 120) {
        plans.current = Result.success(
            coachPlan(status = PlanStatus.Accepted, activity = defaultActivity.copy(cardioMinWeek = minutes)),
        )
    }

    private fun viewModelWith(saved: SavedStateHandle) =
        TrainerViewModel(repository, MutableClock(), plans, blocks, weeks, saved)

    @Test
    fun cardioEdit_opensAtTheSavedMinutesOfTheAcceptedPlan() = runTest(dispatcher) {
        acceptedWithActivity()
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainerEvent.OpenCardioEdit)

        assertEquals(CardioEdit(draft = 120, saved = 120), model.content().cardioEdit)
    }

    @Test
    fun cardioEdit_withoutActivityOrWithAProposedPlan_doesNotOpen() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan(status = PlanStatus.Accepted))
        val withoutActivity = viewModel()
        advanceUntilIdle()
        withoutActivity.onEvent(TrainerEvent.OpenCardioEdit)
        assertNull(withoutActivity.content().cardioEdit)

        plans.current = Result.success(coachPlan(activity = defaultActivity))
        val proposed = viewModel()
        advanceUntilIdle()
        proposed.onEvent(TrainerEvent.OpenCardioEdit)
        assertNull(proposed.content().cardioEdit)
    }

    @Test
    fun cardioEdit_afterAcceptance_savesAndUpdatesThePlanLine() = runTest(dispatcher) {
        acceptedWithActivity(150)
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(TrainerEvent.OpenCardioEdit)
        model.onEvent(TrainerEvent.CardioMinutesChanged(90))
        plans.cardioResult = Result.success(
            coachPlan(status = PlanStatus.Accepted, activity = defaultActivity.copy(cardioMinWeek = 90)),
        )

        model.effects.test {
            model.onEvent(TrainerEvent.SaveCardio)
            advanceUntilIdle()

            assertEquals(TrainerEffect.CardioSaved, awaitItem())
        }

        assertEquals(listOf("plan-1" to 90), plans.cardioCalls)
        val content = model.content()
        assertNull(content.cardioEdit)
        val plan = assertIs<PlanSummary.Accepted>(content.plan).plan
        assertEquals(90, plan.nutrition.activity?.cardioMinWeek)
    }

    @Test
    fun cardioEdit_failure_keepsTheSheetAndTheDraft() = runTest(dispatcher) {
        acceptedWithActivity(150)
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(TrainerEvent.OpenCardioEdit)
        model.onEvent(TrainerEvent.CardioMinutesChanged(90))
        plans.cardioResult = Result.failure(AppException(AppError.Network))

        model.effects.test {
            model.onEvent(TrainerEvent.SaveCardio)
            advanceUntilIdle()

            assertEquals(TrainerEffect.CardioFailed(offline = true), awaitItem())
        }

        assertEquals(CardioEdit(draft = 90, saved = 150), model.content().cardioEdit)
    }

    @Test
    fun cardioEdit_dismiss_closesWithoutSaving() = runTest(dispatcher) {
        acceptedWithActivity()
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(TrainerEvent.OpenCardioEdit)

        model.onEvent(TrainerEvent.DismissCardioEdit)

        assertNull(model.content().cardioEdit)
        assertTrue(plans.cardioCalls.isEmpty())
    }

    @Test
    fun cardioEdit_survivesRecreationAndARefresh() = runTest(dispatcher) {
        acceptedWithActivity(150)
        val saved = SavedStateHandle()
        val first = viewModelWith(saved)
        advanceUntilIdle()
        first.onEvent(TrainerEvent.OpenCardioEdit)
        first.onEvent(TrainerEvent.CardioMinutesChanged(90))

        val second = viewModelWith(saved)
        advanceUntilIdle()
        assertEquals(CardioEdit(draft = 90, saved = 150), second.content().cardioEdit)

        second.onEvent(TrainerEvent.Refresh)
        advanceUntilIdle()
        assertEquals(CardioEdit(draft = 90, saved = 150), second.content().cardioEdit)
    }
}
