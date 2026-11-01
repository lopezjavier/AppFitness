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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    private val routines = FakeRoutineRepository(
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
    private var created: LiveSessionViewModel? = null

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.loaded(): LiveSessionViewModel {
        val bodyMeasurements = FakeWeightRepository()
        val vm = LiveSessionViewModel(
            repository,
            exercises,
            library,
            FakeConnectivityObserver(),
            clock,
            FakeHaptics(),
            routines,
            FakeAlertSound(),
            FakeTrainingPreferencesRepository(),
            bodyMeasurements,
            SaveBodyMeasurement(bodyMeasurements, clock, TimeZone.UTC),
            TimeZone.UTC,
            SavedStateHandle(),
            FakeRestAlertScheduler(),
            FakeNotificationPermission(),
            FakeRestAlertPreferences(),
        )
        created = vm
        advanceUntilIdle()
        return vm
    }

    private fun LiveSessionViewModel.active() = assertIs<LiveSessionUiState.Active>(uiState.value)

    private fun runSessionTest(body: suspend TestScope.() -> Unit) = runTest {
        body()
        created?.onEvent(LiveSessionEvent.RestSkip)
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
}
