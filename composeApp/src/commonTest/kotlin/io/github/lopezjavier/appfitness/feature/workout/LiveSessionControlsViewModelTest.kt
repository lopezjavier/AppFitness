package io.github.lopezjavier.appfitness.feature.workout

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeExerciseOptionsRepository
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.PreviousValuesMode
import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import io.github.lopezjavier.appfitness.domain.model.TrainingPreferences
import io.github.lopezjavier.appfitness.domain.usecase.SaveBodyMeasurement
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import io.github.lopezjavier.appfitness.feature.routines.FakeRoutineRepository
import io.github.lopezjavier.appfitness.feature.weight.FakeWeightRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** US-38: pause and resume, finishing an empty workout, deleting a set and the previous-values mode. */
class LiveSessionControlsViewModelTest {
    private val repository = FakeWorkoutSessionRepository(
        sessionOf(
            SessionExercise("e1", 0, null, listOf(pendingSet(1), pendingSet(2), pendingSet(3), pendingSet(4))),
            SessionExercise("e2", 1, null, listOf(pendingSet(1))),
        ),
    )
    private val exercises = FakeExerciseRepository(listOf(exerciseRow("e1", "bench"), exerciseRow("e2", "squat")))
    private val connectivity = FakeConnectivityObserver()
    private val clock = MutableClock()
    private val trainingPreferences = FakeTrainingPreferencesRepository()
    private val bodyMeasurements = FakeWeightRepository()
    private var created: LiveSessionViewModel? = null

    private fun newViewModel(savedState: SavedStateHandle = SavedStateHandle()) = LiveSessionViewModel(
        repository,
        exercises,
        FakeExerciseLibrary(emptyMap()),
        connectivity,
        clock,
        FakeHaptics(),
        FakeRoutineRepository(),
        FakeAlertSound(),
        trainingPreferences,
        bodyMeasurements,
        FakeExerciseOptionsRepository(),
        SaveBodyMeasurement(bodyMeasurements, clock, TimeZone.UTC),
        TimeZone.UTC,
        savedState,
    ).also { created = it }

    private fun runSessionTest(body: suspend TestScope.() -> Unit) = runTest {
        try {
            body()
        } finally {
            created?.onEvent(LiveSessionEvent.RestSkip)
            runCurrent()
        }
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        repository.history = mapOf("e1" to emptyList(), "e2" to emptyList())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.loaded(savedState: SavedStateHandle = SavedStateHandle()): LiveSessionViewModel {
        val vm = newViewModel(savedState)
        advanceUntilIdle()
        return vm
    }

    private fun LiveSessionViewModel.active() = assertIs<LiveSessionUiState.Active>(uiState.value)

    private fun LiveSessionViewModel.row(position: Int, setIndex: Int) =
        active().exercises[position].sets.first { it.setIndex == setIndex }

    private fun LiveSessionViewModel.indexes(position: Int = 0) = active().exercises[position].sets.map { it.setIndex }

    private fun TestScope.type(vm: LiveSessionViewModel, position: Int, index: Int, weight: String, reps: String) {
        vm.onEvent(LiveSessionEvent.WeightChanged(position, index, weight))
        vm.onEvent(LiveSessionEvent.RepsChanged(position, index, reps))
        runCurrent()
    }

    private fun TestScope.check(vm: LiveSessionViewModel, position: Int, index: Int) {
        vm.onEvent(LiveSessionEvent.SetChecked(position, index, true))
        vm.onEvent(LiveSessionEvent.SetRpeChosen(position, index, 8.0)) // Answers the RPE prompt (ADR-55).
        runCurrent()
    }

    private fun TestScope.send(vm: LiveSessionViewModel, event: LiveSessionEvent) {
        vm.onEvent(event)
        runCurrent()
    }

    // --- Pause and resume ---

    @Test
    fun pausing_marksTheSessionPausedAndTellsTheServer() = runSessionTest {
        val vm = loaded()
        clock.advance(10.minutes)

        send(vm, LiveSessionEvent.PauseSession)

        assertTrue(vm.active().isPaused)
        assertEquals(SESSION_START + 10.minutes, vm.active().pausedAt)
        assertEquals(listOf("s1" to SESSION_START + 10.minutes), repository.pauses)
    }

    @Test
    fun resuming_addsThePausedSpanToTheTotalAndTellsTheServer() = runSessionTest {
        val vm = loaded()
        clock.advance(10.minutes)
        send(vm, LiveSessionEvent.PauseSession)
        clock.advance(5.minutes)

        send(vm, LiveSessionEvent.ResumeSession)

        assertFalse(vm.active().isPaused)
        assertNull(vm.active().pausedAt)
        assertEquals(300L, vm.active().pausedTotalSeconds)
        assertEquals(listOf("s1" to SESSION_START + 15.minutes), repository.resumes)
    }

    @Test
    fun theElapsedTime_freezesWhilePausedAndContinuesFromTheSameValue() = runSessionTest {
        val vm = loaded()
        clock.advance(10.minutes)

        vm.elapsedSeconds.test {
            assertEquals(600L, awaitItem())
            send(vm, LiveSessionEvent.PauseSession)
            clock.advance(5.minutes)
            advanceTimeBy(2_000)
            expectNoEvents()
            send(vm, LiveSessionEvent.ResumeSession)
            clock.advance(1.seconds)
            advanceTimeBy(1_001)
            assertEquals(601L, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun pausingTwice_keepsTheFirstPauseInstant() = runSessionTest {
        val vm = loaded()
        send(vm, LiveSessionEvent.PauseSession)
        clock.advance(1.minutes)

        send(vm, LiveSessionEvent.PauseSession)

        assertEquals(SESSION_START, vm.active().pausedAt)
        assertEquals(1, repository.pauses.size)
    }

    @Test
    fun resumingASessionThatIsNotPaused_doesNothing() = runSessionTest {
        val vm = loaded()

        send(vm, LiveSessionEvent.ResumeSession)

        assertTrue(repository.resumes.isEmpty())
        assertEquals(0L, vm.active().pausedTotalSeconds)
    }

    @Test
    fun aPause_survivesARecreatedViewModelWithTheSameSavedState() = runSessionTest {
        val handle = SavedStateHandle()
        val first = loaded(handle)
        clock.advance(10.minutes)
        send(first, LiveSessionEvent.PauseSession)
        clock.advance(5.minutes)

        val second = loaded(handle)

        assertTrue(second.active().isPaused)
        assertEquals(SESSION_START + 10.minutes, second.active().pausedAt)
        second.elapsedSeconds.test {
            assertEquals(600L, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aSessionReopenedFromTheServer_keepsItsPauseState() = runSessionTest {
        repository.session = repository.session!!.copy(
            pausedAt = SESSION_START + 10.minutes,
            pausedTotalSeconds = 120,
        )
        clock.advance(30.minutes)

        val vm = loaded()

        assertTrue(vm.active().isPaused)
        assertEquals(120L, vm.active().pausedTotalSeconds)
        vm.elapsedSeconds.test {
            assertEquals(480L, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Pause freezes the rest countdown (ADR-56) ---

    private fun TestScope.restRunning(handle: SavedStateHandle = SavedStateHandle()): LiveSessionViewModel {
        val vm = loaded(handle)
        type(vm, 0, 1, "60", "10")
        check(vm, 0, 1)
        clock.advance(70.seconds) // 120 s default rest: 50 s left
        return vm
    }

    @Test
    fun pausing_freezesTheRestCountdown() = runSessionTest {
        val vm = restRunning()

        send(vm, LiveSessionEvent.PauseSession)
        clock.advance(30.seconds)
        advanceTimeBy(5000)
        runCurrent()

        assertEquals(RestTimerUi(50.seconds, 120.seconds, paused = true), vm.active().restTimer)
    }

    @Test
    fun resuming_runsTheFrozenCountdownAgain() = runSessionTest {
        val vm = restRunning()
        send(vm, LiveSessionEvent.PauseSession)
        clock.advance(30.seconds)

        send(vm, LiveSessionEvent.ResumeSession)
        clock.advance(10.seconds)
        advanceTimeBy(1001)

        assertEquals(RestTimerUi(40.seconds, 120.seconds), vm.active().restTimer)
    }

    @Test
    fun markingASetDoneWhilePaused_resumesTheSessionAndStartsANewRest() = runSessionTest {
        val vm = restRunning()
        send(vm, LiveSessionEvent.PauseSession)
        clock.advance(30.seconds)
        type(vm, 0, 2, "60", "10")

        check(vm, 0, 2)

        assertNull(vm.active().pausedAt)
        assertEquals(30L, vm.active().pausedTotalSeconds)
        assertEquals(RestTimerUi(120.seconds, 120.seconds), vm.active().restTimer)
        assertEquals(1, repository.resumes.size)
    }

    @Test
    fun aResumeTheServerRefuses_keepsTheCountdownFrozen() = runSessionTest {
        val vm = restRunning()
        send(vm, LiveSessionEvent.PauseSession)
        repository.resumeFailure = AppError.Network
        clock.advance(30.seconds)

        send(vm, LiveSessionEvent.ResumeSession)
        clock.advance(30.seconds)
        advanceTimeBy(2000)

        assertTrue(vm.active().isPaused)
        assertEquals(RestTimerUi(50.seconds, 120.seconds, paused = true), vm.active().restTimer)
    }

    @Test
    fun markingASetDoneWhilePausedOffline_keepsTheSessionPausedAndStartsNoRunningRest() = runSessionTest {
        val vm = restRunning()
        send(vm, LiveSessionEvent.PauseSession)
        connectivity.state.value = false
        clock.advance(30.seconds)
        type(vm, 0, 2, "60", "10")

        check(vm, 0, 2)
        clock.advance(30.seconds)
        advanceTimeBy(2000)

        assertTrue(vm.active().isPaused)
        assertTrue(vm.row(0, 2).completed)
        assertEquals(RestTimerUi(50.seconds, 120.seconds, paused = true), vm.active().restTimer)
    }

    @Test
    fun aResumeRefusedAfterMarkingASetDone_doesNotDiscardTheNewRest() = runSessionTest {
        val vm = restRunning()
        send(vm, LiveSessionEvent.PauseSession)
        repository.resumeFailure = AppError.Network
        clock.advance(30.seconds)
        type(vm, 0, 2, "60", "10")

        check(vm, 0, 2)
        advanceUntilIdle()

        assertTrue(vm.active().isPaused)
        assertEquals(RestTimerUi(120.seconds, 120.seconds, paused = true), vm.active().restTimer)
    }

    @Test
    fun aPauseTheServerRefuses_leavesTheCountdownRunning() = runSessionTest {
        val vm = restRunning()
        repository.pauseFailure = AppError.Network

        send(vm, LiveSessionEvent.PauseSession)
        clock.advance(10.seconds)
        advanceTimeBy(1001)

        assertFalse(vm.active().isPaused)
        assertEquals(RestTimerUi(40.seconds, 120.seconds), vm.active().restTimer)
    }

    @Test
    fun pausingWithoutARest_changesNothingElseAndResumeWorks() = runSessionTest {
        val vm = loaded()

        send(vm, LiveSessionEvent.PauseSession)
        assertNull(vm.active().restTimer)
        send(vm, LiveSessionEvent.ResumeSession)

        assertNull(vm.active().restTimer)
        assertNull(vm.active().pausedAt)
    }

    @Test
    fun theFrozenCountdown_survivesARecreatedViewModel() = runSessionTest {
        val handle = SavedStateHandle()
        val first = restRunning(handle)
        send(first, LiveSessionEvent.PauseSession)
        clock.advance(30.seconds)

        val second = loaded(handle)
        advanceTimeBy(5000)

        assertTrue(second.active().isPaused)
        assertEquals(RestTimerUi(50.seconds, 120.seconds, paused = true), second.active().restTimer)
        assertEquals(50_000L, handle.get<Long>("live_rest_paused_remaining"))
    }

    @Test
    fun pausingOffline_isBlocked() = runSessionTest {
        connectivity.state.value = false
        val vm = loaded()

        send(vm, LiveSessionEvent.PauseSession)

        assertFalse(vm.active().isPaused)
        assertTrue(repository.pauses.isEmpty())
    }

    @Test
    fun aPauseTheServerRefuses_isRevertedAndReported() = runSessionTest {
        repository.pauseFailure = AppError.Network
        val vm = loaded()

        vm.effects.test {
            send(vm, LiveSessionEvent.PauseSession)

            assertEquals(LiveSessionEffect.ActionFailed, awaitItem())
        }
        assertFalse(vm.active().isPaused)
    }

    @Test
    fun aResumeTheServerRefuses_staysPausedAndIsReported() = runSessionTest {
        val vm = loaded()
        send(vm, LiveSessionEvent.PauseSession)
        repository.resumeFailure = AppError.Network

        vm.effects.test {
            send(vm, LiveSessionEvent.ResumeSession)

            assertEquals(LiveSessionEffect.ActionFailed, awaitItem())
        }
        assertTrue(vm.active().isPaused)
        assertEquals(0L, vm.active().pausedTotalSeconds)
    }

    @Test
    fun theSummaryDuration_excludesPausedTimeIncludingAPauseOpenAtFinish() = runSessionTest {
        val vm = loaded()
        type(vm, 0, 1, "60", "10")
        check(vm, 0, 1)
        clock.advance(10.minutes)
        send(vm, LiveSessionEvent.PauseSession)
        clock.advance(5.minutes)
        send(vm, LiveSessionEvent.RequestFinish)

        send(vm, LiveSessionEvent.ConfirmFinish)
        advanceUntilIdle()

        val summary = assertIs<LiveSessionUiState.Finished>(vm.uiState.value).summary.summary
        assertEquals(600L, summary.durationSeconds)
    }

    // --- Finishing an empty workout ---

    @Test
    fun canFinish_isFalseWithoutCompletedSets_andTrueAfterOne() = runSessionTest {
        val vm = loaded()
        assertFalse(vm.active().canFinish)

        type(vm, 0, 1, "60", "10")
        assertFalse(vm.active().canFinish)
        check(vm, 0, 1)

        assertTrue(vm.active().canFinish)
    }

    @Test
    fun requestingFinishWithoutCompletedSets_isIgnored() = runSessionTest {
        val vm = loaded()

        send(vm, LiveSessionEvent.RequestFinish)

        assertNull(vm.active().finishSheet)
    }

    // --- Deleting a set ---

    @Test
    fun deletingSetTwoOfFour_removesItAtOnceAndReindexesTheRest() = runSessionTest {
        val vm = loaded()
        type(vm, 0, 1, "10", "1")
        type(vm, 0, 3, "30", "3")
        type(vm, 0, 4, "40", "4")

        vm.onEvent(LiveSessionEvent.DeleteSet(0, 2))

        assertEquals(listOf(1, 2, 3), vm.indexes())
        assertEquals(listOf("10", "30", "40"), vm.active().exercises[0].sets.map { it.weight })
        runCurrent()
        assertEquals(listOf(0 to 2), repository.deletedSets)
    }

    @Test
    fun deletingASet_leavesOtherExercisesAlone() = runSessionTest {
        val vm = loaded()

        send(vm, LiveSessionEvent.DeleteSet(0, 2))

        assertEquals(listOf(1), vm.indexes(1))
    }

    @Test
    fun deletingTheLastSet_justRemovesIt() = runSessionTest {
        val vm = loaded()

        send(vm, LiveSessionEvent.DeleteSet(0, 4))

        assertEquals(listOf(1, 2, 3), vm.indexes())
        assertEquals(listOf(0 to 4), repository.deletedSets)
    }

    @Test
    fun deletingTheOnlySetOfAnExercise_isAllowed() = runSessionTest {
        val vm = loaded()

        send(vm, LiveSessionEvent.DeleteSet(1, 1))

        assertTrue(vm.active().exercises[1].sets.isEmpty())
        assertEquals(listOf(1 to 1), repository.deletedSets)
    }

    @Test
    fun deletingAMissingSet_changesNothing() = runSessionTest {
        val vm = loaded()

        send(vm, LiveSessionEvent.DeleteSet(0, 9))

        assertEquals(listOf(1, 2, 3, 4), vm.indexes())
        assertTrue(repository.deletedSets.isEmpty())
    }

    @Test
    fun theQueuedWritesGoBeforeTheDeletion_inTheKeysTheServerStillHas() = runSessionTest {
        connectivity.state.value = false
        val vm = loaded()
        type(vm, 0, 3, "30", "3")
        check(vm, 0, 3)
        type(vm, 0, 2, "20", "2")
        check(vm, 0, 2)

        send(vm, LiveSessionEvent.DeleteSet(0, 2))
        connectivity.state.value = true
        runCurrent() // Not advanceUntilIdle: the rest ticker of a checked set never goes idle.

        // Set 3 was queued under its old index; the deleted set 2 is never written.
        assertEquals(listOf("upsert:0:3", "delete:0:2"), repository.serverLog)
    }

    @Test
    fun aWriteAfterTheDeletion_usesTheNewIndexAndGoesAfterIt() = runSessionTest {
        connectivity.state.value = false
        val vm = loaded()
        send(vm, LiveSessionEvent.DeleteSet(0, 2))
        type(vm, 0, 2, "30", "3") // Former set 3, now set 2.
        check(vm, 0, 2)

        connectivity.state.value = true
        runCurrent()

        assertEquals(listOf("delete:0:2", "upsert:0:2"), repository.serverLog)
    }

    @Test
    fun theUnsavedMarker_followsTheReindexedSet() = runSessionTest {
        connectivity.state.value = false
        val vm = loaded()
        type(vm, 0, 3, "30", "3")
        check(vm, 0, 3)
        assertTrue(vm.row(0, 3).pendingSave)

        send(vm, LiveSessionEvent.DeleteSet(0, 1))

        assertTrue(vm.row(0, 2).pendingSave)
        assertFalse(vm.row(0, 3).pendingSave)
    }

    @Test
    fun aDeletionWhileOffline_isSentOnReconnect() = runSessionTest {
        connectivity.state.value = false
        val vm = loaded()

        send(vm, LiveSessionEvent.DeleteSet(0, 2))
        assertTrue(repository.deletedSets.isEmpty())
        assertEquals(listOf(1, 2, 3), vm.indexes())

        connectivity.state.value = true
        advanceUntilIdle()

        assertEquals(listOf(0 to 2), repository.deletedSets)
    }

    @Test
    fun aFailedDeletion_isReportedKeepsTheRowRemovedAndIsRetried() = runSessionTest {
        repository.deleteSetFailure = AppError.Network
        val vm = loaded()

        vm.effects.test {
            send(vm, LiveSessionEvent.DeleteSet(0, 2))

            assertEquals(LiveSessionEffect.ActionFailed, awaitItem())
        }
        assertEquals(listOf(1, 2, 3), vm.indexes())
        assertTrue(repository.deletedSets.isEmpty())

        repository.deleteSetFailure = null
        advanceUntilIdle()

        assertEquals(listOf(0 to 2), repository.deletedSets)
    }

    @Test
    fun twoDeletions_reachTheServerInOrderEachInItsOwnKeys() = runSessionTest {
        val vm = loaded()

        send(vm, LiveSessionEvent.DeleteSet(0, 2))
        send(vm, LiveSessionEvent.DeleteSet(0, 2))

        assertEquals(listOf(1, 2), vm.indexes())
        assertEquals(listOf(0 to 2, 0 to 2), repository.deletedSets)
    }

    @Test
    fun finishing_sendsThePendingDeletionsFirst() = runSessionTest {
        val vm = loaded()
        type(vm, 0, 1, "60", "10")
        check(vm, 0, 1)
        repository.serverLog.clear()
        connectivity.state.value = false
        runCurrent()
        send(vm, LiveSessionEvent.DeleteSet(0, 3))
        repository.deleteSetFailure = AppError.Network // The reconnect flush fails; finishing must send it.
        connectivity.state.value = true
        runCurrent()
        repository.deleteSetFailure = null
        send(vm, LiveSessionEvent.RequestFinish)

        send(vm, LiveSessionEvent.ConfirmFinish)
        runCurrent()

        assertEquals(listOf("delete:0:3"), repository.serverLog)
        assertEquals(listOf("s1"), repository.finished)
    }

    // --- Previous-values mode ---

    @Test
    fun overallMode_asksForTheLastPerformanceOfAnyRoutine() = runSessionTest {
        loaded()

        assertEquals(listOf<String?>(null, null), repository.lastPerformanceRoutineIds)
    }

    @Test
    fun routineMode_asksForTheLastPerformanceInTheSessionsRoutine() = runSessionTest {
        trainingPreferences.result =
            Result.success(TrainingPreferences(previousValuesMode = PreviousValuesMode.Routine))

        loaded()

        assertEquals(listOf<String?>("r1", "r1"), repository.lastPerformanceRoutineIds)
    }

    @Test
    fun routineMode_inAFreeSession_fallsBackToAnyRoutine() = runSessionTest {
        trainingPreferences.result =
            Result.success(TrainingPreferences(previousValuesMode = PreviousValuesMode.Routine))
        repository.session = repository.session!!.copy(routineId = null)

        loaded()

        assertEquals(listOf<String?>(null, null), repository.lastPerformanceRoutineIds)
    }

    @Test
    fun aFailedPreferenceRead_usesTheOverallMode() = runSessionTest {
        trainingPreferences.result = Result.failure(IllegalStateException("offline"))

        loaded()

        assertEquals(listOf<String?>(null, null), repository.lastPerformanceRoutineIds)
    }
}
