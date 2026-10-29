package io.github.lopezjavier.appfitness.feature.routines

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.domain.model.PlannedSet
import io.github.lopezjavier.appfitness.domain.model.RepRange
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

/** US-70: the optional rep range of a routine exercise is part of the builder draft and of the saved routine. */
class RoutineBuilderRepRangeViewModelTest {
    private val exercises = FakeExerciseRepository(listOf(exerciseRow("a", "bench"), exerciseRow("b", "row")))
    private val library = FakeExerciseLibrary(
        mapOf("bench" to exerciseInfo("bench", "Press de banca"), "row" to exerciseInfo("row", "Remo con barra")),
    )
    private val repository = FakeRoutineRepository(listOf(stored()))

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.builder(savedState: SavedStateHandle = SavedStateHandle()): RoutineBuilderViewModel {
        val vm = RoutineBuilderViewModel(
            "r1",
            savedState,
            repository,
            LoadExerciseCatalog(exercises, library),
            FakeConnectivityObserver(),
            FakeTrainingPreferencesRepository(),
        )
        advanceUntilIdle()
        return vm
    }

    private fun RoutineBuilderViewModel.form() = assertIs<RoutineBuilderUiState.Editing>(uiState.value)

    private fun RoutineBuilderViewModel.range(index: Int) = form().rows[index].repRange

    @Test
    fun aSavedRange_isShownOnItsRowAndTheOtherHasNone() = runTest {
        val vm = builder()

        assertEquals(RepRange(6, 8), vm.range(0))
        assertNull(vm.range(1))
    }

    @Test
    fun steppingUpFromNone_givesEightToTwelve_fromEitherStepper() = runTest {
        val vm = builder()

        vm.onEvent(RoutineBuilderEvent.StepRepMin(1, 1))
        assertEquals(RepRange(8, 12), vm.range(1))
        vm.onEvent(RoutineBuilderEvent.StepRepMin(1, -1))
        vm.onEvent(RoutineBuilderEvent.StepRepMin(1, -1))
        vm.onEvent(RoutineBuilderEvent.StepRepMin(1, -1))
        vm.onEvent(RoutineBuilderEvent.StepRepMin(1, -1))
        vm.onEvent(RoutineBuilderEvent.StepRepMin(1, -1))
        vm.onEvent(RoutineBuilderEvent.StepRepMin(1, -1))
        vm.onEvent(RoutineBuilderEvent.StepRepMin(1, -1))
        vm.onEvent(RoutineBuilderEvent.StepRepMin(1, -1))
        assertNull(vm.range(1))
        vm.onEvent(RoutineBuilderEvent.StepRepMax(1, 1))
        assertEquals(RepRange(8, 12), vm.range(1))
    }

    @Test
    fun steppingMinBelowOne_clearsBothBounds() = runTest {
        val vm = builder()
        vm.onEvent(RoutineBuilderEvent.StepRepMin(0, -1))
        vm.onEvent(RoutineBuilderEvent.StepRepMin(0, -1))
        vm.onEvent(RoutineBuilderEvent.StepRepMin(0, -1))
        vm.onEvent(RoutineBuilderEvent.StepRepMin(0, -1))
        vm.onEvent(RoutineBuilderEvent.StepRepMin(0, -1))
        assertEquals(RepRange(1, 8), vm.range(0))

        vm.onEvent(RoutineBuilderEvent.StepRepMin(0, -1))

        assertNull(vm.range(0))
    }

    @Test
    fun steppingMinAboveMax_raisesMax() = runTest {
        val vm = builder()

        vm.onEvent(RoutineBuilderEvent.StepRepMin(0, 1))
        vm.onEvent(RoutineBuilderEvent.StepRepMin(0, 1))
        vm.onEvent(RoutineBuilderEvent.StepRepMin(0, 1))

        assertEquals(RepRange(9, 9), vm.range(0))
    }

    @Test
    fun steppingMaxBelowMin_lowersMin() = runTest {
        val vm = builder()

        vm.onEvent(RoutineBuilderEvent.StepRepMax(0, -1))
        vm.onEvent(RoutineBuilderEvent.StepRepMax(0, -1))
        vm.onEvent(RoutineBuilderEvent.StepRepMax(0, -1))

        assertEquals(RepRange(5, 5), vm.range(0))
    }

    @Test
    fun theRangeStaysWithinOneAndFifty() = runTest {
        val vm = builder()

        repeat(60) { vm.onEvent(RoutineBuilderEvent.StepRepMax(0, 1)) }
        assertEquals(RepRange(6, 50), vm.range(0))
        repeat(60) { vm.onEvent(RoutineBuilderEvent.StepRepMin(0, 1)) }

        assertEquals(RepRange(50, 50), vm.range(0))
    }

    @Test
    fun editingTheRange_marksTheDraftDirty() = runTest {
        val vm = builder()
        assertFalse(vm.form().isDirty)

        vm.onEvent(RoutineBuilderEvent.StepRepMax(0, 1))

        assertTrue(vm.form().isDirty)
    }

    @Test
    fun theRange_isSavedWithTheRoutine_andClearedWhenUnset() = runTest {
        val vm = builder()
        vm.onEvent(RoutineBuilderEvent.StepRepMax(0, 1))
        vm.onEvent(RoutineBuilderEvent.StepRepMin(1, 1))

        vm.onEvent(RoutineBuilderEvent.Save)
        advanceUntilIdle()

        assertEquals(listOf(RepRange(6, 9), RepRange(8, 12)), repository.saved.single().exercises.map { it.repRange })
    }

    @Test
    fun anUnsavedRange_survivesRecreationFromTheSameSavedState() = runTest {
        val savedState = SavedStateHandle()
        builder(savedState).onEvent(RoutineBuilderEvent.StepRepMax(0, 1))

        val recreated = builder(savedState)

        assertEquals(RepRange(6, 9), recreated.range(0))
    }

    private fun stored() = Routine(
        id = "r1",
        name = "Torso",
        notes = null,
        archived = false,
        exercises = listOf(
            RoutineExercise("a", 0, null, listOf(PlannedSet(60.0, 8)), 90, repRange = RepRange(6, 8)),
            RoutineExercise("b", 1, null, listOf(PlannedSet(40.0, 10)), 90),
        ),
    )
}
