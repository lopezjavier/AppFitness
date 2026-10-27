package io.github.lopezjavier.appfitness.feature.analytics

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.ExerciseSessionRecords
import io.github.lopezjavier.appfitness.domain.model.MaxRepsPoint
import io.github.lopezjavier.appfitness.domain.model.ProgressMetric
import io.github.lopezjavier.appfitness.domain.model.ProgressRange
import io.github.lopezjavier.appfitness.domain.model.TrainingPreferences
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
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
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
    private val training = FakeTrainingPreferencesRepository()
    private val workouts = FakeWorkoutSessionRepository()
    private val exercises = FakeExerciseRepository(listOf(exerciseRow("bench", "bench")))
    private val library = FakeExerciseLibrary(mapOf("bench" to exerciseInfo("bench", "Press de banca")))

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) = ExerciseProgressViewModel(
        "bench",
        workouts,
        strength,
        exercises,
        library,
        MutableClock(),
        training,
        savedState,
    )

    private fun ExerciseProgressViewModel.content() = assertIs<ExerciseProgressUiState.Content>(uiState.value)

    private fun session(date: LocalDate, oneRm: Double? = null, volume: Double = 1000.0, durationS: Int? = null) =
        ExerciseSessionRecords(
            sessionId = "s-$date",
            startedAt = date.atTime(LocalTime(10, 0)).toInstant(TimeZone.UTC),
            sessionVolume = volume,
            bestOneRm = oneRm,
            bestDurationS = durationS,
        )

    @Test
    fun startsLoadingThenShowsBothSeriesInTheDefaultRange() = runTest(dispatcher) {
        workouts.sessionRecords = mapOf(
            "bench" to listOf(
                session(LocalDate(2026, 5, 1), 90.0),
                session(LocalDate(2026, 9, 1), 100.0),
                session(LocalDate(2026, 10, 15), 110.0),
            ),
        )
        val model = viewModel()
        assertEquals(ExerciseProgressUiState.Loading, model.uiState.value)

        advanceUntilIdle()

        val content = model.content()
        assertEquals("Press de banca", content.name)
        assertEquals(ProgressRange.ThreeMonths, content.range)
        assertEquals(ProgressMetric.OneRepMax, content.progress.mode)
        assertEquals(listOf(100.0, 110.0), content.progress.primary.map { it.value })
        assertEquals(listOf(true, true), content.progress.primary.map { it.isRecord })
        assertEquals(2, content.progress.volume.size)
    }

    @Test
    fun changingTheRangeFiltersTheSeriesWithoutReloadingIncludingAllTime() = runTest(dispatcher) {
        workouts.sessionRecords = mapOf(
            "bench" to listOf(session(LocalDate(2024, 5, 1), 90.0), session(LocalDate(2026, 10, 15), 110.0)),
        )
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(1, model.content().progress.primary.size)

        model.onEvent(ExerciseProgressEvent.SelectRange(ProgressRange.OneYear))
        assertEquals(1, model.content().progress.primary.size)

        model.onEvent(ExerciseProgressEvent.SelectRange(ProgressRange.AllTime))
        assertEquals(ProgressRange.AllTime, model.content().range)
        assertEquals(2, model.content().progress.primary.size)

        model.onEvent(ExerciseProgressEvent.SelectRange(ProgressRange.OneMonth))
        assertEquals(1, model.content().progress.primary.size)
    }

    @Test
    fun theSelectedRangeIsRestoredFromTheSavedState() = runTest(dispatcher) {
        workouts.sessionRecords = mapOf(
            "bench" to listOf(session(LocalDate(2024, 5, 1), 90.0), session(LocalDate(2026, 10, 15), 110.0)),
        )
        val saved = SavedStateHandle()
        val first = viewModel(saved)
        advanceUntilIdle()
        first.onEvent(ExerciseProgressEvent.SelectRange(ProgressRange.AllTime))

        val recreated = viewModel(saved)
        advanceUntilIdle()

        assertEquals(ProgressRange.AllTime, recreated.content().range)
        assertEquals(2, recreated.content().progress.primary.size)
    }

    @Test
    fun anUnknownStoredRangeFallsBackToTheDefault() = runTest(dispatcher) {
        workouts.sessionRecords = mapOf("bench" to listOf(session(LocalDate(2026, 10, 15), 110.0)))
        val model = viewModel(SavedStateHandle(mapOf("range" to "Nonsense")))

        advanceUntilIdle()

        assertEquals(ProgressRange.ThreeMonths, model.content().range)
    }

    @Test
    fun aSingleSessionHasItsValueButNotEnoughData() = runTest(dispatcher) {
        workouts.sessionRecords = mapOf("bench" to listOf(session(LocalDate(2026, 10, 15), 110.0)))
        val model = viewModel()

        advanceUntilIdle()

        assertFalse(model.content().progress.hasEnoughData)
        assertEquals(110.0, model.content().progress.primary.single().value)
    }

    @Test
    fun theChartFollowsTheMetricTypeOfTheExercise() = runTest(dispatcher) {
        exercises.rows = listOf(exerciseRow("bench", "bench").copy(metricType = ExerciseMetricType.Duration))
        workouts.sessionRecords = mapOf(
            "bench" to listOf(
                session(LocalDate(2026, 10, 1), durationS = 60),
                session(LocalDate(2026, 10, 10), durationS = 90),
            ),
        )
        val model = viewModel()

        advanceUntilIdle()

        val progress = model.content().progress
        assertEquals(ProgressMetric.Duration, progress.mode)
        assertEquals(listOf(60.0, 90.0), progress.primary.map { it.value })
        assertTrue(progress.volume.isEmpty())
    }

    @Test
    fun anExerciseWithoutLoadShowsMaxRepsInsteadOfOneRepMaxAndVolume() = runTest(dispatcher) {
        workouts.sessionRecords = mapOf(
            "bench" to
                listOf(session(LocalDate(2026, 10, 1), volume = 0.0), session(LocalDate(2026, 10, 10), volume = 0.0)),
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
        assertEquals(ProgressMetric.MaxReps, progress.mode)
        assertEquals(listOf(8.0, 10.0), progress.primary.map { it.value })
        assertTrue(progress.volume.isEmpty())
    }

    @Test
    fun maxRepsFollowTheWarmUpPreference() = runTest(dispatcher) {
        workouts.sessionRecords = mapOf("bench" to listOf(session(LocalDate(2026, 10, 1), volume = 0.0)))
        strength.maxReps = mapOf("bench" to listOf(MaxRepsPoint("a", LocalDate(2026, 10, 1), 8)))
        strength.maxRepsWarmUp = mapOf("bench" to listOf(MaxRepsPoint("a", LocalDate(2026, 10, 1), 15)))

        val off = viewModel()
        advanceUntilIdle()
        training.result = Result.success(TrainingPreferences(warmUpIncluded = true))
        val on = viewModel()
        advanceUntilIdle()

        assertEquals(listOf(8.0), off.content().progress.primary.map { it.value })
        assertEquals(listOf(15.0), on.content().progress.primary.map { it.value })
        assertEquals(listOf(false, true), strength.maxRepsWarmUpArgs)
    }

    @Test
    fun repetitionsAreNotFetchedWhenThereIsAOneRepMax() = runTest(dispatcher) {
        workouts.sessionRecords = mapOf("bench" to listOf(session(LocalDate(2026, 10, 15), 110.0)))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(0, strength.maxRepsCalls)
        assertEquals(ProgressMetric.OneRepMax, model.content().progress.mode)
    }

    @Test
    fun noSessionsIsEmpty() = runTest(dispatcher) {
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(ExerciseProgressUiState.Empty("Press de banca"), model.uiState.value)
    }

    @Test
    fun aFailureShowsErrorAndRetryRecovers() = runTest(dispatcher) {
        workouts.sessionRecords = mapOf("bench" to listOf(session(LocalDate(2026, 10, 15), 110.0)))
        workouts.recordsFailure = AppError.Network
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(ExerciseProgressUiState.Error, model.uiState.value)

        workouts.recordsFailure = null
        model.onEvent(ExerciseProgressEvent.Retry)
        advanceUntilIdle()

        assertEquals(1, model.content().progress.primary.size)
    }

    @Test
    fun aMaxRepsFailureShowsError() = runTest(dispatcher) {
        workouts.sessionRecords = mapOf("bench" to listOf(session(LocalDate(2026, 10, 15), volume = 0.0)))
        strength.maxRepsFailure = AppError.Network
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(ExerciseProgressUiState.Error, model.uiState.value)
    }
}
