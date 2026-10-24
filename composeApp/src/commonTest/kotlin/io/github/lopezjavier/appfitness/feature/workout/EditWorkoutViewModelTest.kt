package io.github.lopezjavier.appfitness.feature.workout

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.usecase.WorkoutDraftIssue
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EditWorkoutViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeWorkoutSessionRepository()
    private val connectivity = FakeConnectivityObserver()
    private val exercises = FakeExerciseRepository(listOf(exerciseRow("e1", "bench")))
    private val library = FakeExerciseLibrary(
        mapOf("bench" to exerciseInfo("bench", "Press banca"), "squat" to exerciseInfo("squat", "Sentadilla")),
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(id: String = "s1", savedState: SavedStateHandle = SavedStateHandle()) = EditWorkoutViewModel(
        sessionId = id,
        repository = repository,
        exerciseRepository = exercises,
        exerciseLibrary = library,
        connectivity = connectivity,
        clock = MutableClock(),
        savedState = savedState,
    )

    private fun EditWorkoutViewModel.content() = assertIs<EditWorkoutUiState.Content>(uiState.value)

    private fun EditWorkoutViewModel.form() = content().view.form

    /** 87.5 x 8 twice, 52 min, started 2026-10-19. */
    private fun seed(id: String = "s1") {
        repository.completed += completedSession(id)
    }

    @Test
    fun startsLoadingThenShowsTheWorkoutAsAnEditableForm() = runTest(dispatcher) {
        seed()
        val model = viewModel()
        assertEquals(EditWorkoutUiState.Loading, model.uiState.value)

        advanceUntilIdle()

        val form = model.form()
        assertEquals("Torso", form.name)
        assertEquals("52", form.duration)
        assertEquals(sessionLocalDate(SESSION_START), form.date)
        assertEquals(listOf("8", "8"), form.exercises.single().sets.map { it.reps })
        assertEquals("Press banca", model.content().view.names["e1"])
        assertTrue(model.content().canSave)
    }

    @Test
    fun savingSendsTheEditedDraftToTheSessionAndEmitsSaved() = runTest(dispatcher) {
        seed()
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(WorkoutFormEvent.ValueChanged(0, 1, SetField.Reps, "7"))
        model.onEvent(WorkoutFormEvent.DescriptionChanged("Buen día"))

        model.effects.test {
            model.onEvent(EditWorkoutEvent.Save)
            advanceUntilIdle()

            assertEquals(EditWorkoutEffect.Saved, awaitItem())
        }

        val (draft, sessionId) = repository.savedDrafts.single()
        assertEquals("s1", sessionId)
        assertEquals("Buen día", draft.description)
        assertEquals(SetEntry.WeightReps(87.5, 7), draft.exercises.single().sets[1].entry)
        assertEquals(sessionLocalDate(SESSION_START), draft.date)
    }

    @Test
    fun leavingWithoutSavingWritesNothing() = runTest(dispatcher) {
        seed()
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(WorkoutFormEvent.ValueChanged(0, 0, SetField.Reps, "3"))
        model.onEvent(WorkoutFormEvent.AddSet(0))
        model.onEvent(WorkoutFormEvent.RemoveSet(0, 0))
        model.onEvent(WorkoutFormEvent.NameChanged("Otro"))
        advanceUntilIdle()

        assertTrue(repository.savedDrafts.isEmpty())
        assertTrue(repository.upserts.isEmpty())
        assertTrue(repository.deleted.isEmpty())
        assertEquals("Torso", repository.completed.single().name)
    }

    @Test
    fun addingAndRemovingSetsAndExercisesChangesTheFormImmediately() = runTest(dispatcher) {
        seed()
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(WorkoutFormEvent.AddSet(0))
        assertEquals(3, model.form().exercises.single().sets.size)
        model.onEvent(WorkoutFormEvent.RemoveSet(0, 0))
        assertEquals(2, model.form().exercises.single().sets.size)
        model.onEvent(WorkoutFormEvent.AddExercise("e1"))
        assertEquals(2, model.form().exercises.size)
        model.onEvent(WorkoutFormEvent.RemoveExercise(0))
        assertEquals(1, model.form().exercises.size)
    }

    @Test
    fun anExerciseWithNoNameOrKnownMetricIsStillAdded() = runTest(dispatcher) {
        seed()
        val model = viewModel()
        advanceUntilIdle()
        // Created in the picker after the screen opened: absent from the lookup read at load.
        exercises.rows = exercises.rows + exerciseRow("e2", "squat")

        model.onEvent(WorkoutFormEvent.AddExercise("e2"))
        advanceUntilIdle()

        assertEquals(listOf("e1", "e2"), model.form().exercises.map { it.exerciseId })
        assertEquals("Sentadilla", model.content().view.names["e2"])
    }

    @Test
    fun changingARestTypeOrRpeIsReflectedInTheSavedDraft() = runTest(dispatcher) {
        seed()
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(WorkoutFormEvent.RestChanged(0, 45))
        model.onEvent(WorkoutFormEvent.TypeChanged(0, 0, SetType.WarmUp))
        model.onEvent(WorkoutFormEvent.RpeChanged(0, 1, 9.0))

        model.onEvent(EditWorkoutEvent.Save)
        advanceUntilIdle()

        val exercise = repository.savedDrafts.single().first.exercises.single()
        assertEquals(45, exercise.restSeconds)
        assertEquals(SetType.WarmUp, exercise.sets[0].setType)
        assertEquals(9.0, exercise.sets[1].rpe)
    }

    @Test
    fun anInvalidDraftBlocksSaveAndFlagsTheSet() = runTest(dispatcher) {
        seed()
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(WorkoutFormEvent.ValueChanged(0, 1, SetField.Reps, ""))

        model.onEvent(EditWorkoutEvent.Save)
        advanceUntilIdle()

        val content = model.content()
        assertTrue(WorkoutDraftIssue.SetValue(0, 1) in content.view.issues)
        assertTrue(content.view.submitAttempted)
        assertEquals(false, content.canSave)
        assertTrue(repository.savedDrafts.isEmpty())
    }

    @Test
    fun aBlankNameBlocksSave() = runTest(dispatcher) {
        seed()
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(WorkoutFormEvent.NameChanged("  "))

        model.onEvent(EditWorkoutEvent.Save)
        advanceUntilIdle()

        assertEquals(listOf(WorkoutDraftIssue.Name), model.content().view.issues)
        assertTrue(repository.savedDrafts.isEmpty())
    }

    @Test
    fun offlineSavingAndDeletingAreRefusedWithActionFailed() = runTest(dispatcher) {
        seed()
        val model = viewModel()
        advanceUntilIdle()
        connectivity.state.value = false
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(EditWorkoutEvent.Save)
            assertEquals(EditWorkoutEffect.ActionFailed, awaitItem())
            model.onEvent(EditWorkoutEvent.Delete)
            assertEquals(EditWorkoutEffect.ActionFailed, awaitItem())
        }
        assertEquals(false, model.content().canSave)
        assertTrue(repository.savedDrafts.isEmpty())
        assertTrue(repository.deleted.isEmpty())
    }

    @Test
    fun aSaveFailureKeepsTheEditsAndEmitsActionFailed() = runTest(dispatcher) {
        seed()
        repository.saveCompletedFailure = AppError.Network
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(WorkoutFormEvent.NameChanged("Otro"))

        model.effects.test {
            model.onEvent(EditWorkoutEvent.Save)
            advanceUntilIdle()

            assertEquals(EditWorkoutEffect.ActionFailed, awaitItem())
        }
        assertEquals("Otro", model.form().name)
        assertEquals(false, model.content().busy)
    }

    @Test
    fun deletingIsImmediateWithoutAnyConfirmationState() = runTest(dispatcher) {
        seed()
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(EditWorkoutEvent.Delete)
            advanceUntilIdle()

            assertEquals(EditWorkoutEffect.Deleted, awaitItem())
        }
        assertEquals(listOf("s1"), repository.deleted)
    }

    @Test
    fun aDeletionFailureEmitsActionFailedAndKeepsTheForm() = runTest(dispatcher) {
        seed()
        repository.deleteFailure = AppError.Network
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(EditWorkoutEvent.Delete)
            advanceUntilIdle()

            assertEquals(EditWorkoutEffect.ActionFailed, awaitItem())
        }
        assertEquals("Torso", model.form().name)
    }

    @Test
    fun aVeryOldWorkoutOutsideTheHistoryWindowLoadsEditsAndDeletes() = runTest(dispatcher) {
        repository.completed += (1..30).map { completedSession("n$it", daysAgo = it) }
        repository.completed += completedSession("old", daysAgo = 3000)
        val model = viewModel("old")
        advanceUntilIdle()

        model.onEvent(WorkoutFormEvent.NameChanged("Viejo"))
        model.onEvent(EditWorkoutEvent.Save)
        advanceUntilIdle()

        assertEquals("old", repository.savedDrafts.single().second)
        assertEquals("Viejo", repository.savedDrafts.single().first.name)
    }

    @Test
    fun aMissingWorkoutShowsTheErrorAndRetryRecovers() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(EditWorkoutUiState.Error, model.uiState.value)

        seed()
        model.onEvent(EditWorkoutEvent.Retry)
        advanceUntilIdle()

        assertEquals("Torso", model.form().name)
    }

    @Test
    fun theHalfEditedFormSurvivesARecreatedViewModelWithoutReloadingTheSource() = runTest(dispatcher) {
        seed()
        val handle = SavedStateHandle()
        val first = viewModel(savedState = handle)
        advanceUntilIdle()
        first.onEvent(WorkoutFormEvent.NameChanged("Otro"))
        first.onEvent(WorkoutFormEvent.ValueChanged(0, 0, SetField.Reps, "3"))

        val second = viewModel(savedState = handle)
        advanceUntilIdle()

        assertEquals("Otro", second.form().name)
        assertEquals("3", second.form().exercises.single().sets[0].reps)
        assertTrue(repository.savedDrafts.isEmpty())
    }
}
