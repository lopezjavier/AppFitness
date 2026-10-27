package io.github.lopezjavier.appfitness.feature.profile

import io.github.lopezjavier.appfitness.app.FakeBodyMeasurementRepository
import io.github.lopezjavier.appfitness.app.FakeClock
import io.github.lopezjavier.appfitness.app.FakeProfileRepository
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.DiaryDaySummary
import io.github.lopezjavier.appfitness.domain.model.ExerciseHistoryPoint
import io.github.lopezjavier.appfitness.domain.model.MealTotals
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.model.ProfileSummary
import io.github.lopezjavier.appfitness.feature.analytics.FakeStrengthRepository
import io.github.lopezjavier.appfitness.feature.analytics.completedWith
import io.github.lopezjavier.appfitness.feature.diary.FakeFoodDiaryRepository
import io.github.lopezjavier.appfitness.feature.workout.FakeWorkoutSessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
import kotlin.time.Instant

/** US-05 / US-56 on the Perfil root: the name and photo and the values on the statistics rows. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileRootViewModelTest {
    private val today = LocalDate(2026, 10, 14)
    private val now = Instant.parse("2026-10-14T12:00:00Z")
    private val profile = FakeProfileRepository(Result.success(ProfileSummary("Ana", "https://photo/a.png", true)))
    private val weights = FakeBodyMeasurementRepository(
        BodyMeasurement("w1", Instant.parse("2026-10-10T08:00:00Z"), 79.4, MeasurementSource.Manual),
    )
    private val strength = FakeStrengthRepository()
    private val workouts = FakeWorkoutSessionRepository()
    private val diary = FakeFoodDiaryRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        strength.sessions = listOf(
            completedWith("a", "2026-10-01T10:00:00Z", "bench", "squat"),
            completedWith("b", "2026-10-08T10:00:00Z", "bench"),
        )
        workouts.history = mapOf(
            "bench" to listOf(ExerciseHistoryPoint("s1", LocalDate(2026, 10, 8), 113.5, 0.0)),
            "squat" to listOf(ExerciseHistoryPoint("s2", LocalDate(2026, 10, 1), 140.0, 0.0)),
        )
        diary.summaryDays += listOf(
            DiaryDaySummary(LocalDate(2026, 10, 13), MealTotals(1900.0, 0.0, 0.0, 0.0)),
            DiaryDaySummary(LocalDate(2026, 10, 12), MealTotals(2000.0, 0.0, 0.0, 0.0)),
            DiaryDaySummary(LocalDate(2026, 9, 1), MealTotals(5000.0, 0.0, 0.0, 0.0)),
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = ProfileRootViewModel(
        profileRepository = profile,
        bodyMeasurementRepository = weights,
        strengthRepository = strength,
        workoutSessionRepository = workouts,
        foodDiaryRepository = diary,
        clock = FakeClock(today, now),
    )

    private fun TestScope.loaded(): Pair<ProfileRootViewModel, () -> ProfileRootUiState.Loaded> {
        val viewModel = viewModel()
        advanceUntilIdle()
        return viewModel to { assertIs<ProfileRootUiState.Loaded>(viewModel.uiState.value) }
    }

    @Test
    fun loading_showsTheNameAndThePhoto() = runTest {
        val (_, state) = loaded()

        assertEquals("Ana", state().displayName)
        assertEquals("https://photo/a.png", state().avatarUrl)
    }

    @Test
    fun beforeTheReadFinishes_isLoading() = runTest {
        val viewModel = viewModel()

        assertEquals(ProfileRootUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun missingNameAndPhoto_areNull() = runTest {
        profile.profileOverride = Result.success(ProfileSummary(null, null, true))

        val (_, state) = loaded()

        assertNull(state().displayName)
        assertNull(state().avatarUrl)
    }

    @Test
    fun blankName_isShownAsMissing() = runTest {
        profile.profileOverride = Result.success(ProfileSummary("  ", null, true))

        val (_, state) = loaded()

        assertNull(state().displayName)
    }

    @Test
    fun readFailure_isLoadFailed_andRetryReloads() = runTest {
        profile.profileOverride = Result.failure(AppException(AppError.Network))
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(ProfileRootUiState.LoadFailed, viewModel.uiState.value)

        profile.profileOverride = null
        viewModel.onEvent(ProfileRootEvent.Retry)
        advanceUntilIdle()

        assertEquals(79.4, assertIs<ProfileRootUiState.Loaded>(viewModel.uiState.value).weightKg)
    }

    @Test
    fun highlights_areTheLatestWeight_theRecordOfTheMostTrainedExercise_andTheWeekAverage() = runTest {
        val (_, state) = loaded()

        assertEquals(79.4, state().weightKg)
        assertEquals(113.5, state().strengthRecordKg)
        assertEquals(1950, state().avgKcal)
    }

    @Test
    fun aFailedStrengthRead_leavesOnlyThatValueNull_andNeverFailsTheScreen() = runTest {
        strength.sessionsFailure = AppError.Network

        val (_, state) = loaded()

        assertNull(state().strengthRecordKg)
        assertEquals(79.4, state().weightKg)
        assertEquals(1950, state().avgKcal)
    }

    @Test
    fun aFailedExerciseHistory_leavesTheStrengthValueNull() = runTest {
        workouts.historyFailure = AppError.Network

        val (_, state) = loaded()

        assertNull(state().strengthRecordKg)
        assertEquals(79.4, state().weightKg)
    }

    @Test
    fun noTrainedExercises_meansNoStrengthRecord() = runTest {
        strength.sessions = emptyList()

        val (_, state) = loaded()

        assertNull(state().strengthRecordKg)
    }

    @Test
    fun anExerciseWithoutAOneRepMax_meansNoStrengthRecord() = runTest {
        workouts.history = mapOf("bench" to listOf(ExerciseHistoryPoint("s1", LocalDate(2026, 10, 8), null, 0.0)))

        val (_, state) = loaded()

        assertNull(state().strengthRecordKg)
    }

    @Test
    fun aFailedDiaryRead_leavesOnlyTheCaloriesNull() = runTest {
        diary.failSummaries()

        val (_, state) = loaded()

        assertNull(state().avgKcal)
        assertEquals(79.4, state().weightKg)
        assertEquals(113.5, state().strengthRecordKg)
    }

    @Test
    fun noLoggedDaysInTheLastWeek_meansNoCalories() = runTest {
        diary.summaryDays.clear()
        diary.summaryDays += DiaryDaySummary(LocalDate(2026, 9, 1), MealTotals(5000.0, 0.0, 0.0, 0.0))

        val (_, state) = loaded()

        assertNull(state().avgKcal)
    }

    @Test
    fun noWeightYet_meansNoWeightValue() = runTest {
        weights.row = null

        val (_, state) = loaded()

        assertNull(state().weightKg)
    }
}
