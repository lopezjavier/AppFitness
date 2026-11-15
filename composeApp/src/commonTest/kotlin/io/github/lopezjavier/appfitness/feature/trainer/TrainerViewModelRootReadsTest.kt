package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeBodyMeasurementRepository
import io.github.lopezjavier.appfitness.app.FakeCoachSummaryRepository
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.domain.model.WeekSummary
import io.github.lopezjavier.appfitness.domain.usecase.RecordPausedWeekReport
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.test.assertNull
import kotlin.time.Instant

/** Root reads added in T-283: the weight-pace input and the paused-week recording (US-129). */
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
