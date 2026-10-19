package io.github.lopezjavier.appfitness.feature.workout

import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.ExerciseHistoryPoint
import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.usecase.SupersetLabel
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
import kotlin.time.Duration.Companion.seconds

class LiveSessionViewModelTest {
    private val repository = FakeWorkoutSessionRepository(twoExercises())
    private val exercises =
        FakeExerciseRepository(
            listOf(
                exerciseRow("e1", "bench"),
                exerciseRow("e2", "squat"),
                exerciseRow("e3", null, name = "Mi ejercicio", custom = true),
            ),
        )
    private val library = FakeExerciseLibrary(mapOf("bench" to exerciseInfo("bench", "Press banca")))
    private val connectivity = FakeConnectivityObserver()
    private val clock = MutableClock()
    private val haptics = FakeHaptics()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.loaded(): LiveSessionViewModel {
        val vm = LiveSessionViewModel(repository, exercises, library, connectivity, clock, haptics)
        advanceUntilIdle()
        return vm
    }

    private fun LiveSessionViewModel.active() = assertIs<LiveSessionUiState.Active>(uiState.value)

    private fun LiveSessionViewModel.row(position: Int, setIndex: Int) =
        active().exercises[position].sets.first { it.setIndex == setIndex }

    private fun TestScope.type(vm: LiveSessionViewModel, position: Int, index: Int, weight: String, reps: String) {
        vm.onEvent(LiveSessionEvent.WeightChanged(position, index, weight))
        vm.onEvent(LiveSessionEvent.RepsChanged(position, index, reps))
        advanceUntilIdle()
    }

    private fun TestScope.check(vm: LiveSessionViewModel, position: Int, index: Int, checked: Boolean = true) {
        vm.onEvent(LiveSessionEvent.SetChecked(position, index, checked))
        advanceUntilIdle()
    }

    @Test
    fun startsLoading_thenShowsTheSessionWithThreeRowsPerExercise() = runTest {
        val vm = LiveSessionViewModel(repository, exercises, library, connectivity, clock, haptics)
        assertEquals(LiveSessionUiState.Loading, vm.uiState.value)

        advanceUntilIdle()

        val state = vm.active()
        assertEquals("Torso", state.name)
        assertEquals(SESSION_START, state.startedAt)
        assertEquals(listOf(3, 3), state.exercises.map { it.sets.size })
        assertEquals(listOf("Press banca", null), state.exercises.map { it.name })
        assertTrue(state.exercises.flatMap { it.sets }.none { it.completed || it.pendingSave })
    }

    @Test
    fun customExercisesShowTheirOwnName() = runTest {
        repository.session = sessionOf(SessionExercise("e3", 0, null, listOf(pendingSet(1))))

        val vm = loaded()

        assertEquals("Mi ejercicio", vm.active().exercises.single().name)
    }

    @Test
    fun supersetExercisesCarryTheirLabel() = runTest {
        repository.session = sessionOf(
            SessionExercise("e1", 0, 4, listOf(pendingSet(1))),
            SessionExercise("e2", 1, 4, listOf(pendingSet(1))),
        )

        val vm = loaded()

        assertEquals(
            listOf(SupersetLabel('A', 1), SupersetLabel('A', 2)),
            vm.active().exercises.map { it.supersetLabel },
        )
    }

    @Test
    fun noInProgressSession_isNoSession() = runTest {
        repository.session = null

        val vm = loaded()

        assertEquals(LiveSessionUiState.NoSession, vm.uiState.value)
    }

    @Test
    fun loadFailure_isAnErrorThatRetryRecovers() = runTest {
        repository.getFailure = AppError.Network
        val vm = loaded()
        assertEquals(LiveSessionUiState.Error, vm.uiState.value)

        repository.getFailure = null
        vm.onEvent(LiveSessionEvent.Retry)
        assertEquals(LiveSessionUiState.Loading, vm.uiState.value)
        advanceUntilIdle()

        assertIs<LiveSessionUiState.Active>(vm.uiState.value)
    }

    @Test
    fun exerciseNamesFailing_stillShowsTheSessionWithoutNames() = runTest {
        exercises.failure = AppError.Network

        val vm = loaded()

        assertEquals(listOf(null, null), vm.active().exercises.map { it.name })
    }

    @Test
    fun resumingRestoresTheCheckedRows() = runTest {
        repository.session = sessionOf(
            SessionExercise("e1", 0, null, listOf(doneSet(1, 60.0, 12), doneSet(2, 87.5, 8), pendingSet(3))),
        )

        val vm = loaded()

        val sets = vm.active().exercises.single().sets
        assertEquals(listOf(true, true, false), sets.map { it.completed })
        assertEquals("60" to "12", sets[0].weight to sets[0].reps)
        assertEquals("87,5" to "8", sets[1].weight to sets[1].reps)
        assertEquals("" to "", sets[2].weight to sets[2].reps)
    }

    @Test
    fun checkingASet_ticksOnce_andUpsertsItCompletedNow() = runTest {
        val vm = loaded()
        type(vm, 0, 1, "60", "12")
        clock.advance(30.seconds)

        check(vm, 0, 1)

        assertEquals(1, haptics.ticks)
        val write = repository.upserts.single()
        assertEquals(Triple("e1", 0, 1), Triple(write.exerciseId, write.exercisePosition, write.setIndex))
        assertEquals(SetEntry.WeightReps(60.0, 12), write.entry)
        assertEquals(clock.current, write.completedAt)
        assertTrue(vm.row(0, 1).completed)
        assertFalse(vm.row(0, 1).pendingSave)
    }

    @Test
    fun weightAcceptsACommaOrADot() = runTest {
        val vm = loaded()
        type(vm, 0, 1, "87,5", "8")
        type(vm, 0, 2, "87.5", "8")

        check(vm, 0, 1)
        check(vm, 0, 2)

        assertEquals(listOf(87.5, 87.5), repository.upserts.map { (it.entry as SetEntry.WeightReps).weightKg })
    }

    @Test
    fun editingADraftDoesNotSaveAnything() = runTest {
        val vm = loaded()

        type(vm, 0, 1, "60", "12")

        assertTrue(repository.upserts.isEmpty())
        assertEquals("60" to "12", vm.row(0, 1).weight to vm.row(0, 1).reps)
    }

    @Test
    fun uncheckingASet_upsertsItPendingAndMakesItEditable() = runTest {
        val vm = loaded()
        type(vm, 0, 1, "60", "12")
        check(vm, 0, 1)

        check(vm, 0, 1, checked = false)
        type(vm, 0, 1, "62,5", "12")

        assertNull(repository.upserts.last().completedAt)
        assertEquals(1, haptics.ticks)
        assertFalse(vm.row(0, 1).completed)
        assertEquals("62,5", vm.row(0, 1).weight)
    }

    @Test
    fun aCompletedRowIgnoresEdits() = runTest {
        val vm = loaded()
        type(vm, 0, 1, "60", "12")
        check(vm, 0, 1)

        type(vm, 0, 1, "99", "99")

        assertEquals("60" to "12", vm.row(0, 1).weight to vm.row(0, 1).reps)
    }

    @Test
    fun checkingWithEmptyReps_isRefusedWithoutHapticOrUpsert() = runTest {
        val vm = loaded()
        type(vm, 0, 1, "60", "")

        check(vm, 0, 1)

        assertTrue(repository.upserts.isEmpty())
        assertEquals(0, haptics.ticks)
        assertFalse(vm.row(0, 1).completed)
        assertTrue(vm.row(0, 1).invalid)
    }

    @Test
    fun checkingWithZeroRepsOrNoWeight_isRefused() = runTest {
        val vm = loaded()
        type(vm, 0, 1, "60", "0")
        type(vm, 0, 2, "", "10")

        check(vm, 0, 1)
        check(vm, 0, 2)

        assertTrue(repository.upserts.isEmpty())
    }

    @Test
    fun aZeroWeightWithRepsIsValid() = runTest {
        val vm = loaded()
        type(vm, 0, 1, "0", "10")

        check(vm, 0, 1)

        assertEquals(SetEntry.WeightReps(0.0, 10), repository.upserts.single().entry)
    }

    @Test
    fun editingARefusedRowClearsTheInvalidMark() = runTest {
        val vm = loaded()
        check(vm, 0, 1)

        type(vm, 0, 1, "60", "10")

        assertFalse(vm.row(0, 1).invalid)
    }

    @Test
    fun addingASet_appendsTheNextIndexWithThePreviousValuesAsPlaceholders() = runTest {
        val vm = loaded()
        type(vm, 0, 3, "87,5", "8")

        vm.onEvent(LiveSessionEvent.AddSet(0))
        advanceUntilIdle()

        val added = vm.active().exercises[0].sets.last()
        assertEquals(4, added.setIndex)
        assertEquals("87,5" to "8", added.weightPlaceholder to added.repsPlaceholder)
        assertEquals("" to "", added.weight to added.reps)
        val write = repository.upserts.single()
        assertEquals(4, write.setIndex)
        assertEquals(SetEntry.WeightReps(0.0, 0), write.entry)
        assertNull(write.completedAt)
    }

    @Test
    fun addingAnExercise_asksForThePicker_thenAppendsItAtTheEndWithOneEmptySet() = runTest {
        val vm = loaded()

        vm.effects.test {
            vm.onEvent(LiveSessionEvent.AddExercise)
            assertEquals(LiveSessionEffect.OpenPicker, awaitItem())
        }
        vm.onEvent(LiveSessionEvent.ExerciseSelected("e3"))
        advanceUntilIdle()

        val added = vm.active().exercises.last()
        assertEquals(2, added.position)
        assertEquals("Mi ejercicio", added.name)
        assertEquals(listOf(1), added.sets.map { it.setIndex })
        val write = repository.upserts.single()
        assertEquals(Triple("e3", 2, 1), Triple(write.exerciseId, write.exercisePosition, write.setIndex))
        assertNull(write.supersetGroup)
        assertNull(write.completedAt)
    }

    @Test
    fun offlineCheck_marksTheRowPendingWithoutCallingTheServer() = runTest {
        connectivity.state.value = false
        val vm = loaded()
        type(vm, 0, 1, "60", "12")

        check(vm, 0, 1)

        assertTrue(repository.upserts.isEmpty())
        assertTrue(vm.row(0, 1).completed)
        assertTrue(vm.row(0, 1).pendingSave)
        assertEquals(1, vm.active().queuedCount)
        assertEquals(1, haptics.ticks)
    }

    @Test
    fun reconnecting_flushesTheQueueOnceAndClearsTheMarker() = runTest {
        connectivity.state.value = false
        val vm = loaded()
        type(vm, 0, 1, "60", "12")
        check(vm, 0, 1)

        connectivity.state.value = true
        advanceUntilIdle()

        assertEquals(1, repository.upserts.size)
        assertEquals(SetEntry.WeightReps(60.0, 12), repository.upserts.single().entry)
        assertFalse(vm.row(0, 1).pendingSave)
        assertEquals(0, vm.active().queuedCount)
    }

    @Test
    fun anUpsertFailureWhileOnline_queuesTheSetAndTheNextReconnectSendsIt() = runTest {
        repository.upsertFailure = AppError.Network
        val vm = loaded()
        type(vm, 0, 1, "60", "12")
        check(vm, 0, 1)
        assertTrue(vm.row(0, 1).pendingSave)
        assertTrue(vm.row(0, 1).completed)

        repository.upsertFailure = null
        connectivity.state.value = false
        advanceUntilIdle()
        connectivity.state.value = true
        advanceUntilIdle()

        assertEquals(1, repository.upserts.size)
        assertFalse(vm.row(0, 1).pendingSave)
    }

    @Test
    fun aLaterSuccessfulWriteAlsoSendsTheQueuedOnes() = runTest {
        repository.upsertFailure = AppError.Network
        val vm = loaded()
        type(vm, 0, 1, "60", "12")
        check(vm, 0, 1)
        repository.upsertFailure = null
        type(vm, 0, 2, "60", "12")

        check(vm, 0, 2)

        assertEquals(listOf(1, 2), repository.upserts.map { it.setIndex })
        assertEquals(0, vm.active().queuedCount)
    }

    @Test
    fun twoEditsOfTheSameQueuedSet_sendOnlyTheLast() = runTest {
        connectivity.state.value = false
        val vm = loaded()
        type(vm, 0, 1, "60", "12")
        check(vm, 0, 1)
        check(vm, 0, 1, checked = false)

        connectivity.state.value = true
        advanceUntilIdle()

        val write = repository.upserts.single()
        assertNull(write.completedAt)
    }

    @Test
    fun requestingFinish_countsTheIncompleteSets() = runTest {
        val vm = loaded()
        completeBenchSets(vm)

        vm.onEvent(LiveSessionEvent.RequestFinish)

        assertEquals(FinishSheet(pendingCount = 3), vm.active().finishSheet)
    }

    @Test
    fun dismissingTheFinishSheet_closesItWithoutFinishing() = runTest {
        val vm = loaded()
        vm.onEvent(LiveSessionEvent.RequestFinish)

        vm.onEvent(LiveSessionEvent.DismissFinish)

        assertNull(vm.active().finishSheet)
        assertTrue(repository.finished.isEmpty())
    }

    @Test
    fun confirmingFinish_finishesAndShowsTheSummary() = runTest {
        repository.history = mapOf("e1" to emptyList(), "e2" to emptyList())
        val vm = loaded()
        completeBenchSets(vm)
        clock.advance(3150.seconds)

        vm.onEvent(LiveSessionEvent.RequestFinish)
        vm.onEvent(LiveSessionEvent.ConfirmFinish)
        advanceUntilIdle()

        assertEquals(listOf("s1"), repository.finished)
        val summary = assertIs<LiveSessionUiState.Finished>(vm.uiState.value).summary
        assertEquals(3150L, summary.summary.durationSeconds)
        assertEquals(3, summary.summary.completedSets)
        assertEquals(2120.0, summary.summary.exercises[0].volume)
        assertEquals(mapOf(0 to "Press banca", 1 to null), summary.exerciseNames)
    }

    @Test
    fun summaryMarksAFirstEverSessionAsARecord_andNotALowerOneRepMax() = runTest {
        repository.history = mapOf(
            "e1" to listOf(ExerciseHistoryPoint("old", LocalDate(2026, 10, 1), 500.0, 0.0)),
            "e2" to emptyList(),
        )
        repository.session = sessionOf(
            SessionExercise("e1", 0, null, listOf(doneSet(1, 60.0, 12))),
            SessionExercise("e2", 1, null, listOf(doneSet(1, 100.0, 5))),
        )
        val vm = loaded()

        vm.onEvent(LiveSessionEvent.RequestFinish)
        vm.onEvent(LiveSessionEvent.ConfirmFinish)
        advanceUntilIdle()

        val exercises = assertIs<LiveSessionUiState.Finished>(vm.uiState.value).summary.summary.exercises
        assertEquals(listOf(false, true), exercises.map { it.isPersonalRecord })
    }

    @Test
    fun historyFailure_stillShowsTheSummaryWithoutRecords() = runTest {
        repository.historyFailure = AppError.Network
        val vm = loaded()
        completeBenchSets(vm)

        vm.onEvent(LiveSessionEvent.RequestFinish)
        vm.onEvent(LiveSessionEvent.ConfirmFinish)
        advanceUntilIdle()

        val exercises = assertIs<LiveSessionUiState.Finished>(vm.uiState.value).summary.summary.exercises
        assertTrue(exercises.none { it.isPersonalRecord })
    }

    @Test
    fun finishFailure_keepsTheStateAndReportsIt() = runTest {
        repository.finishFailure = AppError.Network
        val vm = loaded()
        vm.onEvent(LiveSessionEvent.RequestFinish)

        vm.effects.test {
            vm.onEvent(LiveSessionEvent.ConfirmFinish)
            advanceUntilIdle()
            assertEquals(LiveSessionEffect.ActionFailed, awaitItem())
        }

        val state = vm.active()
        assertFalse(state.busy)
        assertEquals(FinishSheet(6), state.finishSheet)
    }

    @Test
    fun finishingOffline_isBlocked() = runTest {
        connectivity.state.value = false
        val vm = loaded()
        assertTrue(vm.active().actionsBlocked)

        vm.onEvent(LiveSessionEvent.RequestFinish)
        vm.onEvent(LiveSessionEvent.RequestDiscard)

        assertNull(vm.active().finishSheet)
        assertFalse(vm.active().discardConfirm)
    }

    @Test
    fun finishingFlushesTheQueueFirst_andKeepsTheStateIfItCannotBeSent() = runTest {
        val vm = loaded()
        type(vm, 0, 1, "60", "12")
        repository.upsertFailure = AppError.Network
        check(vm, 0, 1)
        vm.onEvent(LiveSessionEvent.RequestFinish)

        vm.effects.test {
            vm.onEvent(LiveSessionEvent.ConfirmFinish)
            advanceUntilIdle()
            assertEquals(LiveSessionEffect.ActionFailed, awaitItem())
        }

        assertTrue(repository.finished.isEmpty())
        assertIs<LiveSessionUiState.Active>(vm.uiState.value)
    }

    @Test
    fun confirmingDiscard_discardsAndCloses() = runTest {
        val vm = loaded()

        vm.onEvent(LiveSessionEvent.RequestDiscard)
        assertTrue(vm.active().discardConfirm)
        vm.effects.test {
            vm.onEvent(LiveSessionEvent.ConfirmDiscard)
            advanceUntilIdle()
            assertEquals(LiveSessionEffect.Closed, awaitItem())
        }

        assertEquals(listOf("s1"), repository.discarded)
    }

    @Test
    fun dismissingDiscard_keepsTheSession() = runTest {
        val vm = loaded()
        vm.onEvent(LiveSessionEvent.RequestDiscard)

        vm.onEvent(LiveSessionEvent.DismissDiscard)

        assertFalse(vm.active().discardConfirm)
        assertTrue(repository.discarded.isEmpty())
    }

    @Test
    fun discardFailure_keepsTheStateAndReportsIt() = runTest {
        repository.discardFailure = AppError.Network
        val vm = loaded()
        vm.onEvent(LiveSessionEvent.RequestDiscard)

        vm.effects.test {
            vm.onEvent(LiveSessionEvent.ConfirmDiscard)
            advanceUntilIdle()
            assertEquals(LiveSessionEffect.ActionFailed, awaitItem())
        }

        assertFalse(vm.active().busy)
        assertIs<LiveSessionUiState.Active>(vm.uiState.value)
    }

    @Test
    fun done_closesTheFlow() = runTest {
        val vm = loaded()
        vm.onEvent(LiveSessionEvent.RequestFinish)
        vm.onEvent(LiveSessionEvent.ConfirmFinish)
        advanceUntilIdle()

        vm.effects.test {
            vm.onEvent(LiveSessionEvent.Done)
            assertEquals(LiveSessionEffect.Closed, awaitItem())
        }
    }

    @Test
    fun theStopwatchCountsFromTheStartWithTheInjectedClock() = runTest {
        val vm = loaded()
        clock.advance(90.seconds)

        vm.elapsedSeconds.test {
            assertEquals(90L, awaitItem())
            clock.advance(5.seconds)
            advanceTimeBy(1_001)
            assertEquals(95L, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun TestScope.completeBenchSets(vm: LiveSessionViewModel) {
        type(vm, 0, 1, "60", "12")
        type(vm, 0, 2, "87,5", "8")
        type(vm, 0, 3, "87,5", "8")
        check(vm, 0, 1)
        check(vm, 0, 2)
        check(vm, 0, 3)
    }

    private companion object {
        fun twoExercises() = sessionOf(
            SessionExercise("e1", 0, null, listOf(pendingSet(1), pendingSet(2), pendingSet(3))),
            SessionExercise("e2", 1, null, listOf(pendingSet(1), pendingSet(2), pendingSet(3))),
        )
    }
}
