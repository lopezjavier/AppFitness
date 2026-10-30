package io.github.lopezjavier.appfitness.feature.trainer

import app.cash.turbine.test
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
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
import kotlin.test.assertIs

/** T-177: the Trainer tab reloads silently when it comes back to the foreground. */
@OptIn(ExperimentalCoroutinesApi::class)
class TrainerRefreshTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeCoachRepository()
    private val plans = FakeCoachPlanRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = TrainerViewModel(
        repository,
        MutableClock(),
        plans = plans,
        blocks = FakeCoachBlockRepository(),
        coachWeeks = FakeCoachWeeksRepository(),
    )

    private fun TrainerViewModel.content() = assertIs<TrainerUiState.Content>(uiState.value)

    @Test
    fun refreshAfterAPlanWasProposed_showsThePendingPlan() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(PlanSummary.None, model.content().plan)
        plans.current = Result.success(coachPlan(status = PlanStatus.Proposed))

        model.onEvent(TrainerEvent.Refresh)
        advanceUntilIdle()

        assertEquals(PlanSummary.Proposed(coachPlan(status = PlanStatus.Proposed)), model.content().plan)
    }

    @Test
    fun refreshAfterThePlanWasAccepted_showsTheActivePlan() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan(status = PlanStatus.Proposed))
        val model = viewModel()
        advanceUntilIdle()
        plans.current = Result.success(coachPlan(status = PlanStatus.Accepted))

        model.onEvent(TrainerEvent.Refresh)
        advanceUntilIdle()

        assertEquals(PlanSummary.Accepted(coachPlan(status = PlanStatus.Accepted)), model.content().plan)
    }

    @Test
    fun refresh_neverShowsLoading() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.uiState.test {
            assertIs<TrainerUiState.Content>(awaitItem())
            plans.current = Result.success(coachPlan(status = PlanStatus.Proposed))
            model.onEvent(TrainerEvent.Refresh)
            advanceUntilIdle()
            val next = awaitItem()
            assertIs<TrainerUiState.Content>(next)
            assertIs<PlanSummary.Proposed>(next.plan)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun failedRefresh_keepsThePreviousContent() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan(status = PlanStatus.Proposed))
        val model = viewModel()
        advanceUntilIdle()
        val before = model.content()
        repository.loaded = Result.failure(AppException(AppError.Network))

        model.onEvent(TrainerEvent.Refresh)
        advanceUntilIdle()

        assertEquals(before, model.uiState.value)
    }

    @Test
    fun refresh_keepsTheOpenEditSheet() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(TrainerEvent.OpenEdit)

        model.onEvent(TrainerEvent.Refresh)
        advanceUntilIdle()

        assertEquals(model.content().values.activityLevel.value, model.content().editSheet)
    }

    @Test
    fun overlappingRefreshes_areCoalescedIntoOneFetch() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan(status = PlanStatus.Accepted))
        val model = viewModel()
        advanceUntilIdle()
        val loadsBefore = repository.loadCalls
        val changeReadsBefore = plans.pendingReads

        model.onEvent(TrainerEvent.Refresh)
        model.onEvent(TrainerEvent.Refresh)
        advanceUntilIdle()

        assertEquals(loadsBefore + 1, repository.loadCalls)
        assertEquals(changeReadsBefore + 1, plans.pendingReads)
    }

    @Test
    fun refreshDuringTheInitialLoad_doesNotFetchTwice() = runTest(dispatcher) {
        val model = viewModel()

        model.onEvent(TrainerEvent.Refresh)
        advanceUntilIdle()

        assertEquals(1, repository.loadCalls)
        assertIs<TrainerUiState.Content>(model.uiState.value)
    }
}
