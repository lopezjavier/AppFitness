package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CoachDerivedValues
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.MissingField
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
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

@OptIn(ExperimentalCoroutinesApi::class)
class PlanProposalViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val plans = FakeCoachPlanRepository()
    private val coach = FakeCoachRepository(Result.success(coachValues(provisional = false)))

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) =
        PlanProposalViewModel(plans, coach, MutableClock(), saved)

    private fun PlanProposalViewModel.proposal() = assertIs<PlanProposalUiState.Proposal>(uiState.value)

    private fun PlanProposalViewModel.failure() = assertIs<PlanProposalUiState.Failed>(uiState.value).kind

    @Test
    fun withoutAStoredPlan_theScreenStartsIdle() = runTest(dispatcher) {
        val model = viewModel()
        assertEquals(PlanProposalUiState.Loading, model.uiState.value)

        advanceUntilIdle()

        assertEquals(PlanProposalUiState.Idle, model.uiState.value)
        assertTrue(plans.requests.isEmpty())
    }

    @Test
    fun anAcceptedPlanAlone_isNotAProposal() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan(status = PlanStatus.Accepted))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(PlanProposalUiState.Idle, model.uiState.value)
    }

    @Test
    fun aStoredProposedPlan_isShownWithoutRequestingAgain() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan())
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(coachPlan(), model.proposal().plan)
        assertTrue(plans.requests.isEmpty())
    }

    @Test
    fun request_showsProgressThenTheProposal() = runTest(dispatcher) {
        plans.requestGate = CompletableDeferred()
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(PlanProposalEvent.Request)
        advanceUntilIdle()
        assertEquals(PlanProposalUiState.Requesting, model.uiState.value)

        plans.requestGate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(coachPlan(), model.proposal().plan)
        assertEquals(1, plans.requests.size)
        assertEquals(LocalDate(2026, 10, 19), plans.requests.single())
    }

    @Test
    fun secondRequestWhileRequesting_isIgnored() = runTest(dispatcher) {
        plans.requestGate = CompletableDeferred()
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(PlanProposalEvent.Request)
        advanceUntilIdle()
        model.onEvent(PlanProposalEvent.Request)
        plans.requestGate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, plans.requests.size)
    }

    @Test
    fun userLimit_isFailedWithItsScope_andNoProposal() = runTest(dispatcher) {
        plans.request = Result.failure(CoachException(CoachFailure.LimitReached(CoachFailure.Scope.User)))
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(PlanProposalEvent.Request)
        advanceUntilIdle()

        assertEquals(PlanFailureKind.Limit(CoachFailure.Scope.User), model.failure())
    }

    @Test
    fun globalLimit_keepsTheGlobalScope() = runTest(dispatcher) {
        plans.request = Result.failure(CoachException(CoachFailure.LimitReached(CoachFailure.Scope.Global)))
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(PlanProposalEvent.Request)
        advanceUntilIdle()

        assertEquals(PlanFailureKind.Limit(CoachFailure.Scope.Global), model.failure())
    }

    @Test
    fun coachFailures_mapToTheirKinds() = runTest(dispatcher) {
        val cases = mapOf(
            CoachFailure.NoCredit to PlanFailureKind.NoCredit,
            CoachFailure.Timeout to PlanFailureKind.Timeout,
            CoachFailure.Invalid to PlanFailureKind.Invalid,
            CoachFailure.Missing(setOf(MissingField.Sex, MissingField.Weight)) to
                PlanFailureKind.Missing(setOf(MissingField.Sex, MissingField.Weight)),
            CoachFailure.Infeasible to PlanFailureKind.Infeasible,
            CoachFailure.Other to PlanFailureKind.Other,
        )
        for ((failure, kind) in cases) {
            plans.request = Result.failure(CoachException(failure))
            val model = viewModel()
            advanceUntilIdle()
            model.onEvent(PlanProposalEvent.Request)
            advanceUntilIdle()
            assertEquals(kind, model.failure(), failure.toString())
        }
    }

    @Test
    fun networkFailure_isOffline() = runTest(dispatcher) {
        plans.request = Result.failure(AppException(AppError.Network))
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(PlanProposalEvent.Request)
        advanceUntilIdle()

        assertEquals(PlanFailureKind.Offline, model.failure())
    }

    @Test
    fun retryAfterInvalidOutput_requestsAgainAndRecovers() = runTest(dispatcher) {
        plans.request = Result.failure(CoachException(CoachFailure.Invalid))
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(PlanProposalEvent.Request)
        advanceUntilIdle()

        plans.request = Result.success(coachPlan())
        model.onEvent(PlanProposalEvent.Retry)
        advanceUntilIdle()

        assertEquals(2, plans.requests.size)
        assertEquals(coachPlan(), model.proposal().plan)
    }

    @Test
    fun retry_prefersAPlanTheServerStoredMeanwhile() = runTest(dispatcher) {
        plans.request = Result.failure(CoachException(CoachFailure.Timeout))
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(PlanProposalEvent.Request)
        advanceUntilIdle()

        plans.current = Result.success(coachPlan())
        model.onEvent(PlanProposalEvent.Retry)
        advanceUntilIdle()

        assertEquals(1, plans.requests.size)
        assertEquals(coachPlan(), model.proposal().plan)
    }

    @Test
    fun provisionalValues_blockAcceptWithAReason() = runTest(dispatcher) {
        coach.loaded = Result.success(coachValues(provisional = true))
        plans.current = Result.success(coachPlan())
        val model = viewModel()

        advanceUntilIdle()

        assertFalse(model.proposal().canAccept)
        assertEquals(AcceptBlockedReason.Unconfirmed, model.proposal().acceptBlockedReason)
        model.onEvent(PlanProposalEvent.Accept)
        advanceUntilIdle()
        assertTrue(plans.acceptCalls.isEmpty())
    }

    @Test
    fun confirm_enablesAccept() = runTest(dispatcher) {
        coach.loaded = Result.success(coachValues(provisional = true))
        plans.current = Result.success(coachPlan())
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(PlanProposalEvent.Confirm)
            advanceUntilIdle()
            assertEquals(PlanProposalEffect.ValuesConfirmed, awaitItem())
        }

        assertTrue(model.proposal().canAccept)
        assertNull(model.proposal().acceptBlockedReason)
    }

    @Test
    fun accept_writesThePlan_andEmitsAcceptedOnce() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan())
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(PlanProposalEvent.Accept)
            advanceUntilIdle()
            assertEquals(PlanProposalEffect.Accepted, awaitItem())
            expectNoEvents()
        }

        assertEquals(listOf("plan-1" to LocalDate(2026, 10, 19)), plans.acceptCalls)
        assertEquals(PlanProposalUiState.Closed, model.uiState.value)
    }

    @Test
    fun accept_withUnconfirmedValues_emitsNoEffectAndNeverCallsTheRepository() = runTest(dispatcher) {
        coach.loaded = Result.success(coachValues(provisional = true))
        plans.current = Result.success(coachPlan())
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(PlanProposalEvent.Accept)
            advanceUntilIdle()
            expectNoEvents()
        }

        assertTrue(plans.acceptCalls.isEmpty())
        assertIs<PlanProposalUiState.Proposal>(model.uiState.value)
    }

    @Test
    fun accept_afterTheServerReportedChangedData_isBlockedUntilConfirmed() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan())
        val model = viewModel()
        advanceUntilIdle()
        plans.acceptResult = Result.failure(CoachException(CoachFailure.NeedsConfirmation))
        coach.loaded = Result.success(coachValues(provisional = true, reask = true))
        model.onEvent(PlanProposalEvent.Accept)
        advanceUntilIdle()
        assertEquals(1, plans.acceptCalls.size)

        model.effects.test {
            model.onEvent(PlanProposalEvent.Accept)
            advanceUntilIdle()
            expectNoEvents()
        }

        assertEquals(1, plans.acceptCalls.size)
        assertEquals(AcceptBlockedReason.DataChanged, model.proposal().acceptBlockedReason)
    }

    @Test
    fun serverRefusingForUnconfirmedValues_refreshesThemAndKeepsTheProposal() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan())
        val model = viewModel()
        advanceUntilIdle()
        plans.acceptResult = Result.failure(CoachException(CoachFailure.NeedsConfirmation))
        coach.loaded = Result.success(coachValues(provisional = true, reask = true))

        model.onEvent(PlanProposalEvent.Accept)
        advanceUntilIdle()

        val proposal = model.proposal()
        assertFalse(proposal.canAccept)
        assertTrue(proposal.values.anyReask)
        assertEquals(AcceptBlockedReason.DataChanged, proposal.acceptBlockedReason)
        assertFalse(proposal.accepting)
    }

    @Test
    fun acceptFailure_emitsActionFailed_andKeepsTheProposal() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan())
        val model = viewModel()
        advanceUntilIdle()
        plans.acceptResult = Result.failure(AppException(AppError.Network))

        model.effects.test {
            model.onEvent(PlanProposalEvent.Accept)
            advanceUntilIdle()
            assertEquals(PlanProposalEffect.ActionFailed(PlanAction.Accept, offline = true), awaitItem())
        }

        assertFalse(model.proposal().accepting)
        assertTrue(model.proposal().canAccept)
    }

    @Test
    fun discard_closesAndEmitsDiscarded() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan())
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(PlanProposalEvent.Discard)
            advanceUntilIdle()
            assertEquals(PlanProposalEffect.Discarded, awaitItem())
        }

        assertEquals(listOf("plan-1"), plans.discardCalls)
        assertEquals(PlanProposalUiState.Closed, model.uiState.value)
    }

    @Test
    fun discardFailure_emitsActionFailed() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan())
        val model = viewModel()
        advanceUntilIdle()
        plans.discardResult = Result.failure(AppException(AppError.Unknown))

        model.effects.test {
            model.onEvent(PlanProposalEvent.Discard)
            advanceUntilIdle()
            assertEquals(PlanProposalEffect.ActionFailed(PlanAction.Discard, offline = false), awaitItem())
        }

        assertIs<PlanProposalUiState.Proposal>(model.uiState.value)
    }

    @Test
    fun derivedValuesFailureWhileLoadingTheProposal_isFailedAndRetryRecovers() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan())
        coach.loaded = Result.failure<CoachDerivedValues>(AppException(AppError.Network))
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(PlanFailureKind.Offline, model.failure())

        coach.loaded = Result.success(coachValues(provisional = false))
        model.onEvent(PlanProposalEvent.Retry)
        advanceUntilIdle()

        assertIs<PlanProposalUiState.Proposal>(model.uiState.value)
        assertTrue(plans.requests.isEmpty())
    }

    @Test
    fun recreatedMidRequest_withTheStoredPlan_landsOnTheProposal() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        plans.requestGate = CompletableDeferred()
        val first = viewModel(saved)
        advanceUntilIdle()
        first.onEvent(PlanProposalEvent.Request)
        advanceUntilIdle()

        plans.current = Result.success(coachPlan())
        val second = viewModel(saved)
        advanceUntilIdle()

        assertEquals(coachPlan(), second.proposal().plan)
        assertEquals(1, plans.requests.size)
    }

    private fun replacing(id: String?) = SavedStateHandle().apply { if (id != null) set("replacingPlanId", id) }

    @Test
    fun replacingAPendingPlan_ignoresItAndRequestsOnceThenShowsTheNewPlan() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan(id = "old"))
        plans.request = Result.success(coachPlan(id = "new"))
        val model = viewModel(replacing("old"))
        advanceUntilIdle()
        assertEquals(PlanProposalUiState.Idle, model.uiState.value)

        model.onEvent(PlanProposalEvent.Request)
        advanceUntilIdle()

        assertEquals(1, plans.requests.size)
        assertEquals("new", model.proposal().plan.id)
    }

    @Test
    fun replacingAnAcceptedPlan_requestsANewOne() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan(id = "old", status = PlanStatus.Accepted))
        plans.request = Result.success(coachPlan(id = "new"))
        val model = viewModel(replacing("old"))
        advanceUntilIdle()

        model.onEvent(PlanProposalEvent.Request)
        advanceUntilIdle()

        assertEquals("new", model.proposal().plan.id)
    }

    @Test
    fun aProposedPlanWithAnotherId_isStillShownWhileReplacing() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan(id = "other"))
        val model = viewModel(replacing("old"))

        advanceUntilIdle()

        assertEquals("other", model.proposal().plan.id)
        assertTrue(plans.requests.isEmpty())
    }

    @Test
    fun retryWhileReplacing_requestsAgainInsteadOfShowingTheOldPendingPlan() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan(id = "old"))
        plans.request = Result.failure(CoachException(CoachFailure.Timeout))
        val model = viewModel(replacing("old"))
        advanceUntilIdle()
        model.onEvent(PlanProposalEvent.Request)
        advanceUntilIdle()
        assertEquals(PlanFailureKind.Timeout, model.failure())

        plans.request = Result.success(coachPlan(id = "new"))
        model.onEvent(PlanProposalEvent.Retry)
        advanceUntilIdle()

        assertEquals(2, plans.requests.size)
        assertEquals("new", model.proposal().plan.id)
    }

    @Test
    fun recreatedAfterTheReplacementIsStored_showsItAndNeverBillsAgain() = runTest(dispatcher) {
        val saved = replacing("old")
        plans.current = Result.success(coachPlan(id = "old"))
        plans.request = Result.success(coachPlan(id = "new"))
        val first = viewModel(saved)
        advanceUntilIdle()
        first.onEvent(PlanProposalEvent.Request)
        advanceUntilIdle()

        plans.current = Result.success(coachPlan(id = "new"))
        val second = viewModel(saved)
        advanceUntilIdle()

        assertEquals("new", second.proposal().plan.id)
        assertEquals(1, plans.requests.size)
    }

    @Test
    fun recreatedMidRequest_withoutAStoredPlan_offersRetryAndNeverRequestsAgain() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        plans.requestGate = CompletableDeferred()
        val first = viewModel(saved)
        advanceUntilIdle()
        first.onEvent(PlanProposalEvent.Request)
        advanceUntilIdle()

        val second = viewModel(saved)
        advanceUntilIdle()

        assertEquals(PlanFailureKind.Interrupted, second.failure())
        assertEquals(1, plans.requests.size)
    }
}
