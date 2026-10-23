package io.github.lopezjavier.appfitness.feature.workout

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeExerciseOptionsRepository
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.model.PreviousSet
import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.WorkoutSet
import io.github.lopezjavier.appfitness.domain.usecase.SaveBodyMeasurement
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import io.github.lopezjavier.appfitness.feature.routines.FakeRoutineRepository
import io.github.lopezjavier.appfitness.feature.weight.FakeWeightRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** US-31: each metric type drafts, validates and saves the columns it measures; the stopwatch counts with the clock. */
@Suppress("TooManyFunctions") // One method per scenario.
class LiveSessionMetricTypesViewModelTest {
    private val repository = FakeWorkoutSessionRepository()
    private val clock = MutableClock()
    private val weights = FakeWeightRepository()
    private val viewModels = mutableListOf<LiveSessionViewModel>()

    private val kinds = listOf(
        ExerciseMetricType.Bodyweight to Equipment.Bodyweight,
        ExerciseMetricType.Duration to Equipment.Bodyweight,
        ExerciseMetricType.DistanceDuration to Equipment.Machine,
        ExerciseMetricType.WeightDuration to Equipment.Dumbbell,
        ExerciseMetricType.RepsOnly to Equipment.Bodyweight,
        ExerciseMetricType.Assisted to Equipment.Machine,
        ExerciseMetricType.Weighted to Equipment.Bodyweight,
        ExerciseMetricType.StepsDuration to Equipment.Other,
        ExerciseMetricType.FloorsDuration to Equipment.Machine,
        ExerciseMetricType.WeightReps to Equipment.Barbell,
    )

    private val exerciseRepository = FakeExerciseRepository(
        kinds.mapIndexed { index, (metric, equipment) ->
            exerciseRow("e$index", null, equipment = equipment, name = "Ejercicio $index", custom = true)
                .copy(metricType = metric)
        },
    )

    private fun positionOf(metric: ExerciseMetricType) = kinds.indexOfFirst { it.first == metric }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        repository.session = sessionOf(
            *kinds.mapIndexed { index, _ ->
                SessionExercise("e$index", index, null, listOf(pendingSet(1)))
            }.toTypedArray(),
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(savedState: SavedStateHandle = SavedStateHandle()) = LiveSessionViewModel(
        repository,
        exerciseRepository,
        FakeExerciseLibrary(),
        FakeConnectivityObserver(),
        clock,
        FakeHaptics(),
        FakeRoutineRepository(),
        FakeAlertSound(),
        FakeTrainingPreferencesRepository(),
        weights,
        FakeExerciseOptionsRepository(),
        SaveBodyMeasurement(weights, clock, TimeZone.UTC),
        TimeZone.UTC,
        savedState,
    ).also { viewModels += it }

    /** Stops every running stopwatch and rest ticker: they never let the virtual scheduler go idle. */
    private fun runLiveTest(body: suspend TestScope.() -> Unit) = runTest {
        body()
        viewModels.forEach { vm ->
            (vm.uiState.value as? LiveSessionUiState.Active)?.exercises?.forEach { exercise ->
                exercise.sets.filter { it.stopwatchRunning }.forEach {
                    vm.onEvent(LiveSessionEvent.StopwatchToggled(exercise.position, it.setIndex))
                }
            }
            vm.onEvent(LiveSessionEvent.RestSkip)
        }
        runCurrent()
    }

    private fun TestScope.loaded(savedState: SavedStateHandle = SavedStateHandle()): LiveSessionViewModel {
        val vm = newViewModel(savedState)
        advanceUntilIdle()
        return vm
    }

    private fun LiveSessionViewModel.active() = assertIs<LiveSessionUiState.Active>(uiState.value)

    private fun LiveSessionViewModel.row(metric: ExerciseMetricType) =
        active().exercises[positionOf(metric)].sets.single()

    private fun TestScope.send(vm: LiveSessionViewModel, vararg events: LiveSessionEvent) {
        events.forEach(vm::onEvent)
        runCurrent()
    }

    private fun TestScope.checkAndGetEntry(vm: LiveSessionViewModel, metric: ExerciseMetricType): SetEntry? {
        send(vm, LiveSessionEvent.SetChecked(positionOf(metric), 1, true))
        if ((vm.uiState.value as? LiveSessionUiState.Active)?.rpePicker?.completesSet == true) {
            send(vm, LiveSessionEvent.SetRpeChosen(positionOf(metric), 1, 8.0)) // Answers the RPE prompt (ADR-55).
        }
        return repository.upserts.lastOrNull { it.exercisePosition == positionOf(metric) }?.entry
    }

    @Test
    fun theExerciseKind_decidesTheRowFields() = runLiveTest {
        val vm = loaded()

        val exercise = vm.active().exercises[positionOf(ExerciseMetricType.Bodyweight)]

        assertEquals(ExerciseMetricType.Bodyweight, exercise.metricType)
        assertEquals(Equipment.Bodyweight, exercise.equipment)
    }

    @Test
    fun bodyweight_savesAddedLoadAndReps() = runLiveTest {
        val vm = loaded()
        val position = positionOf(ExerciseMetricType.Bodyweight)

        send(
            vm,
            LiveSessionEvent.WeightChanged(position, 1, "10"),
            LiveSessionEvent.RepsChanged(position, 1, "8"),
        )

        assertEquals(SetEntry.Bodyweight(10.0, 8), checkAndGetEntry(vm, ExerciseMetricType.Bodyweight))
    }

    @Test
    fun bodyweight_withoutAddedLoad_savesZero() = runLiveTest {
        val vm = loaded()
        val position = positionOf(ExerciseMetricType.Bodyweight)

        send(vm, LiveSessionEvent.RepsChanged(position, 1, "12"))

        assertEquals(SetEntry.Bodyweight(0.0, 12), checkAndGetEntry(vm, ExerciseMetricType.Bodyweight))
    }

    @Test
    fun bodyweight_withoutReps_isRefused() = runLiveTest {
        val vm = loaded()

        assertEquals(null, checkAndGetEntry(vm, ExerciseMetricType.Bodyweight))
        assertTrue(vm.row(ExerciseMetricType.Bodyweight).invalid)
        assertFalse(vm.row(ExerciseMetricType.Bodyweight).completed)
    }

    @Test
    fun assistedAndWeighted_saveTheirOwnVariant() = runLiveTest {
        val vm = loaded()
        val assisted = positionOf(ExerciseMetricType.Assisted)
        val weighted = positionOf(ExerciseMetricType.Weighted)

        send(
            vm,
            LiveSessionEvent.WeightChanged(assisted, 1, "20"),
            LiveSessionEvent.RepsChanged(assisted, 1, "10"),
            LiveSessionEvent.WeightChanged(weighted, 1, "7,5"),
            LiveSessionEvent.RepsChanged(weighted, 1, "6"),
        )

        assertEquals(SetEntry.Assisted(20.0, 10), checkAndGetEntry(vm, ExerciseMetricType.Assisted))
        assertEquals(SetEntry.Weighted(7.5, 6), checkAndGetEntry(vm, ExerciseMetricType.Weighted))
    }

    @Test
    fun repsOnly_savesJustReps() = runLiveTest {
        val vm = loaded()
        val position = positionOf(ExerciseMetricType.RepsOnly)

        send(vm, LiveSessionEvent.RepsChanged(position, 1, "20"))

        assertEquals(SetEntry.RepsOnly(20), checkAndGetEntry(vm, ExerciseMetricType.RepsOnly))
    }

    @Test
    fun stopwatch_countsWithTheClock_andStopsKeepingTheTime() = runLiveTest {
        val vm = loaded()
        val position = positionOf(ExerciseMetricType.Duration)

        send(vm, LiveSessionEvent.StopwatchToggled(position, 1))
        clock.advance(90.seconds)
        send(vm, LiveSessionEvent.Resume)

        assertTrue(vm.row(ExerciseMetricType.Duration).stopwatchRunning)
        assertEquals(90, vm.row(ExerciseMetricType.Duration).durationSeconds)

        send(vm, LiveSessionEvent.StopwatchToggled(position, 1))
        clock.advance(30.seconds)
        send(vm, LiveSessionEvent.Resume)

        assertFalse(vm.row(ExerciseMetricType.Duration).stopwatchRunning)
        assertEquals(90, vm.row(ExerciseMetricType.Duration).durationSeconds)
    }

    @Test
    fun stopwatch_republishesEverySecondWhileRunning() = runLiveTest {
        val vm = loaded()
        val position = positionOf(ExerciseMetricType.Duration)
        send(vm, LiveSessionEvent.StopwatchToggled(position, 1))

        clock.advance(5.seconds)
        testScheduler.advanceTimeBy(1_001)
        runCurrent()

        assertEquals(5, vm.row(ExerciseMetricType.Duration).durationSeconds)
    }

    @Test
    fun stopwatch_stoppingOnlyFillsTheDraft_nothingIsSavedUntilTheCheck() = runLiveTest {
        val vm = loaded()
        val position = positionOf(ExerciseMetricType.Duration)

        send(vm, LiveSessionEvent.StopwatchToggled(position, 1))
        clock.advance(90.seconds)
        send(vm, LiveSessionEvent.StopwatchToggled(position, 1))

        assertTrue(repository.upserts.isEmpty())
        assertEquals(SetEntry.Duration(90), checkAndGetEntry(vm, ExerciseMetricType.Duration))
    }

    @Test
    fun checkingASetWhileTheStopwatchRuns_stopsItAtThatTime() = runLiveTest {
        val vm = loaded()
        val position = positionOf(ExerciseMetricType.Duration)
        send(vm, LiveSessionEvent.StopwatchToggled(position, 1))
        clock.advance(45.seconds)

        val entry = checkAndGetEntry(vm, ExerciseMetricType.Duration)
        clock.advance(60.seconds)
        send(vm, LiveSessionEvent.Resume)

        assertEquals(SetEntry.Duration(45), entry)
        assertFalse(vm.row(ExerciseMetricType.Duration).stopwatchRunning)
        assertEquals(45, vm.row(ExerciseMetricType.Duration).durationSeconds)
    }

    @Test
    fun manualDuration_isUsedForTheSavedSet() = runLiveTest {
        val vm = loaded()
        val position = positionOf(ExerciseMetricType.Duration)

        send(vm, LiveSessionEvent.DurationEdited(position, 1, 90))

        assertEquals(90, vm.row(ExerciseMetricType.Duration).durationSeconds)
        assertEquals(SetEntry.Duration(90), checkAndGetEntry(vm, ExerciseMetricType.Duration))
    }

    @Test
    fun duration_withoutTime_isRefused() = runLiveTest {
        val vm = loaded()

        assertEquals(null, checkAndGetEntry(vm, ExerciseMetricType.Duration))
        assertTrue(vm.row(ExerciseMetricType.Duration).invalid)
    }

    @Test
    fun distanceInKilometres_isSavedInMetresWithTheTime() = runLiveTest {
        val vm = loaded()
        val position = positionOf(ExerciseMetricType.DistanceDuration)

        send(
            vm,
            LiveSessionEvent.DistanceChanged(position, 1, "5,5"),
            LiveSessionEvent.DurationEdited(position, 1, 1800),
        )

        assertEquals(SetEntry.DistanceDuration(5500.0, 1800), checkAndGetEntry(vm, ExerciseMetricType.DistanceDuration))
    }

    @Test
    fun weightDuration_stepsAndFloors_saveTheirColumns() = runLiveTest {
        val vm = loaded()
        val carry = positionOf(ExerciseMetricType.WeightDuration)
        val steps = positionOf(ExerciseMetricType.StepsDuration)
        val floors = positionOf(ExerciseMetricType.FloorsDuration)

        send(
            vm,
            LiveSessionEvent.WeightChanged(carry, 1, "24"),
            LiveSessionEvent.DurationEdited(carry, 1, 40),
            LiveSessionEvent.StepsChanged(steps, 1, "1200"),
            LiveSessionEvent.DurationEdited(steps, 1, 600),
            LiveSessionEvent.FloorsChanged(floors, 1, "12"),
            LiveSessionEvent.DurationEdited(floors, 1, 300),
        )

        assertEquals(SetEntry.WeightDuration(24.0, 40), checkAndGetEntry(vm, ExerciseMetricType.WeightDuration))
        assertEquals(SetEntry.StepsDuration(1200, 600), checkAndGetEntry(vm, ExerciseMetricType.StepsDuration))
        assertEquals(SetEntry.FloorsDuration(12, 300), checkAndGetEntry(vm, ExerciseMetricType.FloorsDuration))
    }

    @Test
    fun uncheckedDraftWrites_useTheMetricsEmptyEntry() = runLiveTest {
        val vm = loaded()
        val position = positionOf(ExerciseMetricType.Duration)

        send(vm, LiveSessionEvent.SetTypeChosen(position, 1, SetType.WarmUp))

        assertEquals(SetEntry.Duration(0), repository.upserts.single().entry)
    }

    @Test
    fun ghostText_followsTheMetricOfEachExercise() = runLiveTest {
        repository.previous = mapOf(
            "e${positionOf(ExerciseMetricType.Duration)}" to listOf(PreviousSet(1, 0.0, 0, durationS = 90)),
            "e${positionOf(ExerciseMetricType.DistanceDuration)}" to
                listOf(PreviousSet(1, 0.0, 0, durationS = 1800, distanceM = 5000.0)),
            "e${positionOf(ExerciseMetricType.WeightReps)}" to listOf(PreviousSet(1, 10.0, 8)),
        )

        val vm = loaded()

        assertEquals("01:30", vm.row(ExerciseMetricType.Duration).previous)
        assertEquals("5,0 km · 30:00", vm.row(ExerciseMetricType.DistanceDuration).previous)
        assertEquals("10 kg × 8", vm.row(ExerciseMetricType.WeightReps).previous)
    }

    @Test
    fun usingThePrevious_fillsTheDistanceAndTheStopwatch() = runLiveTest {
        val position = positionOf(ExerciseMetricType.DistanceDuration)
        repository.previous = mapOf(
            "e$position" to listOf(PreviousSet(1, 0.0, 0, durationS = 1800, distanceM = 5000.0)),
        )
        val vm = loaded()

        send(vm, LiveSessionEvent.UsePrevious(position, 1))

        assertEquals("5", vm.row(ExerciseMetricType.DistanceDuration).distance)
        assertEquals(1800, vm.row(ExerciseMetricType.DistanceDuration).durationSeconds)
    }

    @Test
    fun completedNonWeightSets_reloadIntoTheirFields() = runLiveTest {
        val position = positionOf(ExerciseMetricType.DistanceDuration)
        repository.session = sessionOf(
            SessionExercise(
                "e$position",
                0,
                null,
                listOf(WorkoutSet(1, SetType.Working, SetEntry.DistanceDuration(5500.0, 1800), SESSION_START)),
            ),
        )

        val vm = loaded()

        val row = vm.active().exercises.single().sets.single()
        assertEquals("5,5", row.distance)
        assertEquals(1800, row.durationSeconds)
        assertTrue(row.completed)
    }

    @Test
    fun runningStopwatchAndHalfTypedDrafts_surviveRecreationAndCountTheTimeAway() = runLiveTest {
        val handle = SavedStateHandle()
        val first = loaded(handle)
        val duration = positionOf(ExerciseMetricType.Duration)
        val distance = positionOf(ExerciseMetricType.DistanceDuration)
        send(
            first,
            LiveSessionEvent.StopwatchToggled(duration, 1),
            LiveSessionEvent.DistanceChanged(distance, 1, "3,2"),
            LiveSessionEvent.DurationEdited(distance, 1, 700),
        )
        clock.advance(90.seconds)

        // Not advanceUntilIdle: the first model's stopwatch ticker never goes idle on a virtual clock.
        val second = newViewModel(handle)
        runCurrent()

        assertTrue(second.row(ExerciseMetricType.Duration).stopwatchRunning)
        assertEquals(90, second.row(ExerciseMetricType.Duration).durationSeconds)
        assertEquals("3,2", second.row(ExerciseMetricType.DistanceDuration).distance)
        assertEquals(700, second.row(ExerciseMetricType.DistanceDuration).durationSeconds)
    }

    @Test
    fun liveSummary_usesTheLatestBodyWeightForBodyweightVolume() = runTest {
        weights.rows += BodyMeasurement("w1", Instant.parse("2026-10-18T08:00:00Z"), 74.0, MeasurementSource.Manual)
        val position = positionOf(ExerciseMetricType.Bodyweight)
        repository.session = sessionOf(
            SessionExercise(
                "e$position",
                0,
                null,
                listOf(WorkoutSet(1, SetType.Working, SetEntry.Bodyweight(10.0, 8), SESSION_START)),
            ),
        )
        repository.history = mapOf("e$position" to emptyList())
        val vm = loaded()

        vm.onEvent(LiveSessionEvent.RequestFinish)
        vm.onEvent(LiveSessionEvent.ConfirmFinish)
        advanceUntilIdle()

        val summary = assertIs<LiveSessionUiState.Finished>(vm.uiState.value).summary.summary
        assertEquals(672.0, summary.exercises.single().volume)
    }

    @Test
    fun liveSummary_ofADistanceSet_hasZeroVolumeAndStillCountsTheSet() = runTest {
        val position = positionOf(ExerciseMetricType.DistanceDuration)
        repository.session = sessionOf(
            SessionExercise(
                "e$position",
                0,
                null,
                listOf(WorkoutSet(1, SetType.Working, SetEntry.DistanceDuration(5000.0, 1800), SESSION_START)),
            ),
        )
        repository.history = mapOf("e$position" to emptyList())
        val vm = loaded()

        vm.onEvent(LiveSessionEvent.RequestFinish)
        vm.onEvent(LiveSessionEvent.ConfirmFinish)
        advanceUntilIdle()

        val summary = assertIs<LiveSessionUiState.Finished>(vm.uiState.value).summary.summary
        assertEquals(0.0, summary.exercises.single().volume)
        assertEquals(1, summary.completedSets)
    }
}
