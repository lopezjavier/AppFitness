package io.github.lopezjavier.appfitness.feature.trainer

import app.cash.turbine.test
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.DietBreakWeek
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.PhaseOptions
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
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

class TrainerDietBreakViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val plans = FakeCoachPlanRepository()
    private val repository = FakeCoachRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        TrainerViewModel(repository, MutableClock(), plans, FakeCoachBlockRepository(), FakeCoachWeeksRepository())

    private fun TrainerViewModel.content() = assertIs<TrainerUiState.Content>(uiState.value)

    private fun TrainerViewModel.planOptions() = (content().plan as PlanSummary.Accepted).plan.nutrition.phaseOptions

    private fun acceptedCut(dietBreak: Boolean = false, editedByUser: Boolean = false, phase: Phase = Phase.Cut) =
        coachPlan(
            status = PlanStatus.Accepted,
            phase = phase,
            editedByUser = editedByUser,
            phaseOptions = PhaseOptions(dietBreak = dietBreak),
            dietBreakWeek = if (dietBreak) DietBreakWeek(2400, 160, 280, 70) else null,
        )

    @Test
    fun turningItOn_savesThePlanOption_replacesThePlan_andAppliesTheSwitchOnceMore() = runTest(dispatcher) {
        plans.current = Result.success(acceptedCut())
        val model = viewModel()
        advanceUntilIdle()
        val appliedOnOpen = plans.dietBreakCalls.size

        model.onEvent(TrainerEvent.ToggleDietBreak(true))
        advanceUntilIdle()

        assertEquals(listOf("plan-1" to true), plans.setDietBreakCalls)
        assertTrue(model.planOptions().dietBreak)
        assertEquals(2400, (model.content().plan as PlanSummary.Accepted).plan.nutrition.dietBreak?.targetKcal)
        assertEquals(appliedOnOpen + 1, plans.dietBreakCalls.size)
        assertFalse(model.content().dietBreakSaving)
    }

    @Test
    fun turningItOff_clearsTheWeek4Figures() = runTest(dispatcher) {
        plans.current = Result.success(acceptedCut(dietBreak = true))
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainerEvent.ToggleDietBreak(false))
        advanceUntilIdle()

        assertEquals(listOf("plan-1" to false), plans.setDietBreakCalls)
        assertFalse(model.planOptions().dietBreak)
        assertNull((model.content().plan as PlanSummary.Accepted).plan.nutrition.dietBreak)
    }

    @Test
    fun whileSaving_theSwitchIsPending_andASecondTapIsIgnored() = runTest(dispatcher) {
        plans.current = Result.success(acceptedCut())
        plans.setDietBreakGate = CompletableDeferred()
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainerEvent.ToggleDietBreak(true))
        advanceUntilIdle()
        assertTrue(model.content().dietBreakSaving)
        model.onEvent(TrainerEvent.ToggleDietBreak(false))
        plans.setDietBreakGate?.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, plans.setDietBreakCalls.size)
        assertFalse(model.content().dietBreakSaving)
    }

    @Test
    fun aFailure_snapsTheSwitchBack_keepsThePlan_andEmitsTheFailure() = runTest(dispatcher) {
        val before = acceptedCut()
        plans.current = Result.success(before)
        plans.setDietBreakResult = Result.failure(AppException(AppError.Unknown))
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(TrainerEvent.ToggleDietBreak(true))
            advanceUntilIdle()
            assertEquals(TrainerEffect.DietBreakFailed(offline = false), awaitItem())
        }

        assertEquals(PlanSummary.Accepted(before), model.content().plan)
        assertFalse(model.content().dietBreakSaving)
    }

    @Test
    fun anOfflineFailure_isFlaggedOffline() = runTest(dispatcher) {
        plans.current = Result.success(acceptedCut())
        plans.setDietBreakResult = Result.failure(AppException(AppError.Network))
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(TrainerEvent.ToggleDietBreak(true))
            advanceUntilIdle()
            assertEquals(TrainerEffect.DietBreakFailed(offline = true), awaitItem())
        }
    }

    @Test
    fun success_emitsTheConfirmEffect() = runTest(dispatcher) {
        plans.current = Result.success(acceptedCut())
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(TrainerEvent.ToggleDietBreak(true))
            advanceUntilIdle()
            assertEquals(TrainerEffect.DietBreakSaved, awaitItem())
        }
    }

    @Test
    fun aPlanEditedByTheUser_ignoresTheToggle() = runTest(dispatcher) {
        plans.current = Result.success(acceptedCut(editedByUser = true))
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainerEvent.ToggleDietBreak(true))
        advanceUntilIdle()

        assertTrue(plans.setDietBreakCalls.isEmpty())
    }

    @Test
    fun aPlanThatIsNotACut_ignoresTheToggle() = runTest(dispatcher) {
        plans.current = Result.success(acceptedCut(phase = Phase.Maintenance))
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainerEvent.ToggleDietBreak(true))
        advanceUntilIdle()

        assertTrue(plans.setDietBreakCalls.isEmpty())
    }

    @Test
    fun aProposedPlan_ignoresTheToggle() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan())
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainerEvent.ToggleDietBreak(true))
        advanceUntilIdle()

        assertTrue(plans.setDietBreakCalls.isEmpty())
    }

    @Test
    fun aPlanWithoutBreak_showsNoWeek4Figures() = runTest(dispatcher) {
        plans.current = Result.success(acceptedCut())
        val model = viewModel()
        advanceUntilIdle()

        assertNull((model.content().plan as PlanSummary.Accepted).plan.nutrition.dietBreak)
        assertFalse(model.planOptions().dietBreak)
    }

    @Test
    fun afterProcessDeath_aNewViewModelReadsTheStoredOption() = runTest(dispatcher) {
        plans.current = Result.success(acceptedCut(dietBreak = true))
        val model = viewModel()
        advanceUntilIdle()

        assertTrue(model.planOptions().dietBreak)
    }
}
