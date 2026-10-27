package io.github.lopezjavier.appfitness.feature.analytics

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.ExerciseHistoryPoint
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import io.github.lopezjavier.appfitness.feature.workout.FakeWorkoutSessionRepository
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
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

@OptIn(ExperimentalCoroutinesApi::class)
class StrengthViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val strength = FakeStrengthRepository()
    private val workouts = FakeWorkoutSessionRepository()
    private val exercises = FakeExerciseRepository(
        listOf(exerciseRow("bench", "bench"), exerciseRow("squat", "squat"), exerciseRow("row", "row")),
    )
    private val library = FakeExerciseLibrary(
        mapOf(
            "bench" to exerciseInfo("bench", "Press de banca"),
            "squat" to exerciseInfo("squat", "Sentadilla"),
            "row" to exerciseInfo("row", "Remo"),
        ),
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) =
        StrengthViewModel(strength, workouts, exercises, library, MutableClock(), savedState)

    private fun StrengthViewModel.content() = assertIs<StrengthUiState.Content>(uiState.value)

    private fun point(date: LocalDate, best: Double?) = ExerciseHistoryPoint("s-$date", date, best, 0.0)

    private fun seedThreeExercises() {
        strength.sessions = listOf(
            completedWith("a", "2026-10-01T10:00:00Z", "bench", "squat"),
            completedWith("b", "2026-10-08T10:00:00Z", "bench", "squat", "row"),
            completedWith("c", "2026-10-15T10:00:00Z", "bench"),
        )
        workouts.history = mapOf(
            "bench" to listOf(point(LocalDate(2026, 9, 1), 108.5), point(LocalDate(2026, 10, 15), 113.5)),
            "squat" to listOf(point(LocalDate(2026, 10, 8), 140.0)),
        )
    }

    @Test
    fun startsLoadingThenShowsTheTopThreeWithTheMostFrequentSelected() = runTest(dispatcher) {
        seedThreeExercises()
        val model = viewModel()
        assertEquals(StrengthUiState.Loading, model.uiState.value)

        advanceUntilIdle()

        val content = model.content()
        assertEquals(listOf("bench", "squat", "row"), content.chips.map { it.exerciseId })
        assertEquals(listOf("Press de banca", "Sentadilla", "Remo"), content.chips.map { it.name })
        assertEquals("bench", content.selectedId)
    }

    @Test
    fun showsTheRecordAndTheMonthlyDeltaOfTheSelectedExercise() = runTest(dispatcher) {
        seedThreeExercises()
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(113.5, model.content().summary?.recordOneRepMax)
        assertEquals(5.0, model.content().summary?.monthlyDelta)
    }

    @Test
    fun theSelectedExerciseSurvivesRecreationFromTheSameSavedState() = runTest(dispatcher) {
        seedThreeExercises()
        val saved = SavedStateHandle()
        val first = viewModel(saved)
        advanceUntilIdle()
        first.onEvent(StrengthEvent.SelectExercise("squat"))
        advanceUntilIdle()

        val second = viewModel(saved)
        advanceUntilIdle()

        assertEquals("squat", second.content().selectedId)
    }

    @Test
    fun selectingAnotherChipShowsItsRecord() = runTest(dispatcher) {
        seedThreeExercises()
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(StrengthEvent.SelectExercise("squat"))
        advanceUntilIdle()

        assertEquals("squat", model.content().selectedId)
        assertEquals(140.0, model.content().summary?.recordOneRepMax)
        assertNull(model.content().summary?.monthlyDelta)
    }

    @Test
    fun theRecordOfANewlySelectedExerciseIsNullWhileItLoads() = runTest(dispatcher) {
        seedThreeExercises()
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(StrengthEvent.SelectExercise("squat"))

        assertNull(model.content().summary)
    }

    @Test
    fun selectingAnUnknownExerciseIsIgnored() = runTest(dispatcher) {
        seedThreeExercises()
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(StrengthEvent.SelectExercise("curl"))
        advanceUntilIdle()

        assertEquals("bench", model.content().selectedId)
    }

    @Test
    fun fewerThanThreeExercisesShowOnlyThose() = runTest(dispatcher) {
        strength.sessions = listOf(completedWith("a", "2026-10-01T10:00:00Z", "bench"))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(listOf("bench"), model.content().chips.map { it.exerciseId })
    }

    @Test
    fun noRecentSessionsIsEmpty() = runTest(dispatcher) {
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(StrengthUiState.Empty, model.uiState.value)
    }

    @Test
    fun aFailureShowsErrorAndRetryRecovers() = runTest(dispatcher) {
        seedThreeExercises()
        strength.sessionsFailure = AppError.Network
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(StrengthUiState.Error, model.uiState.value)

        strength.sessionsFailure = null
        model.onEvent(StrengthEvent.Retry)
        advanceUntilIdle()

        assertEquals("bench", model.content().selectedId)
    }

    @Test
    fun aHistoryFailureShowsError() = runTest(dispatcher) {
        seedThreeExercises()
        workouts.historyFailure = AppError.Network
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(StrengthUiState.Error, model.uiState.value)
    }
}
