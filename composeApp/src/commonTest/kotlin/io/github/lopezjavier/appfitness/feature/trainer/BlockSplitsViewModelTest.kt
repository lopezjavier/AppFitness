package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BlockSplitsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val blocks = FakeCoachBlockRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) =
        BlockSplitsViewModel("plan-1", blocks, MutableClock(), saved)

    private fun BlockSplitsViewModel.options() = assertIs<BlockSplitsUiState.Options>(uiState.value)

    private suspend fun BlockSplitsViewModel.start() {
        onEvent(BlockSplitsEvent.Request)
        dispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun request_loadsTheOptionsAndPreselectsTheRecommendedOne() = runTest(dispatcher) {
        val model = viewModel()
        assertEquals(BlockSplitsUiState.Idle, model.uiState.value)

        model.start()

        assertEquals("b", model.options().selectedId)
        assertEquals(listOf("a", "b"), model.options().options.map { it.id })
        assertEquals(1, blocks.splitRequests)
    }

    @Test
    fun request_showsProgressWhileTheOptionsAreAsked() = runTest(dispatcher) {
        val model = viewModel()

        model.onEvent(BlockSplitsEvent.Request)

        assertEquals(BlockSplitsUiState.Requesting, model.uiState.value)
    }

    @Test
    fun request_isIgnoredOnceAskedSoItNeverBillsTwice() = runTest(dispatcher) {
        val model = viewModel()
        model.start()

        model.onEvent(BlockSplitsEvent.Request)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, blocks.splitRequests)
    }

    @Test
    fun pick_changesTheSelectionAndIgnoresUnknownIds() = runTest(dispatcher) {
        val model = viewModel()
        model.start()

        model.onEvent(BlockSplitsEvent.Pick("a"))
        assertEquals("a", model.options().selectedId)
        model.onEvent(BlockSplitsEvent.Pick("zzz"))

        assertEquals("a", model.options().selectedId)
    }

    @Test
    fun create_buildsTheBlockWithThePickedSplitAndEmitsBuilt() = runTest(dispatcher) {
        val model = viewModel()
        model.start()
        model.onEvent(BlockSplitsEvent.Pick("a"))

        model.effects.test {
            model.onEvent(BlockSplitsEvent.Create)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(BlockSplitsEffect.Built, awaitItem())
        }

        assertEquals(listOf("a"), blocks.splitsSent.map { it.id })
        assertEquals("plan-1", blocks.requests.single().first)
    }

    @Test
    fun create_usesTheUtcDateOfTheClock() = runTest(dispatcher) {
        val model = viewModel()
        model.start()

        model.onEvent(BlockSplitsEvent.Create)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(LocalDate(2026, 10, 19), blocks.requests.single().second)
    }

    @Test
    fun create_marksTheOptionsAsBuildingAndIgnoresASecondCreate() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        blocks.requestGate = gate
        val model = viewModel()
        model.start()

        model.onEvent(BlockSplitsEvent.Create)
        dispatcher.scheduler.advanceUntilIdle()
        model.onEvent(BlockSplitsEvent.Create)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(model.options().building)
        gate.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, blocks.requests.size)
    }

    @Test
    fun create_cannotChangeThePickWhileBuilding() = runTest(dispatcher) {
        blocks.requestGate = CompletableDeferred()
        val model = viewModel()
        model.start()
        model.onEvent(BlockSplitsEvent.Create)
        dispatcher.scheduler.advanceUntilIdle()

        model.onEvent(BlockSplitsEvent.Pick("a"))

        assertEquals("b", model.options().selectedId)
    }

    @Test
    fun create_failuresShowTheirKindAndRetryKeepsTheSameChoiceWithoutAskingOptionsAgain() = runTest(dispatcher) {
        val failures = listOf(
            CoachException(CoachFailure.LimitReached(CoachFailure.Scope.User)) to
                PlanFailureKind.Limit(CoachFailure.Scope.User),
            AppException(AppError.Network) to PlanFailureKind.Offline,
            CoachException(CoachFailure.Invalid) to PlanFailureKind.Invalid,
        )
        failures.forEach { (failure, kind) ->
            blocks.requests.clear()
            blocks.splitsSent.clear()
            blocks.splitRequests = 0
            blocks.request = Result.failure(failure)
            val model = viewModel()
            model.start()
            model.onEvent(BlockSplitsEvent.Pick("a"))

            model.onEvent(BlockSplitsEvent.Create)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(BlockSplitsUiState.Failed(kind, BlockSplitsStage.Build), model.uiState.value)

            model.onEvent(BlockSplitsEvent.Retry)
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(listOf("a", "a"), blocks.splitsSent.map { it.id })
            assertEquals(1, blocks.splitRequests)
        }
    }

    @Test
    fun retryAfterABuildFailureCanSucceedWithTheKeptChoice() = runTest(dispatcher) {
        blocks.request = Result.failure(CoachException(CoachFailure.Invalid))
        val model = viewModel()
        model.start()
        model.onEvent(BlockSplitsEvent.Pick("a"))
        model.onEvent(BlockSplitsEvent.Create)
        dispatcher.scheduler.advanceUntilIdle()
        blocks.request = Result.success(coachBlock())

        model.effects.test {
            model.onEvent(BlockSplitsEvent.Retry)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(BlockSplitsEffect.Built, awaitItem())
        }
    }

    @Test
    fun optionsFailure_retryAsksTheOptionsAgain() = runTest(dispatcher) {
        blocks.splitOptions = Result.failure(AppException(AppError.Network))
        val model = viewModel()
        model.start()
        assertEquals(BlockSplitsUiState.Failed(PlanFailureKind.Offline, BlockSplitsStage.Options), model.uiState.value)
        blocks.splitOptions = FakeCoachBlockRepository().splitOptions

        model.onEvent(BlockSplitsEvent.Retry)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("b", model.options().selectedId)
        assertEquals(2, blocks.splitRequests)
    }

    @Test
    fun leavingStoresNothing() = runTest(dispatcher) {
        val model = viewModel()
        model.start()
        model.onEvent(BlockSplitsEvent.Pick("a"))

        assertTrue(blocks.saves.isEmpty())
        assertTrue(blocks.requests.isEmpty())
    }

    @Test
    fun recreatedModel_showsTheKeptOptionsAndChoiceWithoutAskingAgain() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val first = viewModel(saved)
        first.start()
        first.onEvent(BlockSplitsEvent.Pick("a"))

        val second = viewModel(saved)
        dispatcher.scheduler.advanceUntilIdle()
        second.onEvent(BlockSplitsEvent.Request)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("a", second.options().selectedId)
        assertEquals(listOf("a", "b"), second.options().options.map { it.id })
        assertEquals(listOf("Torso"), second.options().options.last().sessions.map { it.name })
        assertEquals(1, blocks.splitRequests)
    }

    @Test
    fun recreatedWhileBuilding_withAStoredBlockNavigatesToIt() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        blocks.requestGate = CompletableDeferred()
        val first = viewModel(saved)
        first.start()
        first.onEvent(BlockSplitsEvent.Create)
        dispatcher.scheduler.advanceUntilIdle()
        blocks.current = Result.success(coachBlock())

        val second = viewModel(saved)
        second.effects.test {
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(BlockSplitsEffect.Built, awaitItem())
        }

        assertEquals(1, blocks.requests.size)
    }

    @Test
    fun recreatedWhileBuilding_withoutABlockShowsInterruptedAndNeverAsksByItself() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        blocks.requestGate = CompletableDeferred()
        val first = viewModel(saved)
        first.start()
        first.onEvent(BlockSplitsEvent.Pick("a"))
        first.onEvent(BlockSplitsEvent.Create)
        dispatcher.scheduler.advanceUntilIdle()
        blocks.requestGate = null

        val second = viewModel(saved)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            BlockSplitsUiState.Failed(PlanFailureKind.Interrupted, BlockSplitsStage.Build),
            second.uiState.value,
        )
        assertEquals(1, blocks.requests.size)
        assertEquals(1, blocks.splitRequests)

        second.onEvent(BlockSplitsEvent.Retry)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("a", "a"), blocks.splitsSent.map { it.id })
    }
}
