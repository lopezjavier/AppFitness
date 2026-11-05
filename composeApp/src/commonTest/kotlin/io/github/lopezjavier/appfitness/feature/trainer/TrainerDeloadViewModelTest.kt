package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CoachChangeException
import io.github.lopezjavier.appfitness.domain.model.CoachChangeFailure
import io.github.lopezjavier.appfitness.domain.model.CoachChangeKind
import io.github.lopezjavier.appfitness.domain.model.CoachChangeStatus
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.domain.model.WeekPhase
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

/** T-230 (US-100): deloads apply on their own, show as plan-level coach changes, can be cancelled or requested. */
@OptIn(ExperimentalCoroutinesApi::class)
class TrainerDeloadViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val plans = FakeCoachPlanRepository().apply {
        current = Result.success(coachPlan(status = PlanStatus.Accepted))
    }
    private val blocks = FakeCoachBlockRepository().apply { current = Result.success(coachBlock(saved = true)) }
    private val weeks = FakeCoachWeeksRepository().apply {
        weeksResult = Result.success(listOf(planWeek(1), planWeek(2, WeekPhase.Upcoming, completed = 0)))
    }
    private val changes = FakeCoachChangesRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) = TrainerViewModel(
        FakeCoachRepository(),
        MutableClock(),
        plans = plans,
        blocks = blocks,
        coachWeeks = weeks,
        savedState = saved,
        coachChanges = changes,
    )

    private fun TrainerViewModel.content() = assertIs<TrainerUiState.Content>(uiState.value)

    @Test
    fun triggeredDeload_appearsAsAPlanLevelRowWithoutExerciseAndIsCancellable() = runTest(dispatcher) {
        changes.changes = Result.success(listOf(deloadChange(kind = CoachChangeKind.DeloadTriggered)))
        val model = viewModel()

        advanceUntilIdle()

        val row = model.content().coachChanges.single()
        assertEquals(CoachChangeKind.DeloadTriggered, row.kind)
        assertNull(row.exerciseId)
        assertEquals(4, row.figures.week)
        assertTrue(row.cancellable)
        assertFalse(row.undoable)
    }

    @Test
    fun scheduledAndUserDeloads_areShownToo() = runTest(dispatcher) {
        changes.changes = Result.success(
            listOf(
                deloadChange("a", CoachChangeKind.DeloadScheduled),
                deloadChange("b", CoachChangeKind.DeloadUser, week = 2),
            ),
        )
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(
            listOf(CoachChangeKind.DeloadScheduled, CoachChangeKind.DeloadUser),
            model.content().coachChanges.map { it.kind },
        )
    }

    @Test
    fun cancelledDeload_isShownButNotCancellableAgain() = runTest(dispatcher) {
        changes.changes = Result.success(listOf(deloadChange(status = CoachChangeStatus.Undone)))
        val model = viewModel()

        advanceUntilIdle()

        val row = model.content().coachChanges.single()
        assertEquals(CoachChangeStatus.Undone, row.status)
        assertFalse(row.cancellable)
    }

    @Test
    fun deloadAppliedByTheRule_isReadBackInTheSameLoad() = runTest(dispatcher) {
        weeks.evaluation = Result.success(deloadChange(kind = CoachChangeKind.DeloadScheduled))
        weeks.onEvaluate =
            { changes.changes = Result.success(listOf(deloadChange(kind = CoachChangeKind.DeloadScheduled))) }
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(listOf(CoachChangeKind.DeloadScheduled), model.content().coachChanges.map { it.kind })
    }

    @Test
    fun cancel_sendsTheIdReloadsRowsAndWeeks_andEmitsTheEffect() = runTest(dispatcher) {
        changes.changes = Result.success(listOf(deloadChange()))
        val model = viewModel()
        advanceUntilIdle()
        weeks.onSuccess = {
            changes.changes = Result.success(listOf(deloadChange(status = CoachChangeStatus.Undone)))
            weeks.weeksResult = Result.success(listOf(planWeek(1), planWeek(2, WeekPhase.Upcoming)))
        }

        model.effects.test {
            model.onEvent(TrainerEvent.CancelDeload("deload-1"))
            advanceUntilIdle()
            assertEquals(TrainerEffect.DeloadCancelled, awaitItem())
        }

        assertEquals(listOf("deload-1"), weeks.cancellations)
        assertEquals(CoachChangeStatus.Undone, model.content().coachChanges.single().status)
        assertNull(model.content().busyChangeId)
        assertEquals(2, weeks.weeksReads.size)
    }

    @Test
    fun cancel_failure_keepsTheRowAndEmitsTheFailure() = runTest(dispatcher) {
        changes.changes = Result.success(listOf(deloadChange()))
        weeks.cancelResult = Result.failure(AppException(AppError.Unknown))
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(TrainerEvent.CancelDeload("deload-1"))
            advanceUntilIdle()
            assertEquals(TrainerEffect.DeloadCancelFailed(offline = false), awaitItem())
        }

        assertEquals(CoachChangeStatus.Applied, model.content().coachChanges.single().status)
        assertNull(model.content().busyChangeId)
    }

    @Test
    fun cancel_offline_flagsOffline() = runTest(dispatcher) {
        changes.changes = Result.success(listOf(deloadChange()))
        weeks.cancelResult = Result.failure(AppException(AppError.Network))
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(TrainerEvent.CancelDeload("deload-1"))
            advanceUntilIdle()
            assertEquals(TrainerEffect.DeloadCancelFailed(offline = true), awaitItem())
        }
    }

    @Test
    fun cancel_whenAlreadyCancelledElsewhere_reloadsTheList() = runTest(dispatcher) {
        changes.changes = Result.success(listOf(deloadChange()))
        weeks.cancelResult = Result.failure(CoachChangeException(CoachChangeFailure.NotUndoable))
        val model = viewModel()
        advanceUntilIdle()
        changes.changes = Result.success(listOf(deloadChange(status = CoachChangeStatus.Undone)))

        model.effects.test {
            model.onEvent(TrainerEvent.CancelDeload("deload-1"))
            advanceUntilIdle()
            assertEquals(TrainerEffect.DeloadCancelFailed(offline = false), awaitItem())
        }

        assertEquals(CoachChangeStatus.Undone, model.content().coachChanges.single().status)
    }

    @Test
    fun cancel_ofARowThatIsNotAnAppliedDeload_isIgnored() = runTest(dispatcher) {
        changes.changes =
            Result.success(listOf(coachChange(id = "raise"), deloadChange(status = CoachChangeStatus.Undone)))
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainerEvent.CancelDeload("raise"))
        model.onEvent(TrainerEvent.CancelDeload("deload-1"))
        model.onEvent(TrainerEvent.CancelDeload("missing"))
        advanceUntilIdle()

        assertTrue(weeks.cancellations.isEmpty())
    }

    @Test
    fun doubleTapOnCancel_sendsOneCall() = runTest(dispatcher) {
        changes.changes = Result.success(listOf(deloadChange()))
        weeks.gate = CompletableDeferred()
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainerEvent.CancelDeload("deload-1"))
        model.onEvent(TrainerEvent.CancelDeload("deload-1"))
        advanceUntilIdle()

        assertEquals(1, weeks.cancellations.size)
        assertEquals("deload-1", model.content().busyChangeId)
        weeks.gate?.complete(Unit)
    }

    @Test
    fun requestIsOffered_whenNoDeloadIsPlannedAndThereIsANextWeek() = runTest(dispatcher) {
        val model = viewModel()

        advanceUntilIdle()

        assertTrue(model.content().canRequestDeload)
    }

    @Test
    fun requestIsHidden_whenAWeekIsAlreadyADeload() = runTest(dispatcher) {
        weeks.weeksResult = Result.success(listOf(planWeek(1), planWeek(2, WeekPhase.Upcoming, deload = true)))
        val model = viewModel()

        advanceUntilIdle()

        assertFalse(model.content().canRequestDeload)
    }

    @Test
    fun requestIsHidden_inTheLastWeekOfTheBlock() = runTest(dispatcher) {
        weeks.weeksResult = Result.success(listOf(planWeek(1, WeekPhase.Past), planWeek(2, WeekPhase.Current)))
        val model = viewModel()

        advanceUntilIdle()

        assertFalse(model.content().canRequestDeload)
    }

    @Test
    fun requestIsOffered_beforeTheBlockStarts_forWeekOne() = runTest(dispatcher) {
        weeks.weeksResult = Result.success(listOf(planWeek(1, WeekPhase.Upcoming), planWeek(2, WeekPhase.Upcoming)))
        val model = viewModel()

        advanceUntilIdle()

        assertTrue(model.content().canRequestDeload)
    }

    @Test
    fun requestIsHidden_withoutWeeksOrASavedBlock() = runTest(dispatcher) {
        weeks.weeksResult = Result.success(emptyList())
        val model = viewModel()

        advanceUntilIdle()

        assertFalse(model.content().canRequestDeload)
    }

    @Test
    fun request_appliesAtOnce_reloadsRowsAndWeeks_andEmitsTheEffect() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        weeks.onSuccess = {
            changes.changes = Result.success(listOf(deloadChange("user", CoachChangeKind.DeloadUser, week = 2)))
            weeks.weeksResult = Result.success(listOf(planWeek(1), planWeek(2, WeekPhase.Upcoming, deload = true)))
        }

        model.effects.test {
            model.onEvent(TrainerEvent.RequestDeload)
            advanceUntilIdle()
            assertEquals(TrainerEffect.DeloadRequested, awaitItem())
        }

        assertEquals(1, weeks.requests)
        assertEquals(listOf(CoachChangeKind.DeloadUser), model.content().coachChanges.map { it.kind })
        assertTrue(model.content().weeks.last().deload)
        assertFalse(model.content().canRequestDeload)
        assertFalse(model.content().requestingDeload)
    }

    @Test
    fun request_refusedBecauseOneIsPlanned_reloadsAndEmitsNotAvailable() = runTest(dispatcher) {
        weeks.requestResult = Result.failure(CoachChangeException(CoachChangeFailure.DeloadNotAvailable))
        val model = viewModel()
        advanceUntilIdle()
        weeks.weeksResult = Result.success(listOf(planWeek(1), planWeek(2, WeekPhase.Upcoming, deload = true)))

        model.effects.test {
            model.onEvent(TrainerEvent.RequestDeload)
            advanceUntilIdle()
            assertEquals(TrainerEffect.DeloadRequestFailed(offline = false, notAvailable = true), awaitItem())
        }

        assertFalse(model.content().canRequestDeload)
        assertFalse(model.content().requestingDeload)
    }

    @Test
    fun request_offline_flagsOfflineAndKeepsTheAction() = runTest(dispatcher) {
        weeks.requestResult = Result.failure(AppException(AppError.Network))
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(TrainerEvent.RequestDeload)
            advanceUntilIdle()
            assertEquals(TrainerEffect.DeloadRequestFailed(offline = true, notAvailable = false), awaitItem())
        }

        assertTrue(model.content().canRequestDeload)
        assertFalse(model.content().requestingDeload)
    }

    @Test
    fun request_otherError_isAGenericFailure() = runTest(dispatcher) {
        weeks.requestResult = Result.failure(AppException(AppError.Unknown))
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(TrainerEvent.RequestDeload)
            advanceUntilIdle()
            assertEquals(TrainerEffect.DeloadRequestFailed(offline = false, notAvailable = false), awaitItem())
        }
    }

    @Test
    fun doubleTapOnRequest_sendsOneCall() = runTest(dispatcher) {
        weeks.gate = CompletableDeferred()
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainerEvent.RequestDeload)
        model.onEvent(TrainerEvent.RequestDeload)
        advanceUntilIdle()

        assertEquals(1, weeks.requests)
        assertTrue(model.content().requestingDeload)
        weeks.gate?.complete(Unit)
    }

    @Test
    fun request_whenNotAllowed_isIgnored() = runTest(dispatcher) {
        weeks.weeksResult = Result.success(listOf(planWeek(1), planWeek(2, WeekPhase.Upcoming, deload = true)))
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainerEvent.RequestDeload)
        advanceUntilIdle()

        assertEquals(0, weeks.requests)
    }

    @Test
    fun recreatedViewModel_rebuildsTheDeloadRowAndRequestFromTheServer() = runTest(dispatcher) {
        changes.changes = Result.success(listOf(deloadChange()))
        val saved = SavedStateHandle()
        viewModel(saved)
        advanceUntilIdle()

        val recreated = viewModel(saved)
        advanceUntilIdle()

        assertEquals(listOf("deload-1"), recreated.content().coachChanges.map { it.id })
        assertFalse(recreated.content().requestingDeload)
        assertNull(recreated.content().busyChangeId)
    }
}
