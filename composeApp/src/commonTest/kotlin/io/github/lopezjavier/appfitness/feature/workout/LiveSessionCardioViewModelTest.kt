package io.github.lopezjavier.appfitness.feature.workout

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.MovementPattern
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.PlannedSet
import io.github.lopezjavier.appfitness.domain.model.Routine
import io.github.lopezjavier.appfitness.domain.model.RoutineExercise
import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.WorkoutSet
import io.github.lopezjavier.appfitness.domain.port.RestAlertKind
import io.github.lopezjavier.appfitness.domain.usecase.SaveBodyMeasurement
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** T-183: the cardio set of a routine is planned in minutes, shown as a placeholder, and starts no rest timer. */
class LiveSessionCardioViewModelTest {
    private val repository = FakeWorkoutSessionRepository(
        sessionOf(
            SessionExercise("e1", 0, null, listOf(pendingSet(1))),
            SessionExercise(
                "bike",
                1,
                null,
                listOf(WorkoutSet(1, SetType.Working, SetEntry.Duration(0), null)),
            ),
        ),
    )
    private val exercises = FakeExerciseRepository(
        listOf(
            exerciseRow("e1", "bench"),
            exerciseRow(
                "bike",
                "bike",
                MuscleGroup.FullBody,
                metric = ExerciseMetricType.Duration,
                pattern = MovementPattern.Cardio,
            ),
        ),
    )
    private val library = FakeExerciseLibrary(
        mapOf("bench" to exerciseInfo("bench", "Press banca"), "bike" to exerciseInfo("bike", "Bicicleta")),
    )
    private val clock = MutableClock()
    private var routines = FakeRoutineRepository(
        listOf(
            Routine(
                id = "r1",
                name = "Torso",
                notes = null,
                archived = false,
                exercises = listOf(
                    RoutineExercise("e1", 0, null, listOf(PlannedSet(null, 8)), restSeconds = 90),
                    RoutineExercise("bike", 1, null, listOf(PlannedSet.ofMinutes(50)), restSeconds = 90),
                ),
            ),
        ),
    )
    private val created = mutableListOf<LiveSessionViewModel>()
    private val scheduler = FakeRestAlertScheduler()
    private val haptics = FakeHaptics()
    private val sound = FakeAlertSound()
    private val savedState = SavedStateHandle()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.loaded(handle: SavedStateHandle = savedState): LiveSessionViewModel {
        val bodyMeasurements = FakeWeightRepository()
        val vm = LiveSessionViewModel(
            repository,
            exercises,
            library,
            FakeConnectivityObserver(),
            clock,
            haptics,
            routines,
            sound,
            FakeTrainingPreferencesRepository(),
            bodyMeasurements,
            SaveBodyMeasurement(bodyMeasurements, clock, TimeZone.UTC),
            TimeZone.UTC,
            handle,
            scheduler,
            FakeNotificationPermission(),
            FakeRestAlertPreferences(),
        )
        // A second view model is loaded while the first one's running ticker never idles: only run what is due.
        if (created.isEmpty()) advanceUntilIdle() else runCurrent()
        created += vm
        return vm
    }

    private fun LiveSessionViewModel.active() = assertIs<LiveSessionUiState.Active>(uiState.value)

    private fun runSessionTest(body: suspend TestScope.() -> Unit) = runTest {
        body()
        // A running ticker never idles, and runTest waits for that: leave no rest or stopwatch running.
        created.forEach { vm ->
            vm.onEvent(LiveSessionEvent.RestSkip)
            (vm.uiState.value as? LiveSessionUiState.Active)?.timerBar
                ?.let { it as? TimerBarUi.Cardio }?.takeUnless { it.paused }
                ?.let { vm.onEvent(LiveSessionEvent.StopwatchToggled(it.position, it.setIndex)) }
        }
        runCurrent()
    }

    @Test
    fun theCardioSetExposesItsPlannedMinutes_andALiftingSetDoesNot() = runSessionTest {
        val vm = loaded()

        assertEquals(50, vm.active().exercises[1].sets.single().plannedDurationMinutes)
        assertNull(vm.active().exercises[0].sets.single().plannedDurationMinutes)
    }

    @Test
    fun completingTheCardioSet_startsNoRestTimer_andLogsThePlannedMinutes() = runSessionTest {
        val vm = loaded()

        vm.onEvent(LiveSessionEvent.SetChecked(1, 1, true))
        runCurrent()

        val row = vm.active().exercises[1].sets.single()
        assertTrue(row.completed)
        assertNull(vm.active().rpePicker)
        assertEquals(3000, row.durationSeconds)
        assertNull(vm.active().restTimer)
        assertNotNull(repository.session)
    }

    @Test
    fun completingALiftingSet_stillStartsTheRestTimer() = runSessionTest {
        val vm = loaded()
        vm.onEvent(LiveSessionEvent.WeightChanged(0, 1, "60"))
        vm.onEvent(LiveSessionEvent.RepsChanged(0, 1, "8"))
        runCurrent()

        vm.onEvent(LiveSessionEvent.SetChecked(0, 1, true))
        runCurrent()
        vm.onEvent(LiveSessionEvent.SetRpeChosen(0, 1, 8.0))
        runCurrent()

        assertNotNull(vm.active().restTimer)
    }

    private fun TestScope.elapse(by: Duration) {
        clock.advance(by)
        advanceTimeBy(by)
        runCurrent()
    }

    private fun LiveSessionViewModel.toggleBike() = onEvent(LiveSessionEvent.StopwatchToggled(1, 1))

    private fun LiveSessionViewModel.cardioBar() = assertIs<TimerBarUi.Cardio>(active().timerBar)

    private fun LiveSessionViewModel.startRestOnBench() {
        onEvent(LiveSessionEvent.WeightChanged(0, 1, "60"))
        onEvent(LiveSessionEvent.RepsChanged(0, 1, "8"))
        onEvent(LiveSessionEvent.SetChecked(0, 1, true))
        onEvent(LiveSessionEvent.SetRpeChosen(0, 1, 8.0))
    }

    @Test
    fun startingTheRun_showsACardioBar_andSchedulesTheAlertAtTheTarget() = runSessionTest {
        val vm = loaded()
        val start = clock.current

        vm.toggleBike()
        runCurrent()

        val bar = vm.cardioBar()
        assertEquals(Duration.ZERO, bar.elapsed)
        assertEquals(50.minutes, bar.target)
        assertEquals("Bicicleta", bar.exerciseName)
        assertFalse(bar.paused)
        assertEquals(start + 50.minutes, scheduler.scheduled)
        assertEquals(RestAlertKind.Cardio, scheduler.scheduledKind)
    }

    @Test
    fun startingTheRun_dropsARunningRest() = runSessionTest {
        val vm = loaded()
        vm.startRestOnBench()
        runCurrent()
        assertIs<TimerBarUi.Rest>(vm.active().timerBar)

        vm.toggleBike()
        runCurrent()

        assertIs<TimerBarUi.Cardio>(vm.active().timerBar)
        assertEquals(clock.current + 50.minutes, scheduler.scheduled)
    }

    @Test
    fun pausing_freezesElapsed_cancelsTheAlert_andResumingReschedulesWithWhatIsLeft() = runSessionTest {
        val vm = loaded()
        vm.toggleBike()
        elapse(10.minutes)

        vm.toggleBike()
        runCurrent()
        elapse(5.minutes)

        assertEquals(
            TimerBarUi.Cardio(1, 1, "Bicicleta", 10.minutes, 50.minutes, paused = true),
            vm.active().timerBar,
        )
        assertNull(scheduler.scheduled)

        vm.toggleBike()
        runCurrent()

        assertFalse(vm.cardioBar().paused)
        assertEquals(clock.current + 40.minutes, scheduler.scheduled)
    }

    @Test
    fun targetReachedInTheForeground_alertsOnce_andKeepsCounting() = runSessionTest {
        val vm = loaded()
        vm.toggleBike()

        elapse(51.minutes)

        assertEquals(1, haptics.alerts)
        assertEquals(1, sound.plays)
        assertNull(scheduler.scheduled)
        assertEquals(51.minutes, vm.cardioBar().elapsed)
        assertFalse(vm.cardioBar().paused)
    }

    @Test
    fun targetReachedInTheBackground_playsNothingLocally_andTheScheduledAlertIsTheTarget() = runSessionTest {
        val vm = loaded()
        val start = clock.current
        vm.toggleBike()
        vm.onEvent(LiveSessionEvent.Pause)

        elapse(51.minutes)

        assertEquals(0, haptics.alerts)
        assertEquals(0, sound.plays)
        assertEquals(start + 50.minutes, scheduler.scheduled)

        vm.onEvent(LiveSessionEvent.Resume)
        elapse(1.minutes)
        assertEquals(0, haptics.alerts)
    }

    @Test
    fun listo_savesTheElapsedTimeWithoutPausedTime_cancelsTheAlert_clearsTheBar_andStartsNoRest() = runSessionTest {
        val vm = loaded()
        vm.toggleBike()
        elapse(20.minutes)
        vm.toggleBike()
        runCurrent()
        elapse(10.minutes)
        vm.toggleBike()
        runCurrent()
        elapse(5.minutes)

        vm.onEvent(LiveSessionEvent.SetChecked(1, 1, true))
        runCurrent()

        val row = vm.active().exercises[1].sets.single()
        assertTrue(row.completed)
        assertEquals(25 * 60, row.durationSeconds)
        assertNull(vm.active().timerBar)
        assertNull(scheduler.scheduled)
    }

    @Test
    fun aRestTakesTheBar_andTheRunReturnsWithItsAlertWhenTheRestIsSkipped() = runSessionTest {
        val vm = loaded()
        vm.toggleBike()
        elapse(10.minutes)

        vm.startRestOnBench()
        runCurrent()
        assertIs<TimerBarUi.Rest>(vm.active().timerBar)
        assertEquals(clock.current + 90.seconds, scheduler.scheduled)

        vm.onEvent(LiveSessionEvent.RestSkip)
        runCurrent()

        assertEquals(10.minutes, vm.cardioBar().elapsed)
        assertEquals(clock.current + 40.minutes, scheduler.scheduled)
    }

    @Test
    fun withoutAPlannedTarget_noAlertIsScheduled_andTheBarHasNoTarget() = runSessionTest {
        routines = FakeRoutineRepository(emptyList())
        val vm = loaded()

        vm.toggleBike()
        elapse(60.minutes)

        assertNull(scheduler.scheduled)
        assertNull(vm.cardioBar().target)
        assertEquals(0, haptics.alerts)
    }

    @Test
    fun pausingTheSession_freezesTheRun_andResumingRestartsIt() = runSessionTest {
        val vm = loaded()
        vm.toggleBike()
        elapse(10.minutes)

        vm.onEvent(LiveSessionEvent.PauseSession)
        runCurrent()
        elapse(30.minutes)

        assertEquals(
            TimerBarUi.Cardio(1, 1, "Bicicleta", 10.minutes, 50.minutes, paused = true),
            vm.active().timerBar,
        )
        assertNull(scheduler.scheduled)

        vm.onEvent(LiveSessionEvent.ResumeSession)
        runCurrent()
        elapse(5.minutes)

        assertEquals(15.minutes, vm.cardioBar().elapsed)
        assertFalse(vm.cardioBar().paused)
        assertEquals(clock.current + 35.minutes, scheduler.scheduled)
    }

    @Test
    fun aRecreatedViewModel_restoresTheRunPausedStateAndTheFiredFlag() = runSessionTest {
        val first = loaded()
        first.toggleBike()
        elapse(51.minutes)
        first.toggleBike()
        runCurrent()
        assertEquals(1, haptics.alerts)

        val second = loaded(savedState)
        elapse(1.minutes)

        val bar = second.cardioBar()
        assertEquals(51.minutes, bar.elapsed)
        assertTrue(bar.paused)
        assertEquals(1 to 1, bar.position to bar.setIndex)

        second.toggleBike()
        runCurrent()
        elapse(1.minutes)

        assertEquals(1, haptics.alerts)
        assertEquals(52.minutes, second.cardioBar().elapsed)
    }

    @Test
    fun aRecreatedViewModel_withARunningRun_keepsCountingAndReschedulesTheAlert() = runSessionTest {
        val first = loaded()
        first.toggleBike()
        elapse(10.minutes)

        val second = loaded(savedState)
        runCurrent()

        assertEquals(10.minutes, second.cardioBar().elapsed)
        assertFalse(second.cardioBar().paused)
        assertEquals(clock.current + 40.minutes, scheduler.scheduled)
    }
}
