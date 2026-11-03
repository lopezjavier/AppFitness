package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TrainerRemoveBlockTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeCoachRepository()
    private val plans = FakeCoachPlanRepository()
    private val blocks = FakeCoachBlockRepository()
    private val weeks = FakeCoachWeeksRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        plans.current = Result.success(coachPlan(status = PlanStatus.Accepted))
        blocks.current = Result.success(coachBlock(saved = true))
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) =
        TrainerViewModel(repository, MutableClock(), plans, blocks, weeks, saved)

    private fun TrainerViewModel.content() = assertIs<TrainerUiState.Content>(uiState.value)

    @Test
    fun request_opensTheDialog_andDismissClosesItWithoutCallingTheRepository() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainerEvent.RequestRemoveBlock)
        assertTrue(model.content().removeBlockConfirm)

        model.onEvent(TrainerEvent.DismissRemoveBlock)
        assertFalse(model.content().removeBlockConfirm)
        assertTrue(blocks.removals.isEmpty())
        assertIs<BlockSummary.Saved>(model.content().block)
    }

    @Test
    fun request_withoutASavedBlock_isIgnored() = runTest(dispatcher) {
        blocks.current = Result.success(coachBlock(saved = false))
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainerEvent.RequestRemoveBlock)

        assertFalse(model.content().removeBlockConfirm)
    }

    @Test
    fun confirm_removesTheBlock_reloadsAndClosesTheDialog() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(TrainerEvent.RequestRemoveBlock)

        model.effects.test {
            model.onEvent(TrainerEvent.ConfirmRemoveBlock)
            advanceUntilIdle()

            assertEquals(TrainerEffect.BlockRemoved, awaitItem())
        }

        assertEquals(listOf("plan-1"), blocks.removals)
        val content = model.content()
        assertEquals(BlockSummary.None, content.block)
        assertTrue(content.weeks.isEmpty())
        assertFalse(content.removeBlockConfirm)
        assertFalse(content.removingBlock)
        assertIs<PlanSummary.Accepted>(content.plan)
    }

    @Test
    fun confirm_withoutOpenDialog_isIgnored() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainerEvent.ConfirmRemoveBlock)
        advanceUntilIdle()

        assertTrue(blocks.removals.isEmpty())
    }

    @Test
    fun doubleConfirm_removesOnce_andFlagsTheRemovalInFlight() = runTest(dispatcher) {
        blocks.removeGate = CompletableDeferred()
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(TrainerEvent.RequestRemoveBlock)

        model.onEvent(TrainerEvent.ConfirmRemoveBlock)
        model.onEvent(TrainerEvent.ConfirmRemoveBlock)
        advanceUntilIdle()

        assertEquals(1, blocks.removals.size)
        assertTrue(model.content().removingBlock)
        assertTrue(model.content().removeBlockConfirm)
        blocks.removeGate?.complete(Unit)
    }

    @Test
    fun dismissWhileRemoving_keepsTheDialogOpen() = runTest(dispatcher) {
        blocks.removeGate = CompletableDeferred()
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(TrainerEvent.RequestRemoveBlock)
        model.onEvent(TrainerEvent.ConfirmRemoveBlock)
        advanceUntilIdle()

        model.onEvent(TrainerEvent.DismissRemoveBlock)

        assertTrue(model.content().removeBlockConfirm)
        blocks.removeGate?.complete(Unit)
    }

    @Test
    fun failure_closesTheDialog_keepsTheBlock_andEmitsTheFailure() = runTest(dispatcher) {
        blocks.removeResult = Result.failure(AppException(AppError.Unknown))
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(TrainerEvent.RequestRemoveBlock)

        model.effects.test {
            model.onEvent(TrainerEvent.ConfirmRemoveBlock)
            advanceUntilIdle()

            assertEquals(TrainerEffect.RemoveBlockFailed(offline = false), awaitItem())
        }

        val content = model.content()
        assertFalse(content.removeBlockConfirm)
        assertFalse(content.removingBlock)
        assertIs<BlockSummary.Saved>(content.block)
    }

    @Test
    fun offlineFailure_flagsOffline() = runTest(dispatcher) {
        blocks.removeResult = Result.failure(AppException(AppError.Network))
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(TrainerEvent.RequestRemoveBlock)

        model.effects.test {
            model.onEvent(TrainerEvent.ConfirmRemoveBlock)
            advanceUntilIdle()

            assertEquals(TrainerEffect.RemoveBlockFailed(offline = true), awaitItem())
        }

        assertIs<BlockSummary.Saved>(model.content().block)
    }

    @Test
    fun openDialog_survivesRecreationAndARefresh() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val first = viewModel(saved)
        advanceUntilIdle()
        first.onEvent(TrainerEvent.RequestRemoveBlock)

        val second = viewModel(saved)
        advanceUntilIdle()
        assertTrue(second.content().removeBlockConfirm)

        second.onEvent(TrainerEvent.Refresh)
        advanceUntilIdle()
        assertTrue(second.content().removeBlockConfirm)

        second.onEvent(TrainerEvent.DismissRemoveBlock)
        assertFalse(viewModel(saved).also { advanceUntilIdle() }.content().removeBlockConfirm)
    }

    @Test
    fun restoredDialog_isClosedWhenThePlanNoLongerHasASavedBlock() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        viewModel(saved).also { advanceUntilIdle() }.onEvent(TrainerEvent.RequestRemoveBlock)
        blocks.current = Result.success(null)

        val recreated = viewModel(saved)
        advanceUntilIdle()

        assertFalse(recreated.content().removeBlockConfirm)
    }
}
