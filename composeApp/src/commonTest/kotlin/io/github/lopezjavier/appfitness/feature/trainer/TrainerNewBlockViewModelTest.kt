package io.github.lopezjavier.appfitness.feature.trainer

import app.cash.turbine.test
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.PhaseWeeks
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.domain.usecase.LoadWeeksInPhaseUseCase
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** T-240: "Bloque nuevo" entry, "Mi entrenamiento", weeks in the phase and the goal-switch proposal on the hub. */
@OptIn(ExperimentalCoroutinesApi::class)
class TrainerNewBlockViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val plans = FakeCoachPlanRepository()
    private val blocks = FakeCoachBlockRepository()
    private val reviews = FakeCoachReviewRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = TrainerViewModel(
        FakeCoachRepository(),
        MutableClock(),
        plans,
        blocks,
        loadWeeksInPhase = LoadWeeksInPhaseUseCase(reviews),
    )

    private fun TrainerViewModel.content() = assertIs<TrainerUiState.Content>(uiState.value)

    private fun openSavedBlock() {
        plans.current = Result.success(coachPlan(status = PlanStatus.Accepted))
        blocks.current = Result.success(coachBlock(saved = true))
    }

    @Test
    fun acceptedPlanWithSavedBlock_offersANewBlock() = runTest(dispatcher) {
        openSavedBlock()
        val model = viewModel()

        advanceUntilIdle()

        assertTrue(model.content().canStartNewBlock)
    }

    @Test
    fun withoutASavedBlock_noNewBlockIsOffered() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan(status = PlanStatus.Accepted))
        blocks.current = Result.success(coachBlock(saved = false))
        val model = viewModel()

        advanceUntilIdle()

        assertFalse(model.content().canStartNewBlock)
    }

    @Test
    fun aProposedPlan_neverOffersANewBlock() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan(status = PlanStatus.Proposed))
        blocks.current = Result.success(coachBlock(saved = true))
        val model = viewModel()

        advanceUntilIdle()

        assertFalse(model.content().canStartNewBlock)
    }

    @Test
    fun aGoalSwitchProposal_keepsTheAcceptedPlanAndTheBlock() = runTest(dispatcher) {
        val accepted = coachPlan(id = "plan-1", status = PlanStatus.Accepted)
        plans.current = Result.success(coachPlan(id = "plan-2", fromGoalSwitch = true))
        plans.accepted = Result.success(accepted)
        blocks.current = Result.success(coachBlock(saved = true))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(PlanSummary.Accepted(accepted), model.content().plan)
        assertTrue(model.content().canStartNewBlock)
        assertEquals(listOf("plan-1"), blocks.reads)
    }

    @Test
    fun aGoalSwitchProposalWithoutAnAcceptedPlan_isNotShownAsAProposal() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan(id = "plan-2", fromGoalSwitch = true))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(PlanSummary.None, model.content().plan)
    }

    @Test
    fun anOrdinaryProposal_staysProposed() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan(status = PlanStatus.Proposed))
        val model = viewModel()

        advanceUntilIdle()

        assertIs<PlanSummary.Proposed>(model.content().plan)
    }

    @Test
    fun weeksInPhase_isLoaded() = runTest(dispatcher) {
        openSavedBlock()
        reviews.weeks = Result.success(PhaseWeeks(Phase.Cut, 6))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(PhaseWeeks(Phase.Cut, 6), model.content().weeksInPhase)
    }

    @Test
    fun aFailedWeeksRead_isNull_andTheHubStaysUsable() = runTest(dispatcher) {
        openSavedBlock()
        reviews.weeks = Result.failure(AppException(AppError.Network))
        val model = viewModel()

        advanceUntilIdle()

        assertNull(model.content().weeksInPhase)
        assertIs<PlanSummary.Accepted>(model.content().plan)
    }

    @Test
    fun withoutAPlan_weeksAreNotRead() = runTest(dispatcher) {
        val model = viewModel()

        advanceUntilIdle()

        assertNull(model.content().weeksInPhase)
        assertEquals(0, reviews.weeksCalls)
    }

    @Test
    fun openNewBlock_asksToOpenItForTheAcceptedPlan() = runTest(dispatcher) {
        openSavedBlock()
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(TrainerEvent.OpenNewBlock)
            advanceUntilIdle()

            assertEquals(TrainerEffect.OpenNewBlock("plan-1"), awaitItem())
        }
    }

    @Test
    fun openNewBlock_isIgnoredWithoutASavedBlock() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan(status = PlanStatus.Accepted))
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(TrainerEvent.OpenNewBlock)
            advanceUntilIdle()

            expectNoEvents()
        }
    }

    @Test
    fun openMyTraining_asksToOpenTheScreen() = runTest(dispatcher) {
        openSavedBlock()
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(TrainerEvent.OpenMyTraining)
            advanceUntilIdle()

            assertEquals(TrainerEffect.OpenMyTraining, awaitItem())
        }
    }
}
