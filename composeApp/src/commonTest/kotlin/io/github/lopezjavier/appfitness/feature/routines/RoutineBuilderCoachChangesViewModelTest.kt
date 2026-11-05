package io.github.lopezjavier.appfitness.feature.routines

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CoachChangeKind
import io.github.lopezjavier.appfitness.domain.model.CoachChangeStatus
import io.github.lopezjavier.appfitness.domain.model.PlannedSet
import io.github.lopezjavier.appfitness.domain.model.RepRange
import io.github.lopezjavier.appfitness.domain.model.Routine
import io.github.lopezjavier.appfitness.domain.model.RoutineExercise
import io.github.lopezjavier.appfitness.domain.usecase.LoadExerciseCatalog
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import io.github.lopezjavier.appfitness.feature.trainer.FakeCoachChangesRepository
import io.github.lopezjavier.appfitness.feature.trainer.coachChange
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** T-228: the range focus coming from "Añadir un rango" and the `?` markers of the coach changes of a routine. */
class RoutineBuilderCoachChangesViewModelTest {
    private val exercises = FakeExerciseRepository(listOf(exerciseRow("a", "bench"), exerciseRow("b", "row")))
    private val library = FakeExerciseLibrary(
        mapOf("bench" to exerciseInfo("bench", "Press de banca"), "row" to exerciseInfo("row", "Remo con barra")),
    )
    private val routines = FakeRoutineRepository(listOf(stored()))
    private val changes = FakeCoachChangesRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.builder(
        savedState: SavedStateHandle = SavedStateHandle(),
        focusExerciseId: String? = null,
        routineId: String? = "r1",
    ): RoutineBuilderViewModel {
        val vm = RoutineBuilderViewModel(
            routineId,
            savedState,
            routines,
            LoadExerciseCatalog(exercises, library),
            FakeConnectivityObserver(),
            FakeTrainingPreferencesRepository(),
            focusExerciseId = focusExerciseId,
            coachChanges = changes,
        )
        advanceUntilIdle()
        return vm
    }

    private fun RoutineBuilderViewModel.form() = assertIs<RoutineBuilderUiState.Editing>(uiState.value)

    @Test
    fun withoutAFocusArgument_noRowIsFocused() = runTest {
        assertNull(builder().form().focusRangeExerciseId)
    }

    @Test
    fun focusExerciseId_isExposedInTheState() = runTest {
        assertEquals("b", builder(focusExerciseId = "b").form().focusRangeExerciseId)
    }

    @Test
    fun theFocus_survivesTheViewModelBeingRecreatedFromTheSameSavedState() = runTest {
        val handle = SavedStateHandle()
        builder(handle, focusExerciseId = "b")

        val recreated = builder(handle, focusExerciseId = null)

        assertEquals("b", recreated.form().focusRangeExerciseId)
    }

    @Test
    fun markers_cover_appliedAndPendingChangesOfThisRoutine() = runTest {
        changes.changes = Result.success(
            listOf(
                coachChange(id = "1", exerciseId = "a", routineId = "r1"),
                coachChange(
                    id = "2",
                    exerciseId = "b",
                    routineId = "r1",
                    kind = CoachChangeKind.NoRangeFlag,
                    status = CoachChangeStatus.Pending,
                ),
            ),
        )

        val markers = builder().form().coachMarkers

        assertEquals(setOf("a", "b"), markers.keys)
        assertEquals(CoachChangeKind.LoadRaise, markers.getValue("a").kind)
        assertEquals(62.5, markers.getValue("a").figures.nextKg)
        assertEquals("reason_1", markers.getValue("a").reason)
        assertEquals(CoachChangeKind.NoRangeFlag, markers.getValue("b").kind)
    }

    @Test
    fun markers_ignoreChangesOfOtherRoutinesAndUndoneOnes() = runTest {
        changes.changes = Result.success(
            listOf(
                coachChange(id = "1", exerciseId = "a", routineId = "other"),
                coachChange(id = "2", exerciseId = "b", routineId = "r1", status = CoachChangeStatus.Undone),
            ),
        )

        assertTrue(builder().form().coachMarkers.isEmpty())
    }

    @Test
    fun markers_failedReadMeansNoMarkers() = runTest {
        changes.changes = Result.failure(AppException(AppError.Network))

        assertTrue(builder().form().coachMarkers.isEmpty())
    }

    @Test
    fun aNewRoutine_readsNoChanges() = runTest {
        builder(routineId = null)

        assertTrue(changes.listCalls.isEmpty())
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
