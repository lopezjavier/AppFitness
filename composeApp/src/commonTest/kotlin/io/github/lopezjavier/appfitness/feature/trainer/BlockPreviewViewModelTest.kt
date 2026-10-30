package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
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
class BlockPreviewViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val blocks = FakeCoachBlockRepository()
    private val exercises = FakeExerciseRepository(listOf(exerciseRow("ex-1", "bench"), exerciseRow("ex-2", "row")))
    private val library = FakeExerciseLibrary(
        mapOf("bench" to exerciseInfo("bench", "Press banca"), "row" to exerciseInfo("row", "Remo")),
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) =
        BlockPreviewViewModel("plan-1", blocks, exercises, library, MutableClock(), saved)

    private fun BlockPreviewViewModel.preview() = assertIs<BlockPreviewUiState.Preview>(uiState.value)

    private fun BlockPreviewViewModel.failure() = assertIs<BlockPreviewUiState.Failed>(uiState.value).kind

    @Test
    fun withoutAStoredBlock_theScreenStartsIdle_andDoesNotRequest() = runTest(dispatcher) {
        val model = viewModel()
        assertEquals(BlockPreviewUiState.Loading, model.uiState.value)

        advanceUntilIdle()

        assertEquals(BlockPreviewUiState.Idle, model.uiState.value)
        assertTrue(blocks.requests.isEmpty())
    }

    @Test
    fun aStoredBlock_isShownWithoutRequestingAgain() = runTest(dispatcher) {
        blocks.current = Result.success(coachBlock())
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(coachBlock(), model.preview().block)
        assertEquals(1, model.preview().selectedWeek)
        assertTrue(blocks.requests.isEmpty())
    }

    @Test
    fun aRequestFlagWithNothingStored_isInterrupted_andNeverRequestsByItself() = runTest(dispatcher) {
        val saved = SavedStateHandle(mapOf("block_requesting" to true))
        val model = viewModel(saved)

        advanceUntilIdle()

        assertEquals(PlanFailureKind.Interrupted, model.failure())
        assertTrue(blocks.requests.isEmpty())
        assertNull(saved.get<Boolean>("block_requesting"))
    }

    @Test
    fun aRequestFlagWithAStoredBlock_showsTheBlock() = runTest(dispatcher) {
        blocks.current = Result.success(coachBlock())
        val model = viewModel(SavedStateHandle(mapOf("block_requesting" to true)))

        advanceUntilIdle()

        assertEquals(coachBlock(), model.preview().block)
    }

    @Test
    fun request_showsProgressThenThePreview_forTheOpenPlanAndUtcToday() = runTest(dispatcher) {
        blocks.requestGate = CompletableDeferred()
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(BlockPreviewEvent.Request)
        advanceUntilIdle()
        assertEquals(BlockPreviewUiState.Requesting, model.uiState.value)

        blocks.requestGate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(coachBlock(), model.preview().block)
        assertEquals(listOf("plan-1" to LocalDate(2026, 10, 19)), blocks.requests)
    }

    @Test
    fun requestWhileRequesting_isIgnored() = runTest(dispatcher) {
        blocks.requestGate = CompletableDeferred()
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(BlockPreviewEvent.Request)
        advanceUntilIdle()
        model.onEvent(BlockPreviewEvent.Request)
        blocks.requestGate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, blocks.requests.size)
    }

    @Test
    fun requestFlag_isSetWhileInFlight_andClearedAfterwards() = runTest(dispatcher) {
        blocks.requestGate = CompletableDeferred()
        val saved = SavedStateHandle()
        val model = viewModel(saved)
        advanceUntilIdle()

        model.onEvent(BlockPreviewEvent.Request)
        advanceUntilIdle()
        assertEquals(true, saved.get<Boolean>("block_requesting"))

        blocks.requestGate!!.complete(Unit)
        advanceUntilIdle()
        assertNull(saved.get<Boolean>("block_requesting"))
    }

    private fun failingRequest(failure: Throwable): BlockPreviewViewModel {
        blocks.request = Result.failure(failure)
        return viewModel()
    }

    @Test
    fun failures_mapToTheirKinds() = runTest(dispatcher) {
        val cases = listOf(
            CoachException(CoachFailure.LimitReached(CoachFailure.Scope.User)) to
                PlanFailureKind.Limit(CoachFailure.Scope.User),
            CoachException(CoachFailure.LimitReached(CoachFailure.Scope.Global)) to
                PlanFailureKind.Limit(CoachFailure.Scope.Global),
            CoachException(CoachFailure.NoCredit) to PlanFailureKind.NoCredit,
            CoachException(CoachFailure.Timeout) to PlanFailureKind.Timeout,
            CoachException(CoachFailure.Invalid) to PlanFailureKind.Invalid,
            CoachException(CoachFailure.Missing) to PlanFailureKind.Missing,
            CoachException(CoachFailure.Infeasible) to PlanFailureKind.Other,
            CoachException(CoachFailure.Other) to PlanFailureKind.Other,
            AppException(AppError.Network) to PlanFailureKind.Offline,
        )
        for ((failure, kind) in cases) {
            val model = failingRequest(failure)
            advanceUntilIdle()
            model.onEvent(BlockPreviewEvent.Request)
            advanceUntilIdle()

            assertEquals(kind, model.failure())
        }
    }

    @Test
    fun readingTheStoredBlockFails_showsTheFailureWithoutRequesting() = runTest(dispatcher) {
        blocks.current = Result.failure(AppException(AppError.Network))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(PlanFailureKind.Offline, model.failure())
        assertTrue(blocks.requests.isEmpty())
    }

    @Test
    fun retry_showsABlockStoredMeanwhile_withoutAskingAgain() = runTest(dispatcher) {
        blocks.request = Result.failure(CoachException(CoachFailure.Timeout))
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(BlockPreviewEvent.Request)
        advanceUntilIdle()
        blocks.current = Result.success(coachBlock())

        model.onEvent(BlockPreviewEvent.Retry)
        advanceUntilIdle()

        assertEquals(coachBlock(), model.preview().block)
        assertEquals(1, blocks.requests.size)
    }

    @Test
    fun retry_withNothingStored_asksAgain() = runTest(dispatcher) {
        blocks.request = Result.failure(CoachException(CoachFailure.Timeout))
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(BlockPreviewEvent.Request)
        advanceUntilIdle()
        blocks.request = Result.success(coachBlock())

        model.onEvent(BlockPreviewEvent.Retry)
        advanceUntilIdle()

        assertEquals(coachBlock(), model.preview().block)
        assertEquals(2, blocks.requests.size)
    }

    @Test
    fun exerciseNames_resolveFromTheCatalog_andAMissingIdIsAbsent() = runTest(dispatcher) {
        blocks.current = Result.success(
            coachBlock(sessionsPerWeek = 1).let { block ->
                block.copy(
                    sessions = block.sessions.map {
                        it.copy(exercises = listOf(blockExercise("ex-1"), blockExercise("gone")))
                    },
                )
            },
        )
        val model = viewModel()

        advanceUntilIdle()

        val names = model.preview().exerciseNames
        assertEquals("Press banca", names["ex-1"])
        assertNull(names["gone"])
    }

    @Test
    fun anUnreadableCatalog_stillShowsTheBlock() = runTest(dispatcher) {
        exercises.failure = AppError.Network
        blocks.current = Result.success(coachBlock())
        val model = viewModel()

        advanceUntilIdle()

        assertTrue(model.preview().exerciseNames.isEmpty())
    }

    @Test
    fun selectWeek_changesTheTab_andIsRemembered() = runTest(dispatcher) {
        blocks.current = Result.success(coachBlock())
        val saved = SavedStateHandle()
        val model = viewModel(saved)
        advanceUntilIdle()

        model.onEvent(BlockPreviewEvent.SelectWeek(3))

        assertEquals(3, model.preview().selectedWeek)
        assertEquals(3, saved.get<Int>("block_week"))
        assertTrue(model.preview().sessions.all { it.week == 3 })
    }

    @Test
    fun selectWeek_outsideTheBlock_isIgnored() = runTest(dispatcher) {
        blocks.current = Result.success(coachBlock())
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(BlockPreviewEvent.SelectWeek(9))

        assertEquals(1, model.preview().selectedWeek)
    }

    @Test
    fun aRecreatedModel_restoresTheWeekAndTheOpenReasonSheet() = runTest(dispatcher) {
        blocks.current = Result.success(coachBlock())
        val saved = SavedStateHandle()
        val first = viewModel(saved)
        advanceUntilIdle()
        first.onEvent(BlockPreviewEvent.SelectWeek(2))
        first.onEvent(BlockPreviewEvent.OpenReason(blockExerciseKey(2, 1, 0)))

        val second = viewModel(saved)
        advanceUntilIdle()

        assertEquals(2, second.preview().selectedWeek)
        assertEquals(blockExerciseKey(2, 1, 0), second.preview().reasonKey)
        assertEquals("ex-1", second.preview().reasonExercise?.exerciseId)
    }

    @Test
    fun aRestoredReasonKeyOutsideTheBlock_isDropped() = runTest(dispatcher) {
        blocks.current = Result.success(coachBlock())
        val saved = SavedStateHandle(mapOf("block_reason_exercise" to "9/9/9"))
        val model = viewModel(saved)

        advanceUntilIdle()

        assertNull(model.preview().reasonKey)
        assertNull(saved.get<String>("block_reason_exercise"))
    }

    @Test
    fun openAndCloseReason_updateTheStateAndTheSavedHandle() = runTest(dispatcher) {
        blocks.current = Result.success(coachBlock())
        val saved = SavedStateHandle()
        val model = viewModel(saved)
        advanceUntilIdle()

        model.onEvent(BlockPreviewEvent.OpenReason(blockExerciseKey(1, 0, 1)))
        assertEquals("ex-2", model.preview().reasonExercise?.exerciseId)
        assertEquals("1/0/1", saved.get<String>("block_reason_exercise"))

        model.onEvent(BlockPreviewEvent.CloseReason)
        assertNull(model.preview().reasonExercise)
        assertNull(saved.get<String>("block_reason_exercise"))
    }

    @Test
    fun save_closesTheScreenAndEmitsSaved() = runTest(dispatcher) {
        blocks.current = Result.success(coachBlock())
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(BlockPreviewEvent.Save)
            advanceUntilIdle()

            assertEquals(BlockPreviewEffect.Saved, awaitItem())
        }
        assertEquals(BlockPreviewUiState.Closed, model.uiState.value)
        assertEquals(listOf("plan-1"), blocks.saves)
    }

    @Test
    fun save_failure_keepsThePreview_andEmitsActionFailed() = runTest(dispatcher) {
        blocks.current = Result.success(coachBlock())
        blocks.saveResult = Result.failure(CoachException(CoachFailure.Conflict))
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(BlockPreviewEvent.Save)
            advanceUntilIdle()

            assertEquals(BlockPreviewEffect.ActionFailed(offline = false), awaitItem())
        }
        assertFalse(model.preview().saving)
    }

    @Test
    fun save_offline_emitsTheOfflineFailure() = runTest(dispatcher) {
        blocks.current = Result.success(coachBlock())
        blocks.saveResult = Result.failure(AppException(AppError.Network))
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(BlockPreviewEvent.Save)
            advanceUntilIdle()

            assertEquals(BlockPreviewEffect.ActionFailed(offline = true), awaitItem())
        }
    }

    @Test
    fun doubleSave_whileSaving_isIgnored() = runTest(dispatcher) {
        blocks.current = Result.success(coachBlock())
        blocks.saveGate = CompletableDeferred()
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(BlockPreviewEvent.Save)
        advanceUntilIdle()
        assertTrue(model.preview().saving)
        model.onEvent(BlockPreviewEvent.Save)
        blocks.saveGate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, blocks.saves.size)
    }

    @Test
    fun leavingTheScreen_neverSaves() = runTest(dispatcher) {
        blocks.current = Result.success(coachBlock())
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(BlockPreviewEvent.SelectWeek(2))
        model.onEvent(BlockPreviewEvent.OpenReason(blockExerciseKey(2, 0, 0)))
        model.onEvent(BlockPreviewEvent.CloseReason)

        assertTrue(blocks.saves.isEmpty())
    }
}
