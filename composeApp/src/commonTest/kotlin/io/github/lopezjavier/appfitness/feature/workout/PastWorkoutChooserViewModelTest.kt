package io.github.lopezjavier.appfitness.feature.workout

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.feature.routines.FakeRoutineRepository
import io.github.lopezjavier.appfitness.feature.routines.routineOf
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

@OptIn(ExperimentalCoroutinesApi::class)
class PastWorkoutChooserViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val routines = FakeRoutineRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) =
        PastWorkoutChooserViewModel(routines, savedState)

    @Test
    fun startsClosedAndOpensOnTheOptions() = runTest(dispatcher) {
        val model = viewModel()
        assertEquals(PastWorkoutChooserStep.Closed, model.uiState.value.step)

        model.onEvent(PastWorkoutChooserEvent.Open)

        assertEquals(PastWorkoutChooserStep.Options, model.uiState.value.step)
    }

    @Test
    fun fromScratchStartsAnEmptyWorkoutAndClosesTheSheet() = runTest(dispatcher) {
        val model = viewModel()
        model.onEvent(PastWorkoutChooserEvent.Open)

        model.effects.test {
            model.onEvent(PastWorkoutChooserEvent.ChooseEmpty)

            assertEquals(PastWorkoutChooserEffect.Start(PastWorkoutSource.Empty), awaitItem())
        }
        assertEquals(PastWorkoutChooserStep.Closed, model.uiState.value.step)
    }

    @Test
    fun theRoutineStepListsRoutinesAndPickingOneStartsFromIt() = runTest(dispatcher) {
        routines.routines += routineOf("r1", "Torso", "e1")
        val model = viewModel()
        model.onEvent(PastWorkoutChooserEvent.Open)

        model.onEvent(PastWorkoutChooserEvent.ChooseRoutine)
        advanceUntilIdle()

        assertEquals(PastWorkoutChooserStep.Routines, model.uiState.value.step)
        assertEquals(
            listOf("r1"),
            assertIs<ChooserRoutines.Content>(model.uiState.value.routines).routines.map { it.id },
        )
        model.effects.test {
            model.onEvent(PastWorkoutChooserEvent.PickRoutine("r1"))

            assertEquals(PastWorkoutChooserEffect.Start(PastWorkoutSource.FromRoutine("r1")), awaitItem())
        }
    }

    @Test
    fun theWorkoutStepStartsFromThePickedHistoryRow() = runTest(dispatcher) {
        val model = viewModel()
        model.onEvent(PastWorkoutChooserEvent.Open)
        model.onEvent(PastWorkoutChooserEvent.ChooseWorkout)
        assertEquals(PastWorkoutChooserStep.Sessions, model.uiState.value.step)

        model.effects.test {
            model.onEvent(PastWorkoutChooserEvent.PickWorkout("s1"))

            assertEquals(PastWorkoutChooserEffect.Start(PastWorkoutSource.FromSession("s1")), awaitItem())
        }
    }

    @Test
    fun noRoutinesIsEmptyAndAFailureIsAnErrorThatRetryRecovers() = runTest(dispatcher) {
        val model = viewModel()
        model.onEvent(PastWorkoutChooserEvent.Open)
        model.onEvent(PastWorkoutChooserEvent.ChooseRoutine)
        advanceUntilIdle()
        assertEquals(ChooserRoutines.Empty, model.uiState.value.routines)

        routines.failure = AppError.Network
        model.onEvent(PastWorkoutChooserEvent.Back)
        model.onEvent(PastWorkoutChooserEvent.ChooseRoutine)
        advanceUntilIdle()
        assertEquals(ChooserRoutines.Error, model.uiState.value.routines)

        routines.failure = null
        routines.routines += routineOf("r1", "Torso", "e1")
        model.onEvent(PastWorkoutChooserEvent.RetryRoutines)
        advanceUntilIdle()
        assertIs<ChooserRoutines.Content>(model.uiState.value.routines)
    }

    @Test
    fun backReturnsToTheOptionsAndDismissCloses() = runTest(dispatcher) {
        val model = viewModel()
        model.onEvent(PastWorkoutChooserEvent.Open)
        model.onEvent(PastWorkoutChooserEvent.ChooseWorkout)

        model.onEvent(PastWorkoutChooserEvent.Back)
        assertEquals(PastWorkoutChooserStep.Options, model.uiState.value.step)
        model.onEvent(PastWorkoutChooserEvent.Dismiss)
        assertEquals(PastWorkoutChooserStep.Closed, model.uiState.value.step)
    }

    @Test
    fun theOpenStepSurvivesARecreatedViewModelAndReloadsItsRoutines() = runTest(dispatcher) {
        routines.routines += routineOf("r1", "Torso", "e1")
        val handle = SavedStateHandle()
        val first = viewModel(handle)
        first.onEvent(PastWorkoutChooserEvent.Open)
        first.onEvent(PastWorkoutChooserEvent.ChooseRoutine)

        val second = viewModel(handle)
        advanceUntilIdle()

        assertEquals(PastWorkoutChooserStep.Routines, second.uiState.value.step)
        assertIs<ChooserRoutines.Content>(second.uiState.value.routines)
    }
}
