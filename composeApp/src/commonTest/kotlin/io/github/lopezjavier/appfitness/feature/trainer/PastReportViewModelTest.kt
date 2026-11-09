package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.app.ReminderCoordinator
import io.github.lopezjavier.appfitness.app.ReminderEnv
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.BlockReview
import io.github.lopezjavier.appfitness.domain.model.PhaseDecision
import io.github.lopezjavier.appfitness.domain.model.WeekSummary
import io.github.lopezjavier.appfitness.domain.usecase.DecideCalorieProposalUseCase
import io.github.lopezjavier.appfitness.domain.usecase.DecidePhaseChangeUseCase
import io.github.lopezjavier.appfitness.domain.usecase.ExerciseNameResolver
import io.github.lopezjavier.appfitness.domain.usecase.LoadBlockReviewUseCase
import io.github.lopezjavier.appfitness.domain.usecase.LoadPastReport
import io.github.lopezjavier.appfitness.domain.usecase.MarkBlockReviewSeen
import io.github.lopezjavier.appfitness.domain.usecase.MarkWeekSummarySeen
import io.github.lopezjavier.appfitness.domain.usecase.RecordPausedWeekReport
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PastReportViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val reports = FakeReportRepository()
    private val exercises = FakeExerciseRepository(listOf(exerciseRow("id-bench", "bench")))
    private val library = FakeExerciseLibrary(mapOf("bench" to exerciseInfo("bench", "Press banca")))

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun resolver() = ExerciseNameResolver(exercises, library)

    private fun viewModel(id: String) = PastReportViewModel(id, LoadPastReport(reports), resolver())

    @Test
    fun weekReport_buildsTheSameUiModelAsTheLiveWeekSummaryScreen() = runTest(dispatcher) {
        val summary = weekSummary(seen = true, changes = listOf(coachChange(exerciseId = "id-bench")))
        reports.reports["w1"] = Result.success(weekReport("w1", summary))
        val live = liveWeekContent(summary)

        val model = viewModel("w1")
        assertEquals(PastReportUiState.Loading, model.uiState.value)
        advanceUntilIdle()

        val state = assertIs<PastReportUiState.Week>(model.uiState.value)
        assertEquals(live, state.content)
        assertEquals("w1", state.item.id)
        assertEquals("Press banca", state.content.exerciseNames["id-bench"])
    }

    @Test
    fun blockReport_buildsTheSameUiModelAsTheLiveReviewWithoutCalorieProposalOrNextPlan() = runTest(dispatcher) {
        val review = blockReview(phaseDecision = PhaseDecision.Accepted, seen = true)
        reports.reports["b1"] = Result.success(blockReport("b1", review))

        val model = viewModel("b1")
        advanceUntilIdle()

        val state = assertIs<PastReportUiState.Block>(model.uiState.value)
        assertEquals(liveBlockContent(review), state.content)
        assertNull(state.content.calorieProposal)
        assertNull(state.content.nextPlanId)
        assertEquals(PhaseDecision.Accepted, state.content.review.phaseDecision)
        assertEquals("Press banca", state.content.exerciseNames["id-bench"])
    }

    @Test
    fun blockReportWithoutDecision_keepsNoDecision() = runTest(dispatcher) {
        reports.reports["b1"] = Result.success(blockReport("b1", blockReview(phaseDecision = null)))

        val model = viewModel("b1")
        advanceUntilIdle()

        assertNull(assertIs<PastReportUiState.Block>(model.uiState.value).content.review.phaseDecision)
    }

    @Test
    fun unknownReport_isNotFound() = runTest(dispatcher) {
        val model = viewModel("gone")

        advanceUntilIdle()

        assertEquals(PastReportUiState.NotFound, model.uiState.value)
        assertEquals(listOf("gone"), reports.gets)
    }

    @Test
    fun networkFailure_isOffline_otherFailureIsError() = runTest(dispatcher) {
        reports.reports["a"] = Result.failure(AppException(AppError.Network))
        reports.reports["b"] = Result.failure(AppException(AppError.Unknown))
        val offline = viewModel("a")
        val error = viewModel("b")

        advanceUntilIdle()

        assertEquals(PastReportUiState.Offline, offline.uiState.value)
        assertEquals(PastReportUiState.Error, error.uiState.value)
    }

    @Test
    fun retry_loadsAgainAfterAFailure() = runTest(dispatcher) {
        reports.reports["w1"] = Result.failure(AppException(AppError.Network))
        val model = viewModel("w1")
        advanceUntilIdle()
        reports.reports["w1"] = Result.success(weekReport("w1"))

        model.onEvent(PastReportEvent.Retry)
        advanceUntilIdle()

        assertIs<PastReportUiState.Week>(model.uiState.value)
    }

    @Test
    fun recreatedModelForTheSameRouteId_showsTheSameReport() = runTest(dispatcher) {
        reports.reports["w1"] = Result.success(weekReport("w1"))
        viewModel("w1")
        advanceUntilIdle()

        val recreated = viewModel("w1")
        advanceUntilIdle()

        assertEquals("w1", assertIs<PastReportUiState.Week>(recreated.uiState.value).item.id)
        assertEquals(listOf("w1", "w1"), reports.gets)
    }

    @Test
    fun failedNameRead_keepsTheContentWithoutNames() = runTest(dispatcher) {
        exercises.failure = AppError.Network
        reports.reports["w1"] = Result.success(weekReport("w1"))
        reports.reports["b1"] = Result.success(blockReport("b1"))
        val week = viewModel("w1")
        val block = viewModel("b1")

        advanceUntilIdle()

        assertTrue(assertIs<PastReportUiState.Week>(week.uiState.value).content.exerciseNames.isEmpty())
        assertTrue(assertIs<PastReportUiState.Block>(block.uiState.value).content.exerciseNames.isEmpty())
    }

    @Test
    fun openingAReport_marksAndRecordsNothing() = runTest(dispatcher) {
        reports.reports["w1"] = Result.success(weekReport("w1"))
        viewModel("w1")

        advanceUntilIdle()

        assertTrue(reports.recorded.isEmpty())
    }

    // The live ViewModels' content for the same model, to prove the stored report goes through the same builders.
    private fun TestScope.liveWeekContent(summary: WeekSummary): WeekSummaryUiState.Content {
        val env = ReminderEnv(LocalDate(2026, 11, 10))
        env.summaries.latest = Result.success(summary)
        val model = WeekSummaryViewModel(
            summaries = env.summaries,
            markSeen = MarkWeekSummarySeen(env.summaries),
            recordPausedWeek = RecordPausedWeekReport(reports),
            reminders = ReminderCoordinator(env.load, backgroundScope),
            names = resolver(),
            clock = env.clock,
        )
        advanceUntilIdle()
        return assertIs<WeekSummaryUiState.Content>(model.uiState.value)
    }

    private fun TestScope.liveBlockContent(review: BlockReview): BlockReviewUiState.Content {
        val env = ReminderEnv(LocalDate(2026, 10, 19))
        env.reviews.review = Result.success(review)
        val plans = FakeCoachPlanRepository()
        val model = BlockReviewViewModel(
            loadReview = LoadBlockReviewUseCase(env.reviews),
            decidePhase = DecidePhaseChangeUseCase(env.reviews),
            plans = plans,
            decideCalorie = DecideCalorieProposalUseCase(plans),
            markSeen = MarkBlockReviewSeen(env.reviews),
            reminders = ReminderCoordinator(env.load, backgroundScope),
            names = resolver(),
            clock = MutableClock(),
        )
        advanceUntilIdle()
        return assertIs<BlockReviewUiState.Content>(model.uiState.value)
    }
}
