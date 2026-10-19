package io.github.lopezjavier.appfitness.feature.analytics

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.ExerciseHistoryPoint
import io.github.lopezjavier.appfitness.domain.model.MaxRepsPoint
import io.github.lopezjavier.appfitness.domain.model.ProgressMode
import io.github.lopezjavier.appfitness.domain.model.ProgressRange
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseProgressViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val strength = FakeStrengthRepository()
    private val workouts = FakeWorkoutSessionRepository()
    private val exercises = FakeExerciseRepository(listOf(exerciseRow("bench", "bench")))
    private val library = FakeExerciseLibrary(mapOf("bench" to exerciseInfo("bench", "Press de banca")))

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = ExerciseProgressViewModel("bench", workouts, strength, exercises, library, MutableClock())

    private fun ExerciseProgressViewModel.content() = assertIs<ExerciseProgressUiState.Content>(uiState.value)

    private fun point(date: LocalDate, best: Double?, volume: Double = 1000.0) =
        ExerciseHistoryPoint("s-$date", date, best, volume)

    @Test
    fun startsLoadingThenShowsBothSeriesInTheDefaultRange() = runTest(dispatcher) {
        workouts.history = mapOf(
            "bench" to listOf(
                point(LocalDate(2026, 5, 1), 90.0),
                point(LocalDate(2026, 9, 1), 100.0),
                point(LocalDate(2026, 10, 15), 110.0),
            ),
        )
        val model = viewModel()
        assertEquals(ExerciseProgressUiState.Loading, model.uiState.value)

        advanceUntilIdle()

        val content = model.content()
        assertEquals("Press de banca", content.name)
        assertEquals(ProgressRange.ThreeMonths, content.range)
        assertEquals(ProgressMode.OneRepMax, content.progress.mode)
        assertEquals(listOf(100.0, 110.0), content.progress.primary.map { it.value })
        assertEquals(2, content.progress.volume.size)
    }

    @Test
    fun changingTheRangeFiltersTheSeriesWithoutReloading() = runTest(dispatcher) {
        workouts.history = mapOf(
            "bench" to listOf(point(LocalDate(2026, 5, 1), 90.0), point(LocalDate(2026, 10, 15), 110.0)),
        )
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(1, model.content().progress.primary.size)

        model.onEvent(ExerciseProgressEvent.SelectRange(ProgressRange.OneYear))

        assertEquals(ProgressRange.OneYear, model.content().range)
        assertEquals(2, model.content().progress.primary.size)

        model.onEvent(ExerciseProgressEvent.SelectRange(ProgressRange.OneMonth))

        assertEquals(1, model.content().progress.primary.size)
    }

    @Test
    fun aSingleSessionHasItsValueButNotEnoughData() = runTest(dispatcher) {
        workouts.history = mapOf("bench" to listOf(point(LocalDate(2026, 10, 15), 110.0)))
        val model = viewModel()

        advanceUntilIdle()

        assertFalse(model.content().progress.hasEnoughData)
        assertEquals(110.0, model.content().progress.primary.single().value)
    }

    @Test
    fun anExerciseWithoutLoadShowsMaxRepsInsteadOfOneRepMaxAndVolume() = runTest(dispatcher) {
        workouts.history = mapOf(
            "bench" to listOf(point(LocalDate(2026, 10, 1), null, 0.0), point(LocalDate(2026, 10, 10), null, 0.0)),
        )
        strength.maxReps = mapOf(
            "bench" to listOf(
                MaxRepsPoint("a", LocalDate(2026, 10, 1), 8),
                MaxRepsPoint("b", LocalDate(2026, 10, 10), 10),
            ),
        )
        val model = viewModel()

        advanceUntilIdle()

        val progress = model.content().progress
        assertEquals(ProgressMode.MaxReps, progress.mode)
        assertEquals(listOf(8.0, 10.0), progress.primary.map { it.value })
        assertTrue(progress.volume.isEmpty())
    }

    @Test
    fun repetitionsAreNotFetchedWhenThereIsAOneRepMax() = runTest(dispatcher) {
        workouts.history = mapOf("bench" to listOf(point(LocalDate(2026, 10, 15), 110.0)))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(0, strength.maxRepsCalls)
        assertEquals(ProgressMode.OneRepMax, model.content().progress.mode)
    }

    @Test
    fun noSessionsIsEmpty() = runTest(dispatcher) {
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(ExerciseProgressUiState.Empty, model.uiState.value)
    }

    @Test
    fun aFailureShowsErrorAndRetryRecovers() = runTest(dispatcher) {
        workouts.history = mapOf("bench" to listOf(point(LocalDate(2026, 10, 15), 110.0)))
        workouts.historyFailure = AppError.Network
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(ExerciseProgressUiState.Error, model.uiState.value)

        workouts.historyFailure = null
        model.onEvent(ExerciseProgressEvent.Retry)
        advanceUntilIdle()

        assertEquals(1, model.content().progress.primary.size)
    }

    @Test
    fun aMaxRepsFailureShowsError() = runTest(dispatcher) {
        workouts.history = mapOf("bench" to listOf(point(LocalDate(2026, 10, 15), null, 0.0)))
        strength.maxRepsFailure = AppError.Network
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(ExerciseProgressUiState.Error, model.uiState.value)
    }
}
