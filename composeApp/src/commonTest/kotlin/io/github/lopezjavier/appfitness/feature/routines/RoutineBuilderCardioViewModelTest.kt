package io.github.lopezjavier.appfitness.feature.routines

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.domain.model.PlannedSet
import io.github.lopezjavier.appfitness.domain.model.Routine
import io.github.lopezjavier.appfitness.domain.model.RoutineExercise
import io.github.lopezjavier.appfitness.domain.usecase.LoadExerciseCatalog
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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

/** T-183: a cardio set is planned in minutes only. */
class RoutineBuilderCardioViewModelTest {
    private val exercises = FakeExerciseRepository(listOf(exerciseRow("a", "bench"), exerciseRow("bike", "bike")))
    private val library = FakeExerciseLibrary(
        mapOf("bench" to exerciseInfo("bench", "Press de banca"), "bike" to exerciseInfo("bike", "Bicicleta")),
    )
    private val routine = Routine(
        id = "r1",
        name = "Entreno 1",
        notes = null,
        archived = false,
        exercises = listOf(
            RoutineExercise("a", 0, null, listOf(PlannedSet(40.0, 8)), restSeconds = 90),
            RoutineExercise("bike", 1, null, listOf(PlannedSet.ofMinutes(50)), restSeconds = 0),
        ),
    )
    private val repository = FakeRoutineRepository(listOf(routine))

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.builder(): RoutineBuilderViewModel {
        val vm = RoutineBuilderViewModel(
            "r1",
            SavedStateHandle(),
            repository,
            LoadExerciseCatalog(exercises, library),
            FakeConnectivityObserver(),
            FakeTrainingPreferencesRepository(),
        )
        advanceUntilIdle()
        return vm
    }

    private fun RoutineBuilderViewModel.form() = assertIs<RoutineBuilderUiState.Editing>(uiState.value)

    @Test
    fun aCardioSet_exposesItsPlannedMinutes() = runTest {
        val set = builder().form().rows[1].plannedSets.single()

        assertTrue(set.timed)
        assertEquals(50, set.minutes)
        assertTrue(set.isValid)
    }

    @Test
    fun changingTheMinutes_roundTripsThroughSave() = runTest {
        val vm = builder()

        vm.onEvent(RoutineBuilderEvent.SetMinutes(1, 0, 55))
        vm.onEvent(RoutineBuilderEvent.Save)
        advanceUntilIdle()

        val saved = repository.saved.single().exercises[1].plannedSets.single()
        assertEquals(3300, saved.durationS)
        assertEquals(0, saved.reps)
        assertEquals(PlannedSet(40.0, 8), repository.saved.single().exercises[0].plannedSets.single())
    }

    @Test
    fun clearingTheMinutes_showsSinDefinir_andBlocksSaving() = runTest {
        val vm = builder()

        vm.onEvent(RoutineBuilderEvent.SetMinutes(1, 0, null))

        assertNull(vm.form().rows[1].plannedSets.single().minutes)
        assertFalse(vm.form().canSave)
    }

    @Test
    fun minutesAreIgnoredOnALiftingSet_andOutOfRangeMinutesAreIgnoredOnACardioSet() = runTest {
        val vm = builder()

        vm.onEvent(RoutineBuilderEvent.SetMinutes(0, 0, 30))
        vm.onEvent(RoutineBuilderEvent.SetMinutes(1, 0, 241))

        assertEquals("8", vm.form().rows[0].plannedSets.single().reps)
        assertFalse(vm.form().rows[0].plannedSets.single().timed)
        assertEquals(50, vm.form().rows[1].plannedSets.single().minutes)
    }
}
