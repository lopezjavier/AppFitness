package io.github.lopezjavier.appfitness.feature.activity

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeHealthActivityRepository
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.DailyActivity
import io.github.lopezjavier.appfitness.domain.model.HealthActivityHistory
import io.github.lopezjavier.appfitness.domain.model.RestingHeartRate
import io.github.lopezjavier.appfitness.domain.model.SleepSession
import io.github.lopezjavier.appfitness.domain.model.SleepStage
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.usecase.ActivityMetric
import io.github.lopezjavier.appfitness.domain.usecase.WeightChartRange
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private val MADRID = TimeZone.of("Europe/Madrid")

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // One method per behaviour.
class ActivityViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate(2026, 10, 22)
    private val clock = object : Clock {
        override fun today(): LocalDate = today

        override fun now(): Instant = Instant.parse("2026-10-22T10:00:00Z")
    }
    private val repository = FakeHealthActivityRepository().also { it.zone = MADRID }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        ActivityViewModel(repository, clock, MADRID, FakeConnectivityObserver(), handle)

    private fun night(endedAt: String, minutes: Int, stages: List<SleepStage> = emptyList()) = SleepSession(
        externalId = "s",
        startedAt = Instant.parse(endedAt) - kotlin.time.Duration.parse("PT8H"),
        endedAt = Instant.parse(endedAt),
        minutes = minutes,
        stages = stages,
    )

    private fun stored() {
        repository.history = HealthActivityHistory(
            daily = listOf(
                DailyActivity(LocalDate(2026, 9, 1), 4000, null, null),
                DailyActivity(LocalDate(2026, 10, 15), 5000, 300.0, 2000.0),
                DailyActivity(LocalDate(2026, 10, 21), 8500, 620.4, 2300.6),
                DailyActivity(today, 1200, null, null),
            ),
            sleep = listOf(night("2026-10-21T22:30:00Z", 435)),
            restingHeartRate = listOf(RestingHeartRate(LocalDate(2026, 10, 21), 52, null)),
        )
    }

    private fun ActivityViewModel.content() = assertIs<ActivityUiState.Content>(uiState.value)

    @Test
    fun loading_isShownBeforeTheRepositoryAnswers() = runTest(dispatcher) {
        assertEquals(ActivityUiState.Loading, viewModel().uiState.value)
    }

    @Test
    fun nothingStored_showsEmpty() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        assertEquals(ActivityUiState.Empty, model.uiState.value)
    }

    @Test
    fun defaults_areStepsMonthAndToday_withTodaysSummary() = runTest(dispatcher) {
        stored()
        val model = viewModel()
        advanceUntilIdle()

        val content = model.content()
        assertEquals(today, content.day)
        assertTrue(content.isToday)
        assertEquals(ActivityMetric.Steps, content.chart.metric)
        assertEquals(WeightChartRange.Month, content.chart.range)
        assertEquals(1200, content.summary.steps)
        // The night ending 00:30 local on the 22nd (22:30Z on the 21st) belongs to today.
        assertEquals(435, content.summary.sleep?.minutes)
        assertNull(content.summary.restingHeartRate)
    }

    @Test
    fun chart_showsOnlyTheRangeOfTheSelectedMetric() = runTest(dispatcher) {
        stored()
        val model = viewModel()
        advanceUntilIdle()

        val days = model.content().chart.points.map { it.day }

        assertEquals(listOf(LocalDate(2026, 10, 15), LocalDate(2026, 10, 21), today), days)
    }

    @Test
    fun switchingMetricAndRange_makesNoRepositoryCall_andRecomputesTheChart() = runTest(dispatcher) {
        stored()
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(ActivityEvent.SelectMetric(ActivityMetric.TotalKcal))
        model.onEvent(ActivityEvent.SelectRange(WeightChartRange.Week))
        advanceUntilIdle()

        val chart = model.content().chart
        assertEquals(ActivityMetric.TotalKcal, chart.metric)
        assertEquals(WeightChartRange.Week, chart.range)
        // 1S starts 7 days back (Oct 15, inclusive).
        assertEquals(listOf(LocalDate(2026, 10, 15), LocalDate(2026, 10, 21)), chart.points.map { it.day })
        assertEquals(1, repository.historyCalls)
        assertTrue(repository.dayCalls.isEmpty())
    }

    @Test
    fun yearRange_includesTheOlderDays() = runTest(dispatcher) {
        stored()
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(ActivityEvent.SelectRange(WeightChartRange.Year))

        assertEquals(LocalDate(2026, 9, 1), model.content().chart.points.first().day)
    }

    @Test
    fun missingRestingHeartRate_givesAnEmptyChartWithoutError() = runTest(dispatcher) {
        repository.history =
            HealthActivityHistory(listOf(DailyActivity(today, 100, null, null)), emptyList(), emptyList())
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(ActivityEvent.SelectMetric(ActivityMetric.RestingHeartRate))

        assertTrue(model.content().chart.points.isEmpty())
    }

    @Test
    fun selectingADayInsideTheWindow_usesTheLoadedHistory() = runTest(dispatcher) {
        stored()
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(ActivityEvent.SelectDay(LocalDate(2026, 10, 21)))
        advanceUntilIdle()

        val content = model.content()
        assertFalse(content.isToday)
        assertEquals(8500, content.summary.steps)
        assertEquals(52, content.summary.restingHeartRate)
        assertTrue(repository.dayCalls.isEmpty())
    }

    @Test
    fun selectingADayOutsideTheWindow_fetchesThatDay() = runTest(dispatcher) {
        stored()
        val old = LocalDate(2025, 6, 1)
        val model = viewModel()
        advanceUntilIdle()
        repository.history =
            HealthActivityHistory(listOf(DailyActivity(old, 777, null, null)), emptyList(), emptyList())

        model.onEvent(ActivityEvent.SelectDay(old))
        advanceUntilIdle()

        assertEquals(listOf(old), repository.dayCalls)
        assertEquals(777, model.content().summary.steps)
        assertFalse(model.content().dayError)
    }

    @Test
    fun aFailedDayFetch_keepsTheScreenAndOffersRetry() = runTest(dispatcher) {
        stored()
        val old = LocalDate(2025, 6, 1)
        val model = viewModel()
        advanceUntilIdle()
        repository.dayFailure = AppException(AppError.Unknown)

        model.onEvent(ActivityEvent.SelectDay(old))
        advanceUntilIdle()
        assertTrue(model.content().dayError)

        repository.dayFailure = null
        model.onEvent(ActivityEvent.Retry)
        advanceUntilIdle()

        assertFalse(model.content().dayError)
        assertEquals(listOf(old, old), repository.dayCalls)
    }

    @Test
    fun aDayWithNoData_hasNullValuesNotAFailure() = runTest(dispatcher) {
        stored()
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(ActivityEvent.SelectDay(LocalDate(2026, 10, 3)))

        val summary = model.content().summary
        assertNull(summary.steps)
        assertNull(summary.sleep)
        assertNull(summary.activeKcal)
    }

    @Test
    fun aFutureDay_isIgnored() = runTest(dispatcher) {
        stored()
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(ActivityEvent.SelectDay(LocalDate(2026, 10, 23)))

        assertEquals(today, model.content().day)
    }

    @Test
    fun goToToday_returnsToTodayAndClearsADayError() = runTest(dispatcher) {
        stored()
        val model = viewModel()
        advanceUntilIdle()
        repository.dayFailure = AppException(AppError.Network)
        model.onEvent(ActivityEvent.SelectDay(LocalDate(2025, 1, 1)))
        advanceUntilIdle()

        model.onEvent(ActivityEvent.GoToToday)
        advanceUntilIdle()

        val content = model.content()
        assertTrue(content.isToday)
        assertFalse(content.dayError)
    }

    @Test
    fun markedDays_areTheDaysWithAnyStoredValue() = runTest(dispatcher) {
        stored()
        val model = viewModel()
        advanceUntilIdle()

        assertEquals(
            setOf(LocalDate(2026, 9, 1), LocalDate(2026, 10, 15), LocalDate(2026, 10, 21), today),
            model.content().markedDays,
        )
    }

    @Test
    fun sleepStages_areSummarisedWhenThereAreStages() = runTest(dispatcher) {
        val stages = listOf(
            SleepStage("deep", Instant.parse("2026-10-21T23:00:00Z"), Instant.parse("2026-10-22T00:00:00Z")),
        )
        repository.history = HealthActivityHistory(
            emptyList(),
            listOf(night("2026-10-22T05:00:00Z", 435, stages)),
            emptyList(),
        )
        val model = viewModel()
        advanceUntilIdle()

        assertEquals(mapOf("deep" to 60), model.content().sleepStages?.minutesByStage)
    }

    @Test
    fun failedLoad_showsError_andRetryLoadsAgain() = runTest(dispatcher) {
        repository.failure = AppException(AppError.Unknown)
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(ActivityUiState.Error, model.uiState.value)

        repository.failure = null
        stored()
        model.onEvent(ActivityEvent.Retry)
        advanceUntilIdle()

        assertIs<ActivityUiState.Content>(model.uiState.value)
    }

    @Test
    fun failedLoadWithoutConnection_showsOffline() = runTest(dispatcher) {
        repository.failure = AppException(AppError.Network)
        val model = viewModel()
        advanceUntilIdle()

        assertEquals(ActivityUiState.Offline, model.uiState.value)
    }

    @Test
    fun refresh_reloadsSilentlyAndKeepsTheSelection() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(ActivityUiState.Empty, model.uiState.value)
        stored()
        model.onEvent(ActivityEvent.Refresh)
        advanceUntilIdle()
        model.onEvent(ActivityEvent.SelectMetric(ActivityMetric.Sleep))
        model.onEvent(ActivityEvent.SelectDay(LocalDate(2026, 10, 21)))

        repository.history = repository.history.copy(
            daily = repository.history.daily + DailyActivity(LocalDate(2026, 10, 20), 1, null, null),
        )
        model.onEvent(ActivityEvent.Refresh)
        // Silent: never goes back to Loading.
        assertIs<ActivityUiState.Content>(model.uiState.value)
        advanceUntilIdle()

        val content = model.content()
        assertEquals(3, repository.historyCalls)
        assertEquals(ActivityMetric.Sleep, content.chart.metric)
        assertEquals(LocalDate(2026, 10, 21), content.day)
        assertTrue(LocalDate(2026, 10, 20) in content.markedDays)
    }

    @Test
    fun selection_isRestoredFromASavedStateHandle() = runTest(dispatcher) {
        stored()
        val first = viewModel()
        advanceUntilIdle()
        first.onEvent(ActivityEvent.SelectMetric(ActivityMetric.ActiveKcal))
        first.onEvent(ActivityEvent.SelectRange(WeightChartRange.Quarter))
        first.onEvent(ActivityEvent.SelectDay(LocalDate(2026, 10, 21)))
        val handle = SavedStateHandle(
            mapOf(
                "activity_metric" to ActivityMetric.ActiveKcal.name,
                "activity_range" to WeightChartRange.Quarter.name,
                "activity_day" to "2026-10-21",
            ),
        )

        val restored = viewModel(handle)
        advanceUntilIdle()

        val content = restored.content()
        assertEquals(ActivityMetric.ActiveKcal, content.chart.metric)
        assertEquals(WeightChartRange.Quarter, content.chart.range)
        assertEquals(LocalDate(2026, 10, 21), content.day)
    }

    @Test
    fun selectionEvents_areWrittenToTheSavedStateHandle() = runTest(dispatcher) {
        stored()
        val handle = SavedStateHandle()
        val model = viewModel(handle)
        advanceUntilIdle()

        model.onEvent(ActivityEvent.SelectMetric(ActivityMetric.Sleep))
        model.onEvent(ActivityEvent.SelectRange(WeightChartRange.Year))
        model.onEvent(ActivityEvent.SelectDay(LocalDate(2026, 10, 21)))

        assertEquals("Sleep", handle.get<String>("activity_metric"))
        assertEquals("Year", handle.get<String>("activity_range"))
        assertEquals("2026-10-21", handle.get<String>("activity_day"))

        model.onEvent(ActivityEvent.GoToToday)
        assertEquals("", handle.get<String>("activity_day"))
    }
}
