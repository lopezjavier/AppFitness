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

/** US-66: the note of a routine exercise is part of the builder draft and is saved with the routine. */
class RoutineBuilderNotesViewModelTest {
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

    private fun TestScope.builder(
        routineId: String? = "r1",
        savedState: SavedStateHandle = SavedStateHandle(),
    ): RoutineBuilderViewModel {
        val vm = RoutineBuilderViewModel(
            routineId,
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

    @Test
    fun anExistingNote_isShownOnItsRow() = runTest {
        val vm = builder()

        assertEquals(listOf("bajar el peso si duele", null), vm.form().rows.map { it.notes })
    }

    @Test
    fun settingANote_marksTheDraftDirty_andKeepsItTrimmed() = runTest {
        val vm = builder()
        assertFalse(vm.form().isDirty)

        vm.onEvent(RoutineBuilderEvent.SetExerciseNote(1, "  pausa al bajar "))

        assertEquals("pausa al bajar", vm.form().rows[1].notes)
        assertTrue(vm.form().isDirty)
    }

    @Test
    fun theNote_isSavedWithTheRoutine() = runTest {
        val vm = builder()
        vm.onEvent(RoutineBuilderEvent.SetExerciseNote(1, "pausa al bajar"))

        vm.onEvent(RoutineBuilderEvent.Save)
        advanceUntilIdle()

        val saved = repository.saved.single()
        assertEquals(listOf("bajar el peso si duele", "pausa al bajar"), saved.exercises.map { it.notes })
    }

    @Test
    fun clearingANote_removesItFromTheSavedRoutine() = runTest {
        val vm = builder()

        vm.onEvent(RoutineBuilderEvent.SetExerciseNote(0, "   "))
        vm.onEvent(RoutineBuilderEvent.Save)
        advanceUntilIdle()

        assertNull(repository.saved.single().exercises[0].notes)
    }

    @Test
    fun aNoteOver200Characters_isIgnored() = runTest {
        val vm = builder()

        vm.onEvent(RoutineBuilderEvent.SetExerciseNote(0, "x".repeat(201)))

        assertEquals("bajar el peso si duele", vm.form().rows[0].notes)
        assertFalse(vm.form().isDirty)
    }

    @Test
    fun revertingANoteToItsSavedText_isNotDirty() = runTest {
        val vm = builder()

        vm.onEvent(RoutineBuilderEvent.SetExerciseNote(0, "otra"))
        vm.onEvent(RoutineBuilderEvent.SetExerciseNote(0, "bajar el peso si duele"))

        assertFalse(vm.form().isDirty)
    }

    @Test
    fun aDraftWithANote_survivesRecreationFromTheSameSavedState() = runTest {
        val saved = SavedStateHandle()
        val first = builder(savedState = saved)
        first.onEvent(RoutineBuilderEvent.SetExerciseNote(1, "pausa al bajar"))
        advanceUntilIdle()

        val second = builder(savedState = saved)

        assertEquals("pausa al bajar", second.form().rows[1].notes)
        assertTrue(second.form().isDirty)
    }

    @Test
    fun aNoteSayingDolorFuerte_isKeptAsIs() = runTest {
        val vm = builder()

        vm.onEvent(RoutineBuilderEvent.SetExerciseNote(1, "dolor fuerte"))

        assertEquals("dolor fuerte", vm.form().rows[1].notes)
    }

    private fun stored() = Routine(
        id = "r1",
        name = "Torso",
        notes = null,
        archived = false,
        exercises = listOf(
            RoutineExercise("a", 0, null, listOf(PlannedSet(60.0, 8)), 90, notes = "bajar el peso si duele"),
            RoutineExercise("b", 1, null, listOf(PlannedSet(40.0, 10)), 90),
        ),
    )
}
