package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeCoachSummaryRepository
import io.github.lopezjavier.appfitness.app.ReminderCoordinator
import io.github.lopezjavier.appfitness.app.ReminderEnv
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.ReminderKind
import io.github.lopezjavier.appfitness.domain.model.StallCause
import io.github.lopezjavier.appfitness.domain.model.StallDiagnosis
import io.github.lopezjavier.appfitness.domain.usecase.ExerciseNameResolver
import io.github.lopezjavier.appfitness.domain.usecase.MarkWeekSummarySeen
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WeekSummaryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val env = ReminderEnv(LocalDate(2026, 11, 10))
    private val summaries: FakeCoachSummaryRepository get() = env.summaries
    private val exercises = FakeExerciseRepository(listOf(exerciseRow("id-bench", "bench")))
    private val library = FakeExerciseLibrary(mapOf("bench" to exerciseInfo("bench", "Press banca")))

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(
        saved: SavedStateHandle = SavedStateHandle(),
        coordinator: ReminderCoordinator = ReminderCoordinator(env.load, backgroundScope),
    ) = WeekSummaryViewModel(
        summaries = summaries,
        markSeen = MarkWeekSummarySeen(summaries),
        reminders = coordinator,
        names = ExerciseNameResolver(exercises, library),
        clock = env.clock,
        savedState = saved,
    )

    @Test
    fun load_showsTheLatestSummaryWithTheResolvedNames() = runTest(dispatcher) {
        summaries.latest = Result.success(weekSummary(changes = listOf(coachChange(exerciseId = "id-bench"))))
        val model = viewModel()
        assertEquals(WeekSummaryUiState.Loading, model.uiState.value)

        advanceUntilIdle()

        val content = assertIs<WeekSummaryUiState.Content>(model.uiState.value)
        assertEquals(3, content.summary.sessionsDone)
        assertEquals("Press banca", content.exerciseNames["id-bench"])
    }

    @Test
    fun load_passesTheStallsThroughWithTheirNames() = runTest(dispatcher) {
        val stall = StallDiagnosis("id-bench", StallCause.Sleep(6.2))
        summaries.latest = Result.success(weekSummary(stalls = listOf(stall)))
        val model = viewModel()

        advanceUntilIdle()

        val content = assertIs<WeekSummaryUiState.Content>(model.uiState.value)
        assertEquals(listOf(stall), content.summary.stalls)
        assertEquals("Press banca", content.exerciseNames["id-bench"])
    }

    @Test
    fun noSummary_isEmptyAndMarksNothing() = runTest(dispatcher) {
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(WeekSummaryUiState.Empty, model.uiState.value)
        assertTrue(summaries.seen.isEmpty())
    }

    @Test
    fun failedRead_isErrorAndMarksNothing() = runTest(dispatcher) {
        summaries.latest = Result.failure(AppException(AppError.Unknown))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(WeekSummaryUiState.Error, model.uiState.value)
        assertTrue(summaries.seen.isEmpty())
    }

    @Test
    fun networkFailure_isOffline() = runTest(dispatcher) {
        summaries.latest = Result.failure(AppException(AppError.Network))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(WeekSummaryUiState.Offline, model.uiState.value)
    }

    @Test
    fun retry_loadsAgainAfterAFailure() = runTest(dispatcher) {
        summaries.latest = Result.failure(AppException(AppError.Network))
        val model = viewModel()
        advanceUntilIdle()
        summaries.latest = Result.success(weekSummary())

        model.onEvent(WeekSummaryEvent.Retry)
        advanceUntilIdle()

        assertIs<WeekSummaryUiState.Content>(model.uiState.value)
    }

    @Test
    fun unseenSummary_isMarkedSeenOnceAndItsNoticeClears() = runTest(dispatcher) {
        summaries.latest = Result.success(weekSummary(seen = false))
        val coordinator = ReminderCoordinator(env.load, backgroundScope)
        coordinator.refresh()
        assertEquals(listOf(ReminderKind.WeekSummary), coordinator.reminders.value.map { it.kind })

        viewModel(coordinator = coordinator)
        advanceUntilIdle()

        assertEquals(listOf("plan-1" to 2), summaries.seen)
        assertTrue(coordinator.reminders.value.none { it.kind == ReminderKind.WeekSummary })
    }

    @Test
    fun recreatedModelOverTheSameHandle_doesNotMarkAgain() = runTest(dispatcher) {
        summaries.latest = Result.success(weekSummary(seen = false))
        val saved = SavedStateHandle()
        viewModel(saved)
        advanceUntilIdle()
        summaries.latest = Result.success(weekSummary(seen = false))

        viewModel(saved)
        advanceUntilIdle()

        assertEquals(1, summaries.seen.size)
    }

    @Test
    fun alreadySeenSummary_isNotMarkedAgain() = runTest(dispatcher) {
        summaries.latest = Result.success(weekSummary(seen = true))
        viewModel()

        advanceUntilIdle()

        assertTrue(summaries.seen.isEmpty())
    }
}
