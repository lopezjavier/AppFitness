package io.github.lopezjavier.appfitness.feature.routines

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.domain.model.MovementPattern
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
    private val exercises =
        FakeExerciseRepository(
            listOf(exerciseRow("a", "bench"), exerciseRow("bike", "bike", pattern = MovementPattern.Cardio)),
        )
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

    private fun TestScope.builder(repo: FakeRoutineRepository = repository): RoutineBuilderViewModel {
        val vm = RoutineBuilderViewModel(
            "r1",
            SavedStateHandle(),
            repo,
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
    fun settingTheTarget_onALegacyCardioWithThreeSets_collapsesItToOneTimedSet() = runTest {
        val legacy = Routine(
            id = "r1",
            name = "Entreno 1",
            notes = null,
            archived = false,
            exercises = listOf(
                RoutineExercise("bike", 0, null, List(3) { PlannedSet.ofMinutes(15) }, restSeconds = 60),
            ),
        )
        val vm = builder(FakeRoutineRepository(listOf(legacy)))
        assertEquals(45, vm.form().rows[0].cardioMinutes)
        assertTrue(vm.form().rows[0].isCardio)

        vm.onEvent(RoutineBuilderEvent.SetMinutes(0, 0, 50))

        val row = vm.form().rows[0]
        assertEquals(1, row.plannedSets.size)
        assertEquals(50, row.cardioMinutes)
        assertTrue(row.plannedSets.single().timed)
    }

    @Test
    fun clearingTheTarget_onALegacyCardio_keepsOneSetWithNoMinutes() = runTest {
        val legacy = Routine(
            "r1",
            "Entreno 1",
            null,
            false,
            listOf(RoutineExercise("bike", 0, null, List(2) { PlannedSet.ofMinutes(15) }, restSeconds = 60)),
        )
        val vm = builder(FakeRoutineRepository(listOf(legacy)))

        vm.onEvent(RoutineBuilderEvent.SetMinutes(0, 0, null))

        assertEquals(1, vm.form().rows[0].plannedSets.size)
        assertNull(vm.form().rows[0].cardioMinutes)
    }

    @Test
    fun aCardioRow_ignoresAddSetAndRemoveSet() = runTest {
        val vm = builder()

        vm.onEvent(RoutineBuilderEvent.AddSet(1))
        vm.onEvent(RoutineBuilderEvent.RemoveSet(1, 0))

        assertEquals(1, vm.form().rows[1].plannedSets.size)
        assertEquals(50, vm.form().rows[1].cardioMinutes)
    }

    @Test
    fun aHold_isNotCardio_andKeepsItsSets() = runTest {
        val hold = Routine(
            "r1",
            "Entreno 1",
            null,
            false,
            listOf(RoutineExercise("a", 0, null, List(3) { PlannedSet.ofMinutes(1) }, restSeconds = 60)),
        )
        val vm = builder(FakeRoutineRepository(listOf(hold)))

        assertFalse(vm.form().rows[0].isCardio)
        vm.onEvent(RoutineBuilderEvent.AddSet(0))
        assertEquals(4, vm.form().rows[0].plannedSets.size)
        vm.onEvent(RoutineBuilderEvent.SetMinutes(0, 1, 5))
        assertEquals(4, vm.form().rows[0].plannedSets.size)
        assertEquals(5, vm.form().rows[0].plannedSets[1].minutes)
    }

    @Test
    fun addingACardioExercise_createsOneTimedSet() = runTest {
        val vm = builder()

        vm.onEvent(RoutineBuilderEvent.AddExercise("bike"))

        val row = vm.form().rows.last()
        assertEquals(1, row.plannedSets.size)
        assertTrue(row.plannedSets.single().timed)
        assertTrue(row.isCardio)
        assertEquals(null, row.cardioMinutes)
        assertTrue(vm.form().rows[0].plannedSets.none { it.timed })
    }

    @Test
    fun savingAfterEditingALegacyCardio_writesOneTimedSet() = runTest {
        val legacy = Routine(
            "r1",
            "Entreno 1",
            null,
            false,
            listOf(RoutineExercise("bike", 0, null, List(3) { PlannedSet.ofMinutes(15) }, restSeconds = 60)),
        )
        val repo = FakeRoutineRepository(listOf(legacy))
        val vm = builder(repo)

        vm.onEvent(RoutineBuilderEvent.SetMinutes(0, 0, 45))
        vm.onEvent(RoutineBuilderEvent.Save)
        advanceUntilIdle()

        assertEquals(2700, repo.saved.single().exercises[0].plannedSets.single().durationS)
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

    @Test
    fun groupingALiftWithACardio_neverPadsTheCardioRow() = runTest {
        val vm = builder()
        vm.onEvent(RoutineBuilderEvent.AddSet(0))
        vm.onEvent(RoutineBuilderEvent.AddSet(0))

        vm.onEvent(RoutineBuilderEvent.GroupWithNext(0))

        assertEquals(3, vm.form().rows[0].plannedSets.size)
        assertEquals(1, vm.form().rows[1].plannedSets.size)
        assertEquals(50, vm.form().rows[1].cardioMinutes)
    }

    @Test
    fun addingOrRemovingALiftSet_inASupersetWithACardio_leavesTheCardioAlone() = runTest {
        val vm = builder()
        vm.onEvent(RoutineBuilderEvent.GroupWithNext(0))

        vm.onEvent(RoutineBuilderEvent.AddSet(0))
        assertEquals(2, vm.form().rows[0].plannedSets.size)
        assertEquals(1, vm.form().rows[1].plannedSets.size)

        vm.onEvent(RoutineBuilderEvent.RemoveSet(0, 1))
        assertEquals(1, vm.form().rows[0].plannedSets.size)
        assertEquals(50, vm.form().rows[1].cardioMinutes)
    }
}
