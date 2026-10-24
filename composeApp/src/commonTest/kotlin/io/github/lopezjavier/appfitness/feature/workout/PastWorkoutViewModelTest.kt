package io.github.lopezjavier.appfitness.feature.workout

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.usecase.WorkoutDraftIssue
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import io.github.lopezjavier.appfitness.feature.routines.FakeRoutineRepository
import io.github.lopezjavier.appfitness.feature.routines.routineOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PastWorkoutViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeWorkoutSessionRepository()
    private val routines = FakeRoutineRepository()
    private val connectivity = FakeConnectivityObserver()
    private val exercises = FakeExerciseRepository(listOf(exerciseRow("e1", "bench")))
    private val library = FakeExerciseLibrary(mapOf("bench" to exerciseInfo("bench", "Press banca")))

    // The fake clock's day is 2026-10-19.
    private val yesterday = LocalDate(2026, 10, 18)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        source: PastWorkoutSource = PastWorkoutSource.Empty,
        savedState: SavedStateHandle = SavedStateHandle(),
    ) = PastWorkoutViewModel(
        source = source,
        repository = repository,
        routineRepository = routines,
        exerciseRepository = exercises,
        exerciseLibrary = library,
        connectivity = connectivity,
        clock = MutableClock(),
        savedState = savedState,
    )

    private fun PastWorkoutViewModel.content() = assertIs<PastWorkoutUiState.Content>(uiState.value)

    private fun PastWorkoutViewModel.form() = content().view.form

    private fun PastWorkoutViewModel.fillValidEmptyWorkout() {
        onEvent(WorkoutFormEvent.NameChanged("Piernas"))
        onEvent(WorkoutFormEvent.AddExercise("e1"))
        onEvent(WorkoutFormEvent.ValueChanged(0, 0, SetField.Weight, "100"))
        onEvent(WorkoutFormEvent.ValueChanged(0, 0, SetField.Reps, "5"))
    }

    @Test
    fun anEmptyWorkoutStartsOnYesterdayWithNothingToSave() = runTest(dispatcher) {
        val model = viewModel()
        assertEquals(PastWorkoutUiState.Loading, model.uiState.value)

        advanceUntilIdle()

        val content = model.content()
        assertEquals(yesterday, content.view.form.date)
        assertEquals(LocalDate(2026, 10, 19), content.today)
        assertEquals(listOf(WorkoutDraftIssue.Name, WorkoutDraftIssue.Exercises), content.view.issues)
        assertEquals(false, content.canSave)
    }

    @Test
    fun savingAFilledWorkoutCreatesACompletedSessionAndEmitsItsId() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        model.fillValidEmptyWorkout()
        model.onEvent(PastWorkoutEvent.DateChanged(LocalDate(2026, 10, 15)))
        model.onEvent(WorkoutFormEvent.DurationChanged("50"))

        model.effects.test {
            model.onEvent(PastWorkoutEvent.Save)
            advanceUntilIdle()

            assertEquals(PastWorkoutEffect.Saved("s-saved"), awaitItem())
        }

        val (draft, sessionId) = repository.savedDrafts.single()
        assertNull(sessionId)
        assertEquals("Piernas", draft.name)
        assertEquals(LocalDate(2026, 10, 15), draft.date)
        assertEquals(50, draft.durationMinutes)
        assertEquals(SetEntry.WeightReps(100.0, 5), draft.exercises.single().sets.single().entry)
    }

    @Test
    fun savingNeverUsesTheLiveSessionApi() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        model.fillValidEmptyWorkout()

        model.onEvent(PastWorkoutEvent.Save)
        advanceUntilIdle()

        assertTrue(repository.startedRoutines.isEmpty())
        assertTrue(repository.upserts.isEmpty())
        assertTrue(repository.finished.isEmpty())
        assertEquals(1, repository.savedDrafts.size)
    }

    @Test
    fun fromARoutineThePlanIsPrefilledAndTheRoutineIsNotModified() = runTest(dispatcher) {
        routines.routines += routineOf("r1", "Torso", "e1")
        val model = viewModel(PastWorkoutSource.FromRoutine("r1"))
        advanceUntilIdle()

        val form = model.form()
        assertEquals("Torso", form.name)
        assertEquals("r1", form.routineId)
        assertEquals(listOf("8", "8", "8"), form.exercises.single().sets.map { it.reps })
        assertEquals(120, form.exercises.single().restSeconds)

        model.onEvent(WorkoutFormEvent.ValueChanged(0, 0, SetField.Reps, "12"))
        model.onEvent(WorkoutFormEvent.RemoveExercise(0))
        advanceUntilIdle()

        assertEquals(3, routines.routines.single().exercises.single().plannedSets.size)
        assertTrue(routines.saved.isEmpty())
    }

    @Test
    fun fromAFinishedWorkoutItsCompletedSetsFillTheFormAsANewWorkout() = runTest(dispatcher) {
        repository.completed += completedSession("s1")
        val model = viewModel(PastWorkoutSource.FromSession("s1"))
        advanceUntilIdle()

        val form = model.form()
        assertEquals("Torso", form.name)
        assertEquals(yesterday, form.date)
        assertNull(form.routineId)
        assertEquals("", form.duration)
        assertEquals(listOf("8", "8"), form.exercises.single().sets.map { it.reps })

        model.onEvent(WorkoutFormEvent.NameChanged("Otro"))
        model.onEvent(PastWorkoutEvent.Save)
        advanceUntilIdle()

        assertNull(repository.savedDrafts.single().second)
        assertEquals("Torso", repository.completed.single { it.id == "s1" }.name)
        assertEquals(2, repository.completed.single { it.id == "s1" }.exercises.single().sets.size)
    }

    @Test
    fun aFutureDateIsFlaggedAndBlocksSaveWhileTodayIsAccepted() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        model.fillValidEmptyWorkout()

        model.onEvent(PastWorkoutEvent.DateChanged(LocalDate(2026, 10, 20)))
        assertEquals(listOf(WorkoutDraftIssue.Date), model.content().view.issues)
        model.onEvent(PastWorkoutEvent.Save)
        advanceUntilIdle()
        assertTrue(repository.savedDrafts.isEmpty())

        model.onEvent(PastWorkoutEvent.DateChanged(LocalDate(2026, 10, 19)))
        assertTrue(model.content().canSave)
    }

    @Test
    fun anInvalidFormIsNotSentAndFlagsTheFields() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(WorkoutFormEvent.AddExercise("e1"))

        model.onEvent(PastWorkoutEvent.Save)
        advanceUntilIdle()

        val content = model.content()
        assertTrue(WorkoutDraftIssue.Name in content.view.issues)
        assertTrue(WorkoutDraftIssue.SetValue(0, 0) in content.view.issues)
        assertTrue(content.view.submitAttempted)
        assertTrue(repository.savedDrafts.isEmpty())
    }

    @Test
    fun offlineSaveIsRefusedWithActionFailed() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        model.fillValidEmptyWorkout()
        connectivity.state.value = false
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(PastWorkoutEvent.Save)
            assertEquals(PastWorkoutEffect.ActionFailed, awaitItem())
        }
        assertEquals(false, model.content().canSave)
        assertTrue(repository.savedDrafts.isEmpty())
    }

    @Test
    fun aSaveFailureKeepsTheFormAndEmitsActionFailed() = runTest(dispatcher) {
        repository.saveCompletedFailure = AppError.Network
        val model = viewModel()
        advanceUntilIdle()
        model.fillValidEmptyWorkout()

        model.effects.test {
            model.onEvent(PastWorkoutEvent.Save)
            advanceUntilIdle()

            assertEquals(PastWorkoutEffect.ActionFailed, awaitItem())
        }
        assertEquals("Piernas", model.form().name)
        assertEquals(false, model.content().busy)
    }

    @Test
    fun aMissingSourceShowsTheErrorAndRetryRecovers() = runTest(dispatcher) {
        val model = viewModel(PastWorkoutSource.FromRoutine("r1"))
        advanceUntilIdle()
        assertEquals(PastWorkoutUiState.Error, model.uiState.value)

        routines.routines += routineOf("r1", "Torso", "e1")
        model.onEvent(PastWorkoutEvent.Retry)
        advanceUntilIdle()

        assertEquals("Torso", model.form().name)
    }

    @Test
    fun aMissingFinishedWorkoutShowsTheError() = runTest(dispatcher) {
        val model = viewModel(PastWorkoutSource.FromSession("nope"))
        advanceUntilIdle()

        assertEquals(PastWorkoutUiState.Error, model.uiState.value)
    }

    @Test
    fun theHalfFilledFormAndItsDateSurviveARecreatedViewModel() = runTest(dispatcher) {
        routines.routines += routineOf("r1", "Torso", "e1")
        val handle = SavedStateHandle()
        val first = viewModel(PastWorkoutSource.FromRoutine("r1"), handle)
        advanceUntilIdle()
        first.onEvent(PastWorkoutEvent.DateChanged(LocalDate(2026, 9, 1)))
        first.onEvent(WorkoutFormEvent.ValueChanged(0, 0, SetField.Reps, "12"))

        val second = viewModel(PastWorkoutSource.FromRoutine("r1"), handle)
        advanceUntilIdle()

        assertEquals(LocalDate(2026, 9, 1), second.form().date)
        assertEquals("12", second.form().exercises.single().sets[0].reps)
        assertTrue(repository.savedDrafts.isEmpty())
    }
}
