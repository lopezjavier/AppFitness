package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeBodyMeasurementRepository
import io.github.lopezjavier.appfitness.app.FakeCoachSummaryRepository
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.DiaryDaySummary
import io.github.lopezjavier.appfitness.domain.model.MealTotals
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.PhaseWeeks
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.domain.model.WeekSummary
import io.github.lopezjavier.appfitness.domain.usecase.LoadWeeksInPhaseUseCase
import io.github.lopezjavier.appfitness.domain.usecase.RecordPausedWeekReport
import io.github.lopezjavier.appfitness.feature.diary.FakeFoodDiaryRepository
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Root reads added in T-283: the weight-pace input, the paused-week recording (US-129), the Calorías mean read from
 * the diary since the phase start and the failed-read rules of the root (R-108).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrainerViewModelRootReadsTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeCoachRepository()
    private val plans = FakeCoachPlanRepository()
    private val blocks = FakeCoachBlockRepository()
    private val weeks = FakeCoachWeeksRepository()
    private val summaries = FakeCoachSummaryRepository()
    private val reports = FakeReportRepository()
    private val body = FakeBodyMeasurementRepository()
    private val diary = FakeFoodDiaryRepository()
    private val reviews = FakeCoachReviewRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) = TrainerViewModel(
        repository = repository,
        clock = MutableClock(),
        plans = plans,
        blocks = blocks,
        coachWeeks = weeks,
        savedState = saved,
        measurements = body,
        summaries = summaries,
        recordPausedWeek = RecordPausedWeekReport(reports),
        timeZone = TimeZone.UTC,
        loadWeeksInPhase = LoadWeeksInPhaseUseCase(reviews),
        diary = diary,
    )

    private fun TrainerViewModel.content() = assertIs<TrainerUiState.Content>(uiState.value)

    private fun acceptPlan() {
        plans.current = Result.success(coachPlan(status = PlanStatus.Accepted))
        blocks.current = Result.success(coachBlock(saved = true))
    }

    private fun weighIn(day: LocalDate, kg: Double) = BodyMeasurement(
        id = "w-$day",
        measuredAt = Instant.parse("${day}T08:00:00Z"),
        weightKg = kg,
        source = MeasurementSource.Manual,
    )

    private fun pausedSummary() =
        WeekSummary.pausedWeek("plan-1", LocalDate(2026, 10, 12), LocalDate(2026, 10, 18)).copy(seen = false)

    // Weight pace

    @Test
    fun weightPace_isNullWithoutAnAcceptedPlan() = runTest(dispatcher) {
        body.rows = listOf(weighIn(LocalDate(2026, 10, 15), 80.0))
        val model = viewModel()

        advanceUntilIdle()

        assertNull(model.content().weightPace)
    }

    @Test
    fun weightPace_isFilledWithAnAcceptedPlan() = runTest(dispatcher) {
        acceptPlan()
        body.rows = listOf(weighIn(LocalDate(2026, 10, 15), 80.0))
        val model = viewModel()

        advanceUntilIdle()

        val pace = assertNotNull(model.content().weightPace)
        assertEquals(-0.375, pace.planRateKgWeek)
    }

    @Test
    fun weightPace_isNullWhenTheMeasurementsAreNotAvailable() = runTest(dispatcher) {
        acceptPlan()
        val model = TrainerViewModel(repository, MutableClock(), plans, blocks, weeks)

        advanceUntilIdle()

        assertNull(model.content().weightPace)
    }

    @Test
    fun fetch_readsTheMeasurementsOnce() = runTest(dispatcher) {
        acceptPlan()
        body.rows = listOf(weighIn(LocalDate(2026, 10, 15), 80.0))
        viewModel()

        advanceUntilIdle()

        assertEquals(1, body.measurementReads)
    }

    @Test
    fun weightPace_isNullWhenTheMeasurementsReadFails() = runTest(dispatcher) {
        acceptPlan()
        body.measurementsResult = Result.failure(AppException(AppError.Network))
        val model = viewModel()

        advanceUntilIdle()

        assertNull(model.content().weightPace)
    }

    // Calorías: mean kcal per logged day since the phase started

    private fun day(date: LocalDate, kcal: Double = 2000.0) = DiaryDaySummary(date, MealTotals(kcal, 0.0, 0.0, 0.0))

    @Test
    fun diaryCalories_readsFromTheStartOfThePhase_andAveragesOnlyTheLoggedDays() = runTest(dispatcher) {
        acceptPlan()
        reviews.weeks = Result.success(PhaseWeeks(Phase.Cut, 4))
        (10..19).forEach { diary.summaryDays += day(LocalDate(2026, 10, it), kcal = 1800.0 + it) }
        diary.summaryDays += day(LocalDate(2026, 9, 1), kcal = 5000.0)
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(LocalDate(2026, 9, 21) to LocalDate(2026, 10, 19), diary.summaryRequests.last())
        assertEquals(DiaryCalories(avgKcal = 1815, days = 10), model.content().diaryCalories)
    }

    @Test
    fun diaryCalories_fallsBackToThePlanStart_whenTheWeeksInPhaseReadFailed() = runTest(dispatcher) {
        acceptPlan()
        reviews.weeks = Result.failure(AppException(AppError.Network))
        diary.summaryDays += day(LocalDate(2026, 10, 19))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(LocalDate(2026, 10, 19) to LocalDate(2026, 10, 19), diary.summaryRequests.last())
        assertEquals(DiaryCalories(avgKcal = 2000, days = 1), model.content().diaryCalories)
    }

    @Test
    fun diaryCalories_isNullForAnEmptyDiary_andForAFailedRead_neverAnErrorScreen() = runTest(dispatcher) {
        acceptPlan()
        val empty = viewModel()
        advanceUntilIdle()
        assertNull(empty.content().diaryCalories)

        diary.failSummaries()
        diary.summaryDays += day(LocalDate(2026, 10, 19))
        val failed = viewModel()
        advanceUntilIdle()

        assertNull(failed.content().diaryCalories)
    }

    @Test
    fun diaryCalories_isNotReadWithoutAnAcceptedPlan() = runTest(dispatcher) {
        diary.summaryDays += day(LocalDate(2026, 10, 19))
        val model = viewModel()

        advanceUntilIdle()

        assertNull(model.content().diaryCalories)
        assertTrue(diary.summaryRequests.isEmpty())
    }

    // R-108: a failed read never becomes the "no plan" state

    @Test
    fun planReadFailingOnce_staysLoadingUntilThePlanArrives_neverNoPlan() = runTest(dispatcher) {
        acceptPlan()
        plans.currentFailures = 1
        val model = viewModel()
        val seen = mutableListOf<TrainerUiState>()
        backgroundScope.launch { model.uiState.collect { seen += it } }

        advanceUntilIdle()

        assertTrue(seen.none { it is TrainerUiState.Content && it.plan == PlanSummary.None })
        assertIs<PlanSummary.Accepted>(model.content().plan)
    }

    @Test
    fun errorThenRefreshWithARecoveredRead_showsTheContent() = runTest(dispatcher) {
        plans.current = Result.failure(AppException(AppError.Network))
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(TrainerUiState.Error, model.uiState.value)

        acceptPlan()
        model.onEvent(TrainerEvent.Refresh)
        advanceUntilIdle()

        assertIs<PlanSummary.Accepted>(model.content().plan)
    }

    @Test
    fun refreshWithAFailingPlanRead_keepsTheContentAlreadyShown() = runTest(dispatcher) {
        acceptPlan()
        val model = viewModel()
        advanceUntilIdle()
        val shown = model.content()

        plans.current = Result.failure(AppException(AppError.Network))
        model.onEvent(TrainerEvent.Refresh)
        advanceUntilIdle()

        assertEquals(shown, model.uiState.value)
    }

    @Test
    fun refreshAfterContent_neverPassesThroughLoading() = runTest(dispatcher) {
        acceptPlan()
        val model = viewModel()
        advanceUntilIdle()
        val seen = mutableListOf<TrainerUiState>()
        backgroundScope.launch { model.uiState.collect { seen += it } }

        model.onEvent(TrainerEvent.Refresh)
        advanceUntilIdle()

        assertFalse(seen.any { it == TrainerUiState.Loading })
    }

    // Paused week recording

    @Test
    fun pausedSummary_isRecordedOnceWithToday_acrossRecreatedModels() = runTest(dispatcher) {
        summaries.latest = Result.success(pausedSummary())
        val saved = SavedStateHandle()
        viewModel(saved)
        advanceUntilIdle()

        viewModel(saved)
        advanceUntilIdle()

        assertEquals(listOf(LocalDate(2026, 10, 19)), reports.recorded)
    }

    @Test
    fun nonPausedSummary_isNeverRecorded() = runTest(dispatcher) {
        summaries.latest = Result.success(weekSummary())
        viewModel()

        advanceUntilIdle()

        assertEquals(emptyList(), reports.recorded)
    }

    @Test
    fun pausedSummary_failedRecordIsSilentAndRetriedOnTheNextLoad() = runTest(dispatcher) {
        summaries.latest = Result.success(pausedSummary())
        reports.recordResult = Result.failure(AppException(AppError.Network))
        val saved = SavedStateHandle()
        val model = viewModel(saved)
        advanceUntilIdle()
        assertIs<TrainerUiState.Content>(model.uiState.value)

        reports.recordResult = Result.success(Unit)
        viewModel(saved)
        advanceUntilIdle()

        assertEquals(2, reports.recorded.size)
    }
}
