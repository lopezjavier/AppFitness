package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.PlanInput
import io.github.lopezjavier.appfitness.domain.model.PlanInputKey
import io.github.lopezjavier.appfitness.domain.model.PlanInputKind
import io.github.lopezjavier.appfitness.domain.model.PlanInputSource
import io.github.lopezjavier.appfitness.domain.model.PlanInputStatus
import io.github.lopezjavier.appfitness.domain.model.PlanInputValue
import io.github.lopezjavier.appfitness.domain.model.PlanInputs
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.CompletableDeferred
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

/**
 * T-245 (US-68, R-59): the proposal lists only the values the server refuses the plan for, lets the user confirm or
 * fix them and accept without creating the plan again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlanProposalUnconfirmedTest {
    private val dispatcher = StandardTestDispatcher()
    private val plans = FakeCoachPlanRepository()
    private val coach = FakeCoachRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) =
        PlanProposalViewModel(plans, coach, MutableClock(), saved)

    private fun PlanProposalViewModel.proposal() = assertIs<PlanProposalUiState.Proposal>(uiState.value)

    private fun givenPlanInputs(vararg inputs: PlanInput) {
        coach.inputs[PlanInputKind.Plan] = Result.success(PlanInputs(PlanInputKind.Plan, null, inputs.toList()))
    }

    private fun estimate(key: PlanInputKey, status: PlanInputStatus, blocksAccept: Boolean, value: String = "x") =
        planInput(
            key,
            status,
            value = PlanInputValue.Text(value),
            source = PlanInputSource.CoachEstimate,
            blocksAccept = blocksAccept,
        )

    private fun givenActivity(status: PlanInputStatus, level: String = "moderate") =
        givenPlanInputs(estimate(PlanInputKey.ActivityLevel, status, blocksAccept = true, value = level))

    /** A fresh account: diary and trend provisional (the server never blocks on them), activity confirmed. */
    private fun givenFreshAccount() = givenPlanInputs(
        estimate(PlanInputKey.ActivityLevel, PlanInputStatus.Ok, blocksAccept = false, value = "moderate"),
        estimate(PlanInputKey.TotalExpenditure, PlanInputStatus.Ok, blocksAccept = false),
        planInput(
            PlanInputKey.WeightTrend,
            PlanInputStatus.Provisional,
            value = PlanInputValue.Absent,
            source = PlanInputSource.CoachEstimate,
        ),
        estimate(PlanInputKey.Diary, PlanInputStatus.Provisional, blocksAccept = false),
        estimate(PlanInputKey.Experience, PlanInputStatus.Ok, blocksAccept = false),
    )

    @Test
    fun freshAccount_withProvisionalDiaryAndNoTrend_canAcceptWithoutAnyNote() = runTest(dispatcher) {
        givenFreshAccount()
        plans.current = Result.success(coachPlan())
        val model = viewModel()

        advanceUntilIdle()

        assertTrue(model.proposal().unconfirmedValues.isEmpty())
        assertTrue(model.proposal().canAccept)
        assertNull(model.proposal().acceptBlockedReason)
    }

    @Test
    fun provisionalActivityLevel_isTheOnlyListedValue_andConfirmingUnlocksAcceptWithoutANewPlan() =
        runTest(dispatcher) {
            givenPlanInputs(
                estimate(
                    PlanInputKey.ActivityLevel,
                    PlanInputStatus.Provisional,
                    blocksAccept = true,
                    value = "moderate",
                ),
                estimate(PlanInputKey.Diary, PlanInputStatus.Provisional, blocksAccept = false),
            )
            plans.current = Result.success(coachPlan())
            val model = viewModel()
            advanceUntilIdle()
            assertEquals(listOf(PlanInputKey.ActivityLevel), model.proposal().unconfirmedValues.map { it.key })
            assertFalse(model.proposal().canAccept)

            model.effects.test {
                model.onEvent(PlanProposalEvent.ConfirmValues)
                advanceUntilIdle()
                assertEquals(PlanProposalEffect.ValuesConfirmed, awaitItem())
            }
            assertTrue(model.proposal().unconfirmedValues.isEmpty())
            model.onEvent(PlanProposalEvent.Accept)
            advanceUntilIdle()

            assertEquals(1, coach.confirmCalls.size)
            assertNull(coach.confirmCalls.single().second)
            assertEquals(1, plans.acceptCalls.size)
            assertTrue(plans.requests.isEmpty())
        }

    @Test
    fun provisionalExperience_isNamedInTheList() = runTest(dispatcher) {
        givenPlanInputs(estimate(PlanInputKey.Experience, PlanInputStatus.Provisional, blocksAccept = true))
        plans.current = Result.success(coachPlan())
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(listOf(PlanInputKey.Experience), model.proposal().unconfirmedValues.map { it.key })
        assertEquals(AcceptBlockedReason.Unconfirmed, model.proposal().acceptBlockedReason)
    }

    @Test
    fun serverRefusal_withTheReReadFailing_keepsADataChangedBlockWithAnEmptyList() = runTest(dispatcher) {
        givenFreshAccount()
        plans.current = Result.success(coachPlan())
        val model = viewModel()
        advanceUntilIdle()
        plans.acceptResult = Result.failure(CoachException(CoachFailure.NeedsConfirmation))
        coach.inputs[PlanInputKind.Plan] = Result.failure(AppException(AppError.Network))

        model.onEvent(PlanProposalEvent.Accept)
        advanceUntilIdle()

        val proposal = model.proposal()
        assertTrue(proposal.dataChanged)
        assertTrue(proposal.unconfirmedValues.isEmpty())
        assertFalse(proposal.canAccept)
        assertEquals(AcceptBlockedReason.DataChanged, proposal.acceptBlockedReason)
    }

    @Test
    fun serverRefusal_listsTheValuesOfTheReRead() = runTest(dispatcher) {
        givenFreshAccount()
        plans.current = Result.success(coachPlan())
        val model = viewModel()
        advanceUntilIdle()
        plans.acceptResult = Result.failure(CoachException(CoachFailure.NeedsConfirmation))
        givenPlanInputs(estimate(PlanInputKey.Experience, PlanInputStatus.Provisional, blocksAccept = true))

        model.onEvent(PlanProposalEvent.Accept)
        advanceUntilIdle()

        assertEquals(listOf(PlanInputKey.Experience), model.proposal().unconfirmedValues.map { it.key })
        assertTrue(model.proposal().dataChanged)
    }

    @Test
    fun refresh_afterAnEditor_updatesTheListWithoutRequestingAPlan() = runTest(dispatcher) {
        givenActivity(PlanInputStatus.Provisional)
        plans.current = Result.success(coachPlan())
        val model = viewModel()
        advanceUntilIdle()
        assertTrue(model.proposal().unconfirmed)

        givenActivity(PlanInputStatus.Ok)
        model.onEvent(PlanProposalEvent.Refresh)
        advanceUntilIdle()

        assertTrue(model.proposal().unconfirmedValues.isEmpty())
        assertTrue(model.proposal().canAccept)
        assertTrue(plans.requests.isEmpty())
    }

    @Test
    fun refresh_clearsADataChangedBlockWhenNothingBlocksAnymore() = runTest(dispatcher) {
        givenFreshAccount()
        plans.current = Result.success(coachPlan())
        val model = viewModel()
        advanceUntilIdle()
        plans.acceptResult = Result.failure(CoachException(CoachFailure.NeedsConfirmation))
        model.onEvent(PlanProposalEvent.Accept)
        advanceUntilIdle()
        assertTrue(model.proposal().dataChanged)

        model.onEvent(PlanProposalEvent.Refresh)
        advanceUntilIdle()

        assertFalse(model.proposal().dataChanged)
        assertTrue(model.proposal().canAccept)
    }

    @Test
    fun refreshAndConfirmValues_whileBusy_areIgnored() = runTest(dispatcher) {
        givenActivity(PlanInputStatus.Provisional)
        plans.current = Result.success(coachPlan())
        val model = viewModel()
        advanceUntilIdle()
        coach.gate = CompletableDeferred()
        model.onEvent(PlanProposalEvent.ConfirmValues)
        advanceUntilIdle()
        assertTrue(model.proposal().confirming)
        val readsBefore = coach.inputsCalls.size

        model.onEvent(PlanProposalEvent.Refresh)
        model.onEvent(PlanProposalEvent.ConfirmValues)
        advanceUntilIdle()

        assertEquals(1, coach.confirmCalls.size)
        assertEquals(readsBefore, coach.inputsCalls.size)
        coach.gate!!.complete(Unit)
        advanceUntilIdle()
        assertFalse(model.proposal().confirming)
    }

    @Test
    fun confirmValues_offline_emitsActionFailed_andKeepsTheList() = runTest(dispatcher) {
        givenActivity(PlanInputStatus.Provisional)
        plans.current = Result.success(coachPlan())
        val model = viewModel()
        advanceUntilIdle()
        coach.confirmResult = { Result.failure(AppException(AppError.Network)) }

        model.effects.test {
            model.onEvent(PlanProposalEvent.ConfirmValues)
            advanceUntilIdle()
            assertEquals(PlanProposalEffect.ActionFailed(PlanAction.Confirm, offline = true), awaitItem())
        }

        assertEquals(listOf(PlanInputKey.ActivityLevel), model.proposal().unconfirmedValues.map { it.key })
        assertFalse(model.proposal().confirming)
    }

    @Test
    fun activityLevelChangedAfterThePlan_makesItStale_andBlocksAccept() = runTest(dispatcher) {
        givenActivity(PlanInputStatus.Ok)
        plans.current = Result.success(coachPlan())
        val model = viewModel()
        advanceUntilIdle()
        assertFalse(model.proposal().stale)

        givenActivity(PlanInputStatus.Ok, level = "high")
        model.onEvent(PlanProposalEvent.Refresh)
        advanceUntilIdle()

        val proposal = model.proposal()
        assertTrue(proposal.stale)
        assertFalse(proposal.canAccept)
        assertEquals(AcceptBlockedReason.Stale, proposal.acceptBlockedReason)
        model.onEvent(PlanProposalEvent.Accept)
        advanceUntilIdle()
        assertTrue(plans.acceptCalls.isEmpty())
    }

    @Test
    fun recreated_reReadsTheValues_andKeepsTheStaleBaseline() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        givenActivity(PlanInputStatus.Provisional)
        plans.current = Result.success(coachPlan())
        viewModel(saved)
        advanceUntilIdle()

        givenActivity(PlanInputStatus.Provisional, level = "high")
        val second = viewModel(saved)
        advanceUntilIdle()

        assertEquals(listOf(PlanInputKey.ActivityLevel), second.proposal().unconfirmedValues.map { it.key })
        assertTrue(second.proposal().stale)
    }
}
