package io.github.lopezjavier.appfitness.feature.activity

import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeHealthActivityRepository
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.DailyActivity
import io.github.lopezjavier.appfitness.domain.model.HealthActivitySummary
import io.github.lopezjavier.appfitness.domain.model.RestingHeartRate
import io.github.lopezjavier.appfitness.domain.model.SleepSession
import io.github.lopezjavier.appfitness.domain.port.Clock
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
import kotlin.test.assertNull
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate(2026, 10, 22)
    private val clock = object : Clock {
        override fun today(): LocalDate = today

        override fun now(): Instant = Instant.parse("2026-10-22T10:00:00Z")
    }
    private val repository = FakeHealthActivityRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        ActivityViewModel(repository, clock, TimeZone.of("Europe/Madrid"), FakeConnectivityObserver())

    private fun night(endedAt: String, minutes: Int) = SleepSession(
        externalId = "s",
        startedAt = Instant.parse(endedAt) - kotlin.time.Duration.parse("PT8H"),
        endedAt = Instant.parse(endedAt),
        minutes = minutes,
        stages = emptyList(),
    )

    @Test
    fun loading_isShownBeforeTheRepositoryAnswers() = runTest(dispatcher) {
        val model = viewModel()

        assertEquals(ActivityUiState.Loading, model.uiState.value)
    }

    @Test
    fun nothingStored_showsEmpty() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        assertEquals(ActivityUiState.Empty, model.uiState.value)
    }

    @Test
    fun sleep_isDatedByTheLocalDayItEnded_andKeepsItsMinutes() = runTest(dispatcher) {
        // 22:30 UTC on the 21st is 00:30 on the 22nd in Madrid (UTC+2 in October).
        repository.summary = HealthActivitySummary(night("2026-10-21T22:30:00Z", 435), emptyList(), null)
        val model = viewModel()
        advanceUntilIdle()

        val content = assertIs<ActivityUiState.Content>(model.uiState.value)
        assertEquals(DayValue(435, LocalDate(2026, 10, 22)), content.sleep)
        assertEquals(today, content.today)
    }

    @Test
    fun eachDailyMetric_usesTheMostRecentDayWithAValue() = runTest(dispatcher) {
        repository.summary = HealthActivitySummary(
            null,
            listOf(
                DailyActivity(LocalDate(2026, 10, 22), null, null, 2100.4),
                DailyActivity(LocalDate(2026, 10, 21), 8500, 620.4, 2300.6),
                DailyActivity(LocalDate(2026, 10, 20), 9100, 700.0, 2400.0),
            ),
            null,
        )
        val model = viewModel()
        advanceUntilIdle()

        val content = assertIs<ActivityUiState.Content>(model.uiState.value)
        assertEquals(DayValue(8500, LocalDate(2026, 10, 21)), content.steps)
        assertEquals(DayValue(620, LocalDate(2026, 10, 21)), content.activeKcal)
        assertEquals(DayValue(2100, LocalDate(2026, 10, 22)), content.totalKcal)
    }

    @Test
    fun typeTheWatchDoesNotProvide_hasNoCardAndNoError() = runTest(dispatcher) {
        repository.summary = HealthActivitySummary(
            null,
            listOf(DailyActivity(LocalDate(2026, 10, 21), 8500, null, null)),
            null,
        )
        val model = viewModel()
        advanceUntilIdle()

        val content = assertIs<ActivityUiState.Content>(model.uiState.value)
        assertNull(content.sleep)
        assertNull(content.activeKcal)
        assertNull(content.totalKcal)
        assertNull(content.restingHeartRate)
    }

    @Test
    fun restingHeartRate_keepsBpmAndDay() = runTest(dispatcher) {
        repository.summary =
            HealthActivitySummary(null, emptyList(), RestingHeartRate(LocalDate(2026, 10, 22), 52, null))
        val model = viewModel()
        advanceUntilIdle()

        val content = assertIs<ActivityUiState.Content>(model.uiState.value)
        assertEquals(DayValue(52, LocalDate(2026, 10, 22)), content.restingHeartRate)
    }

    @Test
    fun failedLoad_showsError_andRetryLoadsAgain() = runTest(dispatcher) {
        repository.failure = AppException(AppError.Unknown)
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(ActivityUiState.Error, model.uiState.value)

        repository.failure = null
        repository.summary = HealthActivitySummary(null, emptyList(), RestingHeartRate(today, 50, null))
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
}
