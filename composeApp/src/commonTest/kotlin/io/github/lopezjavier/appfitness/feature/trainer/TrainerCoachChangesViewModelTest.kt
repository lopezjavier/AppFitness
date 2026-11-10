package io.github.lopezjavier.appfitness.feature.trainer

import app.cash.turbine.test
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CoachChangeException
import io.github.lopezjavier.appfitness.domain.model.CoachChangeFailure
import io.github.lopezjavier.appfitness.domain.model.CoachChangeFigures
import io.github.lopezjavier.appfitness.domain.model.CoachChangeKind
import io.github.lopezjavier.appfitness.domain.model.CoachChangeStatus
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.domain.model.ProgressionFlagDecision
import io.github.lopezjavier.appfitness.domain.model.UndoResult
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** T-228: the "Cambios del entrenador" list of the Trainer tab, Deshacer, the flag options and the range link. */
@OptIn(ExperimentalCoroutinesApi::class)
class TrainerCoachChangesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val plans = FakeCoachPlanRepository().apply {
        current = Result.success(coachPlan(status = PlanStatus.Accepted))
    }
    private val changes = FakeCoachChangesRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = TrainerViewModel(
        FakeCoachRepository(),
        MutableClock(),
        plans = plans,
        blocks = FakeCoachBlockRepository(),
        coachWeeks = FakeCoachWeeksRepository(),
        coachChanges = changes,
    )

    private fun TrainerViewModel.content() = assertIs<TrainerUiState.Content>(uiState.value)

    private fun TrainerViewModel.rowIds() = content().coachChanges.map { it.id }

    @Test
    fun rows_readTheCurrentPlanWithTheShownStatuses() = runTest(dispatcher) {
        changes.changes = Result.success(listOf(coachChange()))

        val model = viewModel()
        advanceUntilIdle()

        assertEquals(
            listOf<Pair<String?, Set<CoachChangeStatus>>>(
                "plan-1" to setOf(
                    CoachChangeStatus.Applied,
                    CoachChangeStatus.Undone,
                    CoachChangeStatus.Pending,
                    CoachChangeStatus.Decided,
                ),
            ),
            changes.listCalls,
        )
        assertEquals(listOf("change-1"), model.rowIds())
    }

    @Test
    fun rows_putPendingFlagsFirstAndKeepTheNewestFirstOrderOtherwise() = runTest(dispatcher) {
        changes.changes = Result.success(
            listOf(
                coachChange(id = "a"),
                coachChange(id = "b", kind = CoachChangeKind.LoadFlag, status = CoachChangeStatus.Pending),
                coachChange(id = "c", status = CoachChangeStatus.Undone),
                coachChange(id = "d", kind = CoachChangeKind.NoRangeFlag, status = CoachChangeStatus.Pending),
            ),
        )

        val model = viewModel()
        advanceUntilIdle()

        assertEquals(listOf("b", "d", "a", "c"), model.rowIds())
    }

    @Test
    fun rows_mapKindStatusFiguresAndRoutines() = runTest(dispatcher) {
        changes.changes = Result.success(
            listOf(
                coachChange(id = "x", kind = CoachChangeKind.LoadDrop, routineId = "routine-9", exerciseId = "squat"),
            ),
        )

        val model = viewModel()
        advanceUntilIdle()

        val row = model.content().coachChanges.single()
        assertEquals(CoachChangeKind.LoadDrop, row.kind)
        assertEquals(CoachChangeStatus.Applied, row.status)
        assertEquals("squat", row.exerciseId)
        assertEquals(listOf("routine-9"), row.routineIds)
        assertEquals(62.5, row.figures.nextKg)
        assertTrue(row.undoable)
    }

    @Test
    fun rows_ofUnknownKindOrStatusAreDropped() = runTest(dispatcher) {
        changes.changes = Result.success(
            listOf(
                coachChange(id = "u1", kind = CoachChangeKind.Unknown),
                coachChange(id = "u2", status = CoachChangeStatus.Unknown),
                coachChange(id = "ok"),
            ),
        )

        val model = viewModel()
        advanceUntilIdle()

        assertEquals(listOf("ok"), model.rowIds())
    }

    @Test
    fun rows_areCappedAtTenWithPendingKept() = runTest(dispatcher) {
        val applied = (1..12).map { coachChange(id = "a$it") }
        val pending = coachChange(id = "p", kind = CoachChangeKind.LoadFlag, status = CoachChangeStatus.Pending)
        changes.changes = Result.success(applied + pending)

        val model = viewModel()
        advanceUntilIdle()

        assertEquals(10, model.rowIds().size)
        assertEquals("p", model.rowIds().first())
    }

    @Test
    fun noChanges_leavesTheListEmpty() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        assertTrue(model.content().coachChanges.isEmpty())
    }

    @Test
    fun failedRead_degradesToAnEmptyList() = runTest(dispatcher) {
        changes.changes = Result.failure(AppException(AppError.Network))

        val model = viewModel()
        advanceUntilIdle()

        assertTrue(model.content().coachChanges.isEmpty())
    }

    @Test
    fun withoutAPlan_nothingIsRead() = runTest(dispatcher) {
        plans.current = Result.success(null)

        val model = viewModel()
        advanceUntilIdle()

        assertTrue(changes.listCalls.isEmpty())
        assertTrue(model.content().coachChanges.isEmpty())
    }

    @Test
    fun undo_reloadsTheListAndEmitsTheEffect() = runTest(dispatcher) {
        changes.changes = Result.success(listOf(coachChange(id = "a")))
        val model = viewModel()
        advanceUntilIdle()
        changes.onSuccess = {
            changes.changes = Result.success(listOf(coachChange(id = "a", status = CoachChangeStatus.Undone)))
        }

        model.effects.test {
            model.onEvent(TrainerEvent.UndoCoachChange("a"))
            advanceUntilIdle()

            assertEquals(TrainerEffect.CoachChangeUndone(skipped = 0), awaitItem())
        }
        assertEquals(listOf("a"), changes.undone)
        assertEquals(CoachChangeStatus.Undone, model.content().coachChanges.single().status)
        assertNull(model.content().busyChangeId)
    }

    @Test
    fun undoWithSkippedRoutines_reportsTheCount() = runTest(dispatcher) {
        changes.changes = Result.success(listOf(coachChange(id = "a")))
        changes.undoResult = Result.success(UndoResult(restored = 1, skipped = 2))
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(TrainerEvent.UndoCoachChange("a"))
            advanceUntilIdle()

            assertEquals(TrainerEffect.CoachChangeUndone(skipped = 2), awaitItem())
        }
    }

    @Test
    fun undo_ofARowThatIsNotUndoable_isIgnored() = runTest(dispatcher) {
        changes.changes = Result.success(listOf(coachChange(id = "k", kind = CoachChangeKind.LoadKept)))
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainerEvent.UndoCoachChange("k"))
        advanceUntilIdle()

        assertTrue(changes.undone.isEmpty())
    }

    @Test
    fun undoConflict_keepsTheRowAndReportsIt() = runTest(dispatcher) {
        changes.changes = Result.success(listOf(coachChange(id = "a")))
        changes.undoResult = Result.failure(CoachChangeException(CoachChangeFailure.Conflict))
        val model = viewModel()
        advanceUntilIdle()
        val before = model.content().coachChanges

        model.effects.test {
            model.onEvent(TrainerEvent.UndoCoachChange("a"))
            advanceUntilIdle()

            assertEquals(
                TrainerEffect.CoachChangeUndoFailed(CoachChangeUndoFailure.Conflict, offline = false),
                awaitItem(),
            )
        }
        assertEquals(before, model.content().coachChanges)
        assertNull(model.content().busyChangeId)
    }

    @Test
    fun undoNotUndoable_isReportedLikeAConflict() = runTest(dispatcher) {
        changes.changes = Result.success(listOf(coachChange(id = "a")))
        changes.undoResult = Result.failure(CoachChangeException(CoachChangeFailure.NotUndoable))
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(TrainerEvent.UndoCoachChange("a"))
            advanceUntilIdle()

            assertEquals(
                TrainerEffect.CoachChangeUndoFailed(CoachChangeUndoFailure.Conflict, offline = false),
                awaitItem(),
            )
        }
    }

    @Test
    fun undoNotFound_reloadsTheList() = runTest(dispatcher) {
        changes.changes = Result.success(listOf(coachChange(id = "a")))
        changes.undoResult = Result.failure(AppException(AppError.NotFound))
        val model = viewModel()
        advanceUntilIdle()
        changes.changes = Result.success(emptyList())

        model.effects.test {
            model.onEvent(TrainerEvent.UndoCoachChange("a"))
            advanceUntilIdle()

            assertEquals(
                TrainerEffect.CoachChangeUndoFailed(CoachChangeUndoFailure.NotFound, offline = false),
                awaitItem(),
            )
        }
        assertTrue(model.content().coachChanges.isEmpty())
    }

    @Test
    fun undoOffline_reportsOfflineAndKeepsTheState() = runTest(dispatcher) {
        changes.changes = Result.success(listOf(coachChange(id = "a")))
        changes.undoResult = Result.failure(AppException(AppError.Network))
        val model = viewModel()
        advanceUntilIdle()
        val before = model.content().coachChanges

        model.effects.test {
            model.onEvent(TrainerEvent.UndoCoachChange("a"))
            advanceUntilIdle()

            assertEquals(
                TrainerEffect.CoachChangeUndoFailed(CoachChangeUndoFailure.Other, offline = true),
                awaitItem(),
            )
        }
        assertEquals(before, model.content().coachChanges)
    }

    @Test
    fun secondTapWhileBusy_isIgnored() = runTest(dispatcher) {
        changes.changes = Result.success(listOf(coachChange(id = "a"), coachChange(id = "b")))
        changes.gate = CompletableDeferred()
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainerEvent.UndoCoachChange("a"))
        advanceUntilIdle()
        assertEquals("a", model.content().busyChangeId)
        model.onEvent(TrainerEvent.UndoCoachChange("a"))
        model.onEvent(TrainerEvent.UndoCoachChange("b"))
        advanceUntilIdle()

        assertEquals(listOf("a"), changes.undone)
        changes.gate?.complete(Unit)
        advanceUntilIdle()
        assertNull(model.content().busyChangeId)
    }

    @Test
    fun refresh_keepsTheBusyChange() = runTest(dispatcher) {
        changes.changes = Result.success(listOf(coachChange(id = "a")))
        changes.gate = CompletableDeferred()
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(TrainerEvent.UndoCoachChange("a"))
        advanceUntilIdle()

        model.onEvent(TrainerEvent.Refresh)
        advanceUntilIdle()

        assertEquals("a", model.content().busyChangeId)
        changes.gate?.complete(Unit)
        advanceUntilIdle()
    }

    private fun pendingFlag() =
        coachChange(id = "f", kind = CoachChangeKind.LoadFlag, status = CoachChangeStatus.Pending)

    @Test
    fun decideRaise_sendsTheDecisionAndReloads() = runTest(dispatcher) {
        changes.changes = Result.success(listOf(pendingFlag()))
        val model = viewModel()
        advanceUntilIdle()
        changes.onSuccess = { changes.changes = Result.success(emptyList()) }

        model.effects.test {
            model.onEvent(TrainerEvent.DecideFlag("f", ProgressionFlagDecision.Raise))
            advanceUntilIdle()

            assertEquals(TrainerEffect.FlagDecided(ProgressionFlagDecision.Raise), awaitItem())
        }
        assertEquals(listOf("f" to ProgressionFlagDecision.Raise), changes.decided)
        assertTrue(model.content().coachChanges.isEmpty())
        assertNull(model.content().busyChangeId)
    }

    @Test
    fun decideKeep_sendsKeep() = runTest(dispatcher) {
        changes.changes = Result.success(listOf(pendingFlag()))
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(TrainerEvent.DecideFlag("f", ProgressionFlagDecision.Keep))
            advanceUntilIdle()

            assertEquals(TrainerEffect.FlagDecided(ProgressionFlagDecision.Keep), awaitItem())
        }
        assertEquals(listOf("f" to ProgressionFlagDecision.Keep), changes.decided)
    }

    @Test
    fun decide_onAnAppliedRow_isIgnored() = runTest(dispatcher) {
        changes.changes = Result.success(listOf(coachChange(id = "a")))
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainerEvent.DecideFlag("a", ProgressionFlagDecision.Raise))
        advanceUntilIdle()

        assertTrue(changes.decided.isEmpty())
    }

    @Test
    fun decideFailure_keepsTheFlagOpen() = runTest(dispatcher) {
        changes.changes = Result.success(listOf(pendingFlag()))
        changes.decideResult = Result.failure(AppException(AppError.Network))
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(TrainerEvent.DecideFlag("f", ProgressionFlagDecision.Raise))
            advanceUntilIdle()

            assertEquals(TrainerEffect.FlagDecideFailed(offline = true, notPending = false), awaitItem())
        }
        assertEquals(listOf("f"), model.rowIds())
        assertNull(model.content().busyChangeId)
    }

    @Test
    fun decideNotPending_reloadsTheList() = runTest(dispatcher) {
        changes.changes = Result.success(listOf(pendingFlag()))
        changes.decideResult = Result.failure(CoachChangeException(CoachChangeFailure.NotPending))
        val model = viewModel()
        advanceUntilIdle()
        changes.changes = Result.success(
            listOf(coachChange(id = "f", kind = CoachChangeKind.LoadFlag, status = CoachChangeStatus.Decided)),
        )

        model.effects.test {
            model.onEvent(TrainerEvent.DecideFlag("f", ProgressionFlagDecision.Raise))
            advanceUntilIdle()

            assertEquals(TrainerEffect.FlagDecideFailed(offline = false, notPending = true), awaitItem())
        }
        assertEquals(CoachChangeStatus.Decided, model.content().coachChanges.single().status)
    }

    @Test
    fun openRange_emitsTheNavigationEffectForTheFirstRoutine() = runTest(dispatcher) {
        changes.changes = Result.success(
            listOf(
                coachChange(
                    id = "n",
                    kind = CoachChangeKind.NoRangeFlag,
                    status = CoachChangeStatus.Pending,
                    exerciseId = "curl",
                    routineId = "routine-7",
                ),
            ),
        )
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(TrainerEvent.OpenRange("n"))
            advanceUntilIdle()

            assertEquals(TrainerEffect.OpenRoutineRange("routine-7", "curl"), awaitItem())
        }
    }

    @Test
    fun openRange_onAServerShapedRowWithoutSnapshots_usesTheRoutineOfTheFigures() = runTest(dispatcher) {
        changes.changes = Result.success(listOf(serverNoRangeFlag(CoachChangeFigures(routineId = "routine-9"))))
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(TrainerEvent.OpenRange("n"))
            advanceUntilIdle()

            assertEquals(TrainerEffect.OpenRoutineRange("routine-9", "curl"), awaitItem())
        }
    }

    @Test
    fun openRange_withoutAnyRoutine_tellsTheUserInsteadOfReturningSilently() = runTest(dispatcher) {
        changes.changes = Result.success(listOf(serverNoRangeFlag(CoachChangeFigures())))
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(TrainerEvent.OpenRange("n"))
            advanceUntilIdle()

            assertEquals(TrainerEffect.RangeUnavailable, awaitItem())
        }
    }

    private fun serverNoRangeFlag(figures: CoachChangeFigures) = coachChange(
        id = "n",
        kind = CoachChangeKind.NoRangeFlag,
        status = CoachChangeStatus.Pending,
        exerciseId = "curl",
        figures = figures,
    ).copy(before = null, after = null)

    @Test
    fun openRange_onAnotherKindOfRow_isIgnored() = runTest(dispatcher) {
        changes.changes = Result.success(listOf(coachChange(id = "a")))
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(TrainerEvent.OpenRange("a"))
            advanceUntilIdle()

            expectNoEvents()
        }
    }
}
