package io.github.lopezjavier.appfitness.feature.trainer

import app.cash.turbine.test
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CoachPlan
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.PhaseOptions
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlanProposalOptionsTest {
    private val dispatcher = StandardTestDispatcher()
    private val plans = FakeCoachPlanRepository()
    private val coach = FakeCoachRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun proposalOf(model: PlanProposalViewModel) = assertIs<PlanProposalUiState.Proposal>(model.uiState.value)

    private fun TestScope.opened(plan: CoachPlan = coachPlan()): PlanProposalViewModel {
        plans.current = Result.success(plan)
        val model = PlanProposalViewModel(plans, coach, MutableClock())
        advanceUntilIdle()
        return model
    }

    @Test
    fun cutRatePicked_callsUpdateWithCurrentDietBreak_andReplacesThePlan() = runTest(dispatcher) {
        val model = opened(coachPlan(phaseOptions = PhaseOptions(0.5, dietBreak = true)))

        model.onEvent(PlanProposalEvent.CutRatePicked(1.0))
        advanceUntilIdle()

        assertEquals(listOf(Triple("plan-1", 1.0, true)), plans.optionsCalls)
        assertEquals(PhaseOptions(1.0, true), proposalOf(model).plan.nutrition.phaseOptions)
        assertTrue(plans.requests.isEmpty())
        assertFalse(proposalOf(model).optionsSaving)
    }

    @Test
    fun dietBreakToggled_callsUpdateWithCurrentRate_andReplacesThePlan() = runTest(dispatcher) {
        val model = opened(coachPlan(phaseOptions = PhaseOptions(0.75, dietBreak = false)))

        model.onEvent(PlanProposalEvent.DietBreakToggled(true))
        advanceUntilIdle()

        assertEquals(listOf(Triple("plan-1", 0.75, true)), plans.optionsCalls)
        assertEquals(PhaseOptions(0.75, true), proposalOf(model).plan.nutrition.phaseOptions)
        assertTrue(plans.requests.isEmpty())
    }

    @Test
    fun whileSaving_optionsSavingIsTrueAndBusy() = runTest(dispatcher) {
        val model = opened()
        val gate = CompletableDeferred<Unit>()
        plans.optionsGate = gate

        model.onEvent(PlanProposalEvent.CutRatePicked(1.0))
        runCurrent()

        assertTrue(proposalOf(model).optionsSaving)
        assertTrue(proposalOf(model).busy)
        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(proposalOf(model).busy)
    }

    @Test
    fun failure_keepsThePreviousPlan_andEmitsActionFailedOptions() = runTest(dispatcher) {
        val model = opened()
        val before = proposalOf(model).plan
        plans.optionsResult = Result.failure(AppException(AppError.Network))

        model.effects.test {
            model.onEvent(PlanProposalEvent.CutRatePicked(1.0))
            advanceUntilIdle()
            assertEquals(PlanProposalEffect.ActionFailed(PlanAction.Options, offline = true), awaitItem())
        }

        assertEquals(before, proposalOf(model).plan)
        assertFalse(proposalOf(model).optionsSaving)
    }

    @Test
    fun nonCutPhase_ignoresOptionEvents() = runTest(dispatcher) {
        val model = opened(coachPlan(phase = Phase.Gain))

        model.onEvent(PlanProposalEvent.CutRatePicked(1.0))
        model.onEvent(PlanProposalEvent.DietBreakToggled(true))
        advanceUntilIdle()

        assertTrue(plans.optionsCalls.isEmpty())
    }

    @Test
    fun outOfRangeRate_isIgnored() = runTest(dispatcher) {
        val model = opened()

        model.onEvent(PlanProposalEvent.CutRatePicked(1.5))
        advanceUntilIdle()

        assertTrue(plans.optionsCalls.isEmpty())
    }

    @Test
    fun whileBusy_aSecondOptionEventIsIgnored() = runTest(dispatcher) {
        val model = opened()
        val gate = CompletableDeferred<Unit>()
        plans.optionsGate = gate

        model.onEvent(PlanProposalEvent.CutRatePicked(1.0))
        runCurrent()
        model.onEvent(PlanProposalEvent.DietBreakToggled(true))
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, plans.optionsCalls.size)
    }
}
