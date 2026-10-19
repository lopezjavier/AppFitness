package io.github.lopezjavier.appfitness.feature.workout

import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.ExerciseHistoryPoint
import io.github.lopezjavier.appfitness.domain.model.PreviousSet
import io.github.lopezjavier.appfitness.domain.model.Routine
import io.github.lopezjavier.appfitness.domain.model.RoutineExercise
import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.usecase.SupersetLabel
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import io.github.lopezjavier.appfitness.feature.routines.FakeRoutineRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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

@Suppress("LargeClass") // One class per view model: session, ghost data and rest timer share the same setup.
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
    private val alertSound = FakeAlertSound()
    private val routines = FakeRoutineRepository(listOf(routineWithRests(0, 0)))

    private var created: LiveSessionViewModel? = null

    private fun newViewModel() = LiveSessionViewModel(
        repository,
        exercises,
        library,
        connectivity,
        clock,
        haptics,
        routines,
        alertSound,
    ).also { created = it }

    /** Stops a rest ticker left running, which would never let the virtual scheduler go idle (frozen clock). */
    private fun runSessionTest(body: suspend TestScope.() -> Unit) = runTest {
        body()
        created?.onEvent(LiveSessionEvent.RestSkip)
        runCurrent()
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.loaded(): LiveSessionViewModel {
        val vm = newViewModel()
        advanceUntilIdle()
        return vm
    }

    private fun LiveSessionViewModel.active() = assertIs<LiveSessionUiState.Active>(uiState.value)

    private fun LiveSessionViewModel.row(position: Int, setIndex: Int) =
        active().exercises[position].sets.first { it.setIndex == setIndex }

    private fun TestScope.type(vm: LiveSessionViewModel, position: Int, index: Int, weight: String, reps: String) {
        vm.onEvent(LiveSessionEvent.WeightChanged(position, index, weight))
        vm.onEvent(LiveSessionEvent.RepsChanged(position, index, reps))
        runCurrent() // Not advanceUntilIdle: a running rest ticker never goes idle on a virtual clock.
    }

    private fun TestScope.check(vm: LiveSessionViewModel, position: Int, index: Int, checked: Boolean = true) {
        vm.onEvent(LiveSessionEvent.SetChecked(position, index, checked))
        runCurrent()
    }

    @Test
    fun startsLoading_thenShowsTheSessionWithThreeRowsPerExercise() = runSessionTest {
        val vm = newViewModel()
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
    fun customExercisesShowTheirOwnName() = runSessionTest {
        repository.session = sessionOf(SessionExercise("e3", 0, null, listOf(pendingSet(1))))

        val vm = loaded()

        assertEquals("Mi ejercicio", vm.active().exercises.single().name)
    }

    @Test
    fun supersetExercisesCarryTheirLabel() = runSessionTest {
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
    fun noInProgressSession_isNoSession() = runSessionTest {
        repository.session = null

        val vm = loaded()

        assertEquals(LiveSessionUiState.NoSession, vm.uiState.value)
    }

    @Test
    fun loadFailure_isAnErrorThatRetryRecovers() = runSessionTest {
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
    fun exerciseNamesFailing_stillShowsTheSessionWithoutNames() = runSessionTest {
        exercises.failure = AppError.Network

        val vm = loaded()

        assertEquals(listOf(null, null), vm.active().exercises.map { it.name })
    }

    @Test
    fun resumingRestoresTheCheckedRows() = runSessionTest {
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
    fun checkingASet_ticksOnce_andUpsertsItCompletedNow() = runSessionTest {
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
    fun weightAcceptsACommaOrADot() = runSessionTest {
        val vm = loaded()
        type(vm, 0, 1, "87,5", "8")
        type(vm, 0, 2, "87.5", "8")

        check(vm, 0, 1)
        check(vm, 0, 2)

        assertEquals(listOf(87.5, 87.5), repository.upserts.map { (it.entry as SetEntry.WeightReps).weightKg })
    }

    @Test
    fun editingADraftDoesNotSaveAnything() = runSessionTest {
        val vm = loaded()

        type(vm, 0, 1, "60", "12")

        assertTrue(repository.upserts.isEmpty())
        assertEquals("60" to "12", vm.row(0, 1).weight to vm.row(0, 1).reps)
    }

    @Test
    fun uncheckingASet_upsertsItPendingAndMakesItEditable() = runSessionTest {
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
    fun aCompletedRowIgnoresEdits() = runSessionTest {
        val vm = loaded()
        type(vm, 0, 1, "60", "12")
        check(vm, 0, 1)

        type(vm, 0, 1, "99", "99")

        assertEquals("60" to "12", vm.row(0, 1).weight to vm.row(0, 1).reps)
    }

    @Test
    fun checkingWithEmptyReps_isRefusedWithoutHapticOrUpsert() = runSessionTest {
        val vm = loaded()
        type(vm, 0, 1, "60", "")

        check(vm, 0, 1)

        assertTrue(repository.upserts.isEmpty())
        assertEquals(0, haptics.ticks)
        assertFalse(vm.row(0, 1).completed)
        assertTrue(vm.row(0, 1).invalid)
    }

    @Test
    fun checkingWithZeroRepsOrNoWeight_isRefused() = runSessionTest {
        val vm = loaded()
        type(vm, 0, 1, "60", "0")
        type(vm, 0, 2, "", "10")

        check(vm, 0, 1)
        check(vm, 0, 2)

        assertTrue(repository.upserts.isEmpty())
    }

    @Test
    fun aZeroWeightWithRepsIsValid() = runSessionTest {
        val vm = loaded()
        type(vm, 0, 1, "0", "10")

        check(vm, 0, 1)

        assertEquals(SetEntry.WeightReps(0.0, 10), repository.upserts.single().entry)
    }

    @Test
    fun editingARefusedRowClearsTheInvalidMark() = runSessionTest {
        val vm = loaded()
        check(vm, 0, 1)

        type(vm, 0, 1, "60", "10")

        assertFalse(vm.row(0, 1).invalid)
    }

    @Test
    fun addingASet_appendsTheNextIndexWithThePreviousValuesAsPlaceholders() = runSessionTest {
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
    fun addingAnExercise_asksForThePicker_thenAppendsItAtTheEndWithOneEmptySet() = runSessionTest {
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
    fun offlineCheck_marksTheRowPendingWithoutCallingTheServer() = runSessionTest {
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
    fun reconnecting_flushesTheQueueOnceAndClearsTheMarker() = runSessionTest {
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
    fun anUpsertFailureWhileOnline_queuesTheSetAndTheNextReconnectSendsIt() = runSessionTest {
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
    fun anUpsertFailureWhileOnline_isRetriedWithBackoffAndClearsTheMarkerWithoutAConnectivityChange() = runSessionTest {
        repository.upsertFailure = AppError.Network
        val vm = loaded()
        type(vm, 0, 1, "60", "12")
        vm.onEvent(LiveSessionEvent.SetChecked(0, 1, true))
        runCurrent()
        assertTrue(vm.row(0, 1).pendingSave)

        repository.upsertFailure = null
        advanceTimeBy(1_001)
        runCurrent()

        assertEquals(1, repository.upserts.size)
        assertFalse(vm.row(0, 1).pendingSave)
    }

    @Test
    fun retriesAreBounded_soAPersistentFailureStaysPendingAfterThem() = runSessionTest {
        repository.upsertFailure = AppError.Network
        val vm = loaded()
        type(vm, 0, 1, "60", "12")
        check(vm, 0, 1)
        advanceTimeBy(60_000)
        runCurrent()

        repository.upsertFailure = null
        advanceTimeBy(60_000)
        runCurrent()

        assertTrue(repository.upserts.isEmpty())
        assertTrue(vm.row(0, 1).pendingSave)
    }

    @Test
    fun aLaterSuccessfulWriteAlsoSendsTheQueuedOnes() = runSessionTest {
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
    fun twoEditsOfTheSameQueuedSet_sendOnlyTheLast() = runSessionTest {
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
    fun requestingFinish_countsTheIncompleteSets() = runSessionTest {
        val vm = loaded()
        completeBenchSets(vm)

        vm.onEvent(LiveSessionEvent.RequestFinish)

        assertEquals(FinishSheet(pendingCount = 3), vm.active().finishSheet)
    }

    @Test
    fun dismissingTheFinishSheet_closesItWithoutFinishing() = runSessionTest {
        val vm = loaded()
        vm.onEvent(LiveSessionEvent.RequestFinish)

        vm.onEvent(LiveSessionEvent.DismissFinish)

        assertNull(vm.active().finishSheet)
        assertTrue(repository.finished.isEmpty())
    }

    @Test
    fun confirmingFinish_finishesAndShowsTheSummary() = runSessionTest {
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
    fun summaryMarksAFirstEverSessionAsARecord_andNotALowerOneRepMax() = runSessionTest {
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
    fun historyFailure_stillShowsTheSummaryWithoutRecords() = runSessionTest {
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
    fun finishFailure_keepsTheStateAndReportsIt() = runSessionTest {
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
    fun finishingOffline_isBlocked() = runSessionTest {
        connectivity.state.value = false
        val vm = loaded()
        assertTrue(vm.active().actionsBlocked)

        vm.onEvent(LiveSessionEvent.RequestFinish)
        vm.onEvent(LiveSessionEvent.RequestDiscard)

        assertNull(vm.active().finishSheet)
        assertFalse(vm.active().discardConfirm)
    }

    @Test
    fun finishingFlushesTheQueueFirst_andKeepsTheStateIfItCannotBeSent() = runSessionTest {
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
    fun confirmingDiscard_discardsAndCloses() = runSessionTest {
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
    fun dismissingDiscard_keepsTheSession() = runSessionTest {
        val vm = loaded()
        vm.onEvent(LiveSessionEvent.RequestDiscard)

        vm.onEvent(LiveSessionEvent.DismissDiscard)

        assertFalse(vm.active().discardConfirm)
        assertTrue(repository.discarded.isEmpty())
    }

    @Test
    fun discardFailure_keepsTheStateAndReportsIt() = runSessionTest {
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
    fun done_closesTheFlow() = runSessionTest {
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
    fun theStopwatchCountsFromTheStartWithTheInjectedClock() = runSessionTest {
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

    private fun replaceRoutine(vararg rests: Int) {
        routines.routines.clear()
        routines.routines += routineWithRests(*rests)
    }

    // --- Ghost data (US-12) ---

    @Test
    fun ghostIsLoadedOncePerExerciseAtLoad() = runSessionTest {
        repository.session = sessionOf(
            SessionExercise("e1", 0, null, listOf(pendingSet(1))),
            SessionExercise("e1", 1, null, listOf(pendingSet(1))),
            SessionExercise("e2", 2, null, listOf(pendingSet(1))),
        )

        loaded()

        assertEquals(listOf("e1", "e2"), repository.lastPerformanceCalls)
    }

    @Test
    fun ghostFeedsThePreviousTextAndThePlaceholders_andMissingSetsHaveNone() = runSessionTest {
        repository.previous = mapOf("e1" to listOf(PreviousSet(1, 80.0, 10), PreviousSet(2, 85.0, 8)))

        val vm = loaded()

        val first = vm.row(0, 1)
        assertEquals("80 kg × 10", first.previous)
        assertEquals("80" to "10", first.weightPlaceholder to first.repsPlaceholder)
        assertEquals("85 kg × 8", vm.row(0, 2).previous)
        assertNull(vm.row(0, 3).previous)
        assertNull(vm.row(1, 1).previous)
        assertEquals("" to "", first.weight to first.reps)
    }

    @Test
    fun ghostFailure_isTolerated() = runSessionTest {
        repository.lastPerformanceFailure = AppError.Network

        val vm = loaded()

        assertNull(vm.row(0, 1).previous)
        assertEquals(6, vm.active().exercises.sumOf { it.sets.size })
    }

    @Test
    fun usePrevious_fillsTheDraftsAndTheSetIsWrittenOnCheck() = runSessionTest {
        repository.previous = mapOf("e1" to listOf(PreviousSet(1, 80.0, 10), PreviousSet(2, 82.5, 8)))
        val vm = loaded()

        vm.onEvent(LiveSessionEvent.UsePrevious(0, 2))
        runCurrent()

        assertEquals("82,5" to "8", vm.row(0, 2).weight to vm.row(0, 2).reps)
        assertTrue(repository.upserts.isEmpty())
        check(vm, 0, 2)
        assertEquals(SetEntry.WeightReps(82.5, 8), repository.upserts.single().entry)
    }

    @Test
    fun usePrevious_withoutGhostOrOnACompletedSet_changesNothing() = runSessionTest {
        repository.previous = mapOf("e1" to listOf(PreviousSet(1, 80.0, 10)))
        val vm = loaded()
        type(vm, 0, 1, "60", "12")
        check(vm, 0, 1)

        vm.onEvent(LiveSessionEvent.UsePrevious(0, 1))
        vm.onEvent(LiveSessionEvent.UsePrevious(0, 3))
        vm.onEvent(LiveSessionEvent.UsePrevious(1, 1))

        assertEquals("60" to "12", vm.row(0, 1).weight to vm.row(0, 1).reps)
        assertEquals("" to "", vm.row(0, 3).weight to vm.row(0, 3).reps)
    }

    @Test
    fun addingAnExercise_loadsItsGhost() = runSessionTest {
        repository.previous = mapOf("e3" to listOf(PreviousSet(1, 40.0, 12)))
        val vm = loaded()

        vm.onEvent(LiveSessionEvent.ExerciseSelected("e3"))
        advanceUntilIdle()

        assertEquals(listOf("e1", "e2", "e3"), repository.lastPerformanceCalls)
        assertEquals("40 kg × 12", vm.row(2, 1).previous)
    }

    // --- Rest timer (US-13) ---

    private fun TestScope.timerVm(vararg rests: Int): LiveSessionViewModel {
        replaceRoutine(*rests)
        val vm = loaded()
        type(vm, 0, 1, "60", "12")
        return vm
    }

    @Test
    fun noTimerRunsBeforeASetIsChecked() = runSessionTest {
        val vm = timerVm(120, 120)

        assertNull(vm.active().restTimer)
    }

    @Test
    fun checkingASet_startsTheRoutineRest() = runSessionTest {
        val vm = timerVm(90, 120)

        check(vm, 0, 1)

        assertEquals(RestTimerUi(90.seconds, 90.seconds), vm.active().restTimer)
        assertEquals(listOf(90, 120), vm.active().exercises.map { it.restSeconds })
    }

    @Test
    fun zeroRest_startsNoTimer() = runSessionTest {
        val vm = timerVm(0, 120)

        check(vm, 0, 1)

        assertNull(vm.active().restTimer)
    }

    @Test
    fun countdownIsRecomputedFromTheClock_evenAfterABackgroundGap() = runSessionTest {
        val vm = timerVm(120, 120)
        check(vm, 0, 1)

        clock.advance(100.seconds)
        vm.onEvent(LiveSessionEvent.Resume)

        assertEquals(20.seconds, vm.active().restTimer?.remaining)
    }

    @Test
    fun theTickerRefreshesTheRemainingTimeEverySecond() = runSessionTest {
        val vm = timerVm(120, 120)
        check(vm, 0, 1)

        clock.advance(1.seconds)
        advanceTimeBy(1_001)

        assertEquals(119.seconds, vm.active().restTimer?.remaining)
    }

    @Test
    fun add30_extendsWhatIsLeft_andGrowsTheTotalWhenExceeded() = runSessionTest {
        val vm = timerVm(60, 120)
        check(vm, 0, 1)
        clock.advance(40.seconds)

        vm.onEvent(LiveSessionEvent.RestAdd30)

        assertEquals(RestTimerUi(50.seconds, 60.seconds), vm.active().restTimer)
        vm.onEvent(LiveSessionEvent.RestAdd30)
        assertEquals(RestTimerUi(80.seconds, 80.seconds), vm.active().restTimer)
    }

    @Test
    fun skip_removesTheTimerWithoutAlert() = runSessionTest {
        val vm = timerVm(60, 120)
        check(vm, 0, 1)

        vm.onEvent(LiveSessionEvent.RestSkip)
        clock.advance(120.seconds)
        advanceTimeBy(5_000)

        assertNull(vm.active().restTimer)
        assertEquals(0, haptics.alerts)
        assertEquals(0, alertSound.plays)
    }

    @Test
    fun atZero_alertsOnce_andTheTimerDisappears() = runSessionTest {
        val vm = timerVm(30, 120)
        check(vm, 0, 1)

        clock.advance(29.seconds)
        advanceTimeBy(1_001)
        assertEquals(0, haptics.alerts)
        assertEquals(1.seconds, vm.active().restTimer?.remaining)
        clock.advance(1.seconds)
        advanceTimeBy(1_001)
        advanceTimeBy(5_000)

        assertNull(vm.active().restTimer)
        assertEquals(1, haptics.alerts)
        assertEquals(1, alertSound.plays)
    }

    @Test
    fun checkingAnotherSet_restartsTheTimerWithThatExercisesRest() = runSessionTest {
        val vm = timerVm(60, 90)
        check(vm, 0, 1)
        clock.advance(20.seconds)
        type(vm, 1, 1, "40", "10")

        check(vm, 1, 1)

        assertEquals(RestTimerUi(90.seconds, 90.seconds), vm.active().restTimer)
    }

    @Test
    fun uncheckingASet_doesNotStopTheTimer() = runSessionTest {
        val vm = timerVm(60, 120)
        check(vm, 0, 1)

        check(vm, 0, 1, checked = false)

        assertEquals(60.seconds, vm.active().restTimer?.remaining)
    }

    @Test
    fun supersetMembers_useTheirOwnRest_transitionThenBetweenRounds() = runSessionTest {
        repository.session = sessionOf(
            SessionExercise("e1", 0, 1, listOf(pendingSet(1))),
            SessionExercise("e2", 1, 1, listOf(pendingSet(1))),
        )
        replaceRoutine(0, 90)
        val vm = loaded()
        type(vm, 0, 1, "60", "12")
        type(vm, 1, 1, "40", "10")

        check(vm, 0, 1)
        assertNull(vm.active().restTimer)
        check(vm, 1, 1)

        assertEquals(90.seconds, vm.active().restTimer?.remaining)
    }

    @Test
    fun exercisesAddedDuringTheSession_rest120Seconds() = runSessionTest {
        val vm = loaded()
        vm.onEvent(LiveSessionEvent.ExerciseSelected("e3"))
        advanceUntilIdle()
        type(vm, 2, 1, "40", "10")

        check(vm, 2, 1)

        assertEquals(120.seconds, vm.active().restTimer?.remaining)
        assertEquals(120, vm.active().exercises[2].restSeconds)
    }

    @Test
    fun aRoutineReadFailure_fallsBackTo120Seconds() = runSessionTest {
        routines.failure = AppError.Network
        val vm = loaded()
        type(vm, 0, 1, "60", "12")

        check(vm, 0, 1)

        assertEquals(120.seconds, vm.active().restTimer?.remaining)
    }

    @Test
    fun aSessionOverride_appliesToFollowingSetsOnly_andNeverSavesTheRoutine() = runSessionTest {
        val vm = timerVm(60, 120)
        check(vm, 0, 1)
        assertEquals(60.seconds, vm.active().restTimer?.remaining)

        vm.onEvent(LiveSessionEvent.SetExerciseRest(0, 90))
        assertEquals(60.seconds, vm.active().restTimer?.remaining)
        type(vm, 0, 2, "60", "12")
        check(vm, 0, 2)

        assertEquals(90.seconds, vm.active().restTimer?.remaining)
        assertEquals(90, vm.active().exercises[0].restSeconds)
        assertTrue(routines.saved.isEmpty())
    }

    @Test
    fun aRestOverride_isClampedToTheRoutineBounds() = runSessionTest {
        val vm = loaded()

        vm.onEvent(LiveSessionEvent.SetExerciseRest(0, 9_999))
        assertEquals(600, vm.active().exercises[0].restSeconds)
        vm.onEvent(LiveSessionEvent.SetExerciseRest(0, -5))
        assertEquals(0, vm.active().exercises[0].restSeconds)
    }

    @Test
    fun finishing_removesTheTimer() = runSessionTest {
        val vm = timerVm(60, 120)
        check(vm, 0, 1)

        vm.onEvent(LiveSessionEvent.RequestFinish)
        vm.onEvent(LiveSessionEvent.ConfirmFinish)
        runCurrent()
        clock.advance(120.seconds)
        advanceTimeBy(5_000)

        assertEquals(0, haptics.alerts)
    }

    private companion object {
        fun routineWithRests(vararg rests: Int) = Routine(
            id = "r1",
            name = "Torso",
            notes = null,
            archived = false,
            exercises = rests.mapIndexed { index, rest ->
                RoutineExercise("e${index + 1}", index, null, 3, 8, rest)
            },
        )

        fun twoExercises() = sessionOf(
            SessionExercise("e1", 0, null, listOf(pendingSet(1), pendingSet(2), pendingSet(3))),
            SessionExercise("e2", 1, null, listOf(pendingSet(1), pendingSet(2), pendingSet(3))),
        )
    }
}
