package io.github.lopezjavier.appfitness.feature.weight

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeClock
import io.github.lopezjavier.appfitness.app.FakeNutritionGoalsRepository
import io.github.lopezjavier.appfitness.app.FakeProfileRepository
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.BodyCircumference
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.BodyMetric
import io.github.lopezjavier.appfitness.domain.model.CircumferenceSite
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.model.ProfileSummary
import io.github.lopezjavier.appfitness.domain.repository.WeightChartPreferences
import io.github.lopezjavier.appfitness.domain.usecase.RateTone
import io.github.lopezjavier.appfitness.domain.usecase.WeightChartRange
import io.github.lopezjavier.appfitness.domain.usecase.WeightTrend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

private class FakeWeightChartPreferences(initial: WeightChartRange = WeightChartRange.Default) :
    WeightChartPreferences {
    val state = MutableStateFlow(initial)
    override val range: Flow<WeightChartRange> = state

    override suspend fun setRange(range: WeightChartRange) {
        state.value = range
    }

    val metricState = MutableStateFlow(BodyMetric.Weight)
    override val metric: Flow<BodyMetric> = metricState

    override suspend fun setMetric(metric: BodyMetric) {
        metricState.value = metric
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class WeightTrendViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate(2026, 10, 21)
    private val clock = FakeClock(today, Instant.parse("2026-10-21T10:00:00Z"))
    private val prefs = FakeWeightChartPreferences()
    private val goals = FakeNutritionGoalsRepository(
        NutritionGoals(GoalStrategy.Deficit, calorieAdjustment = -500, targetKcal = 2000),
    )

    private val profile = FakeProfileRepository(Result.success(ProfileSummary("Ana", null, true)))

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun measurement(date: LocalDate, kg: Double) = BodyMeasurement(
        id = "m-$date",
        measuredAt = Instant.parse("${date}T08:00:00Z"),
        weightKg = kg,
        source = MeasurementSource.Manual,
    )

    /** Daily weights, one per day, [perDay] kg lighter each day for 20 days up to [lastDay]. */
    private fun series(start: Double, perDay: Double, lastDay: LocalDate = today) = (0..19).map { back ->
        measurement(LocalDate.fromEpochDays(lastDay.toEpochDays() - back), start + perDay * (19 - back))
    }

    private val circumferences = FakeCircumferenceRepository()

    private fun fatMeasurement(date: LocalDate, kg: Double, fat: Double) = measurement(date, kg).copy(bodyFatPct = fat)

    private fun circumference(date: LocalDate, cm: Double) = BodyCircumference(
        id = "c-$date",
        site = CircumferenceSite.Waist,
        valueCm = cm,
        measuredAt = Instant.parse("${date}T09:00:00Z"),
        createdAt = Instant.parse("${date}T09:00:00Z"),
    )

    private fun viewModel(repository: FakeWeightRepository, savedState: SavedStateHandle = SavedStateHandle()) =
        WeightTrendViewModel(
            measurements = repository,
            circumferences = circumferences,
            goals = goals,
            profile = profile,
            preferences = prefs,
            weightTrend = WeightTrend(),
            clock = clock,
            timeZone = TimeZone.UTC,
            savedState = savedState,
        )

    private fun WeightTrendViewModel.content(): WeightTrendUiState.Content {
        val state = uiState.value
        assertIs<WeightTrendUiState.Content>(state)
        return state
    }

    @Test
    fun targetKg_comesFromTheProfile() = runTest(dispatcher) {
        profile.targetWeight = Result.success(72.5)
        val vm = viewModel(FakeWeightRepository(*series(80.0, 0.0).toTypedArray()))
        advanceUntilIdle()

        assertEquals(72.5, vm.content().targetKg)
    }

    @Test
    fun targetKg_isNullWithoutATargetOrWhenTheReadFails() = runTest(dispatcher) {
        profile.targetWeight = Result.success(null)
        val vm = viewModel(FakeWeightRepository(*series(80.0, 0.0).toTypedArray()))
        advanceUntilIdle()
        assertNull(vm.content().targetKg)

        profile.targetWeight = Result.failure(AppException(AppError.Network))
        vm.onEvent(WeightTrendEvent.Refresh)
        advanceUntilIdle()
        assertNull(vm.content().targetKg)
        assertEquals(20, vm.content().trend.points.size)
    }

    @Test
    fun refresh_picksUpAChangedTarget() = runTest(dispatcher) {
        profile.targetWeight = Result.success(72.5)
        val vm = viewModel(FakeWeightRepository(*series(80.0, 0.0).toTypedArray()))
        advanceUntilIdle()

        profile.targetWeight = Result.success(70.0)
        vm.onEvent(WeightTrendEvent.Refresh)
        advanceUntilIdle()

        assertEquals(70.0, vm.content().targetKg)
    }

    @Test
    fun toggleMaHelp_opensAndClosesAndSurvivesRecreation() = runTest(dispatcher) {
        val repository = FakeWeightRepository(*series(80.0, 0.0).toTypedArray())
        val state = SavedStateHandle()
        val vm = viewModel(repository, state)
        advanceUntilIdle()
        assertFalse(vm.content().maHelpOpen)

        vm.onEvent(WeightTrendEvent.ToggleMaHelp)
        advanceUntilIdle()
        assertTrue(vm.content().maHelpOpen)
        assertEquals(true, state.get<Boolean>("weight_trend_ma_help_open"))

        val recreated = viewModel(repository, state)
        advanceUntilIdle()
        assertTrue(recreated.content().maHelpOpen)

        recreated.onEvent(WeightTrendEvent.ToggleMaHelp)
        advanceUntilIdle()
        assertFalse(recreated.content().maHelpOpen)
    }

    @Test
    fun startsLoading_thenShowsTheTrendOfTheSavedRange() = runTest(dispatcher) {
        val vm = viewModel(FakeWeightRepository(*series(80.0, 0.0).toTypedArray()))
        assertEquals(WeightTrendUiState.Loading, vm.uiState.value)

        advanceUntilIdle()

        val content = vm.content()
        assertEquals(WeightChartRange.Quarter, content.range)
        assertEquals(20, content.trend.points.size)
        assertTrue(content.trend.hasEnoughData)
    }

    @Test
    fun deficitLosingWeight_paceIsFavorable() = runTest(dispatcher) {
        val vm = viewModel(FakeWeightRepository(*series(80.0, -0.1).toTypedArray()))
        advanceUntilIdle()

        val rate = vm.content().trend.rate
        assertEquals(-0.7, rate.value)
        assertEquals(RateTone.Favorable, rate.tone)
    }

    @Test
    fun deficitGainingWeight_paceIsUnfavorable() = runTest(dispatcher) {
        val vm = viewModel(FakeWeightRepository(*series(80.0, 0.05).toTypedArray()))
        advanceUntilIdle()

        assertEquals(RateTone.Unfavorable, vm.content().trend.rate.tone)
    }

    @Test
    fun withoutGoals_maintenanceStrategyApplies() = runTest(dispatcher) {
        goals.row = null
        val vm = viewModel(FakeWeightRepository(*series(80.0, 0.05).toTypedArray()))
        advanceUntilIdle()

        assertEquals(RateTone.Caution, vm.content().trend.rate.tone) // +0.35 kg/week is beyond the 0.20 band
    }

    @Test
    fun selectingARange_savesItAndReloadsOnlyThatWindow() = runTest(dispatcher) {
        val old = measurement(LocalDate(2026, 8, 1), 90.0)
        val vm = viewModel(FakeWeightRepository(old, *series(80.0, 0.0).toTypedArray()))
        advanceUntilIdle()
        assertTrue(vm.content().trend.points.any { it.date == LocalDate(2026, 8, 1) })

        vm.onEvent(WeightTrendEvent.SelectRange(WeightChartRange.Month))
        advanceUntilIdle()

        assertEquals(WeightChartRange.Month, prefs.state.value)
        val content = vm.content()
        assertEquals(WeightChartRange.Month, content.range)
        assertFalse(content.trend.points.any { it.date == LocalDate(2026, 8, 1) })
    }

    @Test
    fun savedRange_isRestoredOnANewViewModel() = runTest(dispatcher) {
        prefs.state.value = WeightChartRange.Year
        val vm = viewModel(FakeWeightRepository())
        advanceUntilIdle()

        assertEquals(WeightChartRange.Year, vm.content().range)
    }

    @Test
    fun singleMeasurement_isNotEnoughData() = runTest(dispatcher) {
        val vm = viewModel(FakeWeightRepository(measurement(today, 74.2)))
        advanceUntilIdle()

        val trend = vm.content().trend
        assertFalse(trend.hasEnoughData)
        assertNull(trend.rate.value)
        assertEquals(RateTone.Neutral, trend.rate.tone)
    }

    @Test
    fun selectingAPoint_showsItsDetail_andNullClearsIt() = runTest(dispatcher) {
        val vm = viewModel(FakeWeightRepository(*series(80.0, 0.0).toTypedArray()))
        advanceUntilIdle()
        val day = LocalDate(2026, 10, 15)

        vm.onEvent(WeightTrendEvent.SelectPoint(day))
        advanceUntilIdle()
        assertEquals(day, vm.content().selectedPoint?.date)
        assertEquals(80.0, vm.content().selectedPoint?.dailyKg)

        vm.onEvent(WeightTrendEvent.SelectPoint(null))
        advanceUntilIdle()
        assertNull(vm.content().selectedPoint)
    }

    @Test
    fun selectedPoint_isRestoredFromSavedState() = runTest(dispatcher) {
        val repository = FakeWeightRepository(*series(80.0, 0.0).toTypedArray())
        val savedState = SavedStateHandle()
        val first = viewModel(repository, savedState)
        advanceUntilIdle()
        first.onEvent(WeightTrendEvent.SelectPoint(LocalDate(2026, 10, 15)))

        val recreated = viewModel(repository, savedState)
        advanceUntilIdle()

        assertEquals(LocalDate(2026, 10, 15), recreated.content().selectedPoint?.date)
    }

    @Test
    fun selectedDayOutsideTheRange_showsNoDetail() = runTest(dispatcher) {
        val vm = viewModel(FakeWeightRepository(*series(80.0, 0.0).toTypedArray()))
        advanceUntilIdle()

        vm.onEvent(WeightTrendEvent.SelectPoint(LocalDate(2020, 1, 1)))
        advanceUntilIdle()

        assertNull(vm.content().selectedPoint)
    }

    @Test
    fun refresh_afterAPastDayWrite_updatesChartAndPill() = runTest(dispatcher) {
        val repository = FakeWeightRepository(measurement(today, 74.0))
        val vm = viewModel(repository)
        advanceUntilIdle()
        assertFalse(vm.content().trend.hasEnoughData)

        repository.rows += measurement(LocalDate(2026, 10, 20), 74.0)
        vm.onEvent(WeightTrendEvent.Refresh)
        advanceUntilIdle()

        val trend = vm.content().trend
        assertEquals(2, trend.points.size)
        assertTrue(trend.hasEnoughData)
    }

    @Test
    fun loadFailure_showsError_andRetryRecovers() = runTest(dispatcher) {
        val repository = FakeWeightRepository(*series(80.0, 0.0).toTypedArray())
        repository.failReads = true
        val vm = viewModel(repository)
        advanceUntilIdle()
        assertEquals(WeightTrendUiState.Error, vm.uiState.value)

        repository.failReads = false
        vm.onEvent(WeightTrendEvent.Retry)
        advanceUntilIdle()

        assertTrue(vm.uiState.value is WeightTrendUiState.Content)
    }

    @Test
    fun defaultMetric_isWeightWithTarget() = runTest(dispatcher) {
        profile.targetWeight = Result.success(72.5)
        val vm = viewModel(FakeWeightRepository(*series(80.0, 0.0).toTypedArray()))
        advanceUntilIdle()

        assertEquals(BodyMetric.Weight, vm.content().metric)
        assertEquals(72.5, vm.content().targetKg)
    }

    @Test
    fun selectingBodyFat_savesItAndShowsItsSeriesWithoutTarget() = runTest(dispatcher) {
        profile.targetWeight = Result.success(72.5)
        val rows = (0..9).map { fatMeasurement(LocalDate.fromEpochDays(today.toEpochDays() - it), 80.0, 20.0 + it) }
        val vm = viewModel(FakeWeightRepository(*rows.toTypedArray()))
        advanceUntilIdle()

        vm.onEvent(WeightTrendEvent.SelectMetric(BodyMetric.BodyFat))
        advanceUntilIdle()

        assertEquals(BodyMetric.BodyFat, prefs.metricState.value)
        val content = vm.content()
        assertEquals(BodyMetric.BodyFat, content.metric)
        assertEquals(29.0, content.trend.points.first().dailyKg)
        assertEquals(20.0, content.trend.points.last().dailyKg)
        assertNull(content.targetKg)
    }

    @Test
    fun weeklyRate_staysWeightBasedWhenAnotherMetricIsSelected() = runTest(dispatcher) {
        val rows = series(80.0, -0.1).map { it.copy(bodyFatPct = 20.0) }
        val vm = viewModel(FakeWeightRepository(*rows.toTypedArray()))
        advanceUntilIdle()
        val weightRate = vm.content().weeklyRate

        vm.onEvent(WeightTrendEvent.SelectMetric(BodyMetric.BodyFat))
        advanceUntilIdle()

        assertEquals(weightRate, vm.content().weeklyRate)
        assertEquals(RateTone.Favorable, vm.content().weeklyRate.tone)
    }

    @Test
    fun selectingAMetric_clearsTheSelectedPoint() = runTest(dispatcher) {
        val state = SavedStateHandle()
        val vm = viewModel(FakeWeightRepository(*series(80.0, 0.0).toTypedArray()), state)
        advanceUntilIdle()
        vm.onEvent(WeightTrendEvent.SelectPoint(LocalDate(2026, 10, 15)))
        advanceUntilIdle()
        assertEquals(LocalDate(2026, 10, 15), vm.content().selectedPoint?.date)

        vm.onEvent(WeightTrendEvent.SelectMetric(BodyMetric.Weight))
        vm.onEvent(WeightTrendEvent.SelectMetric(BodyMetric.BodyFat))
        advanceUntilIdle()

        assertNull(vm.content().selectedPoint)
        assertEquals("", state.get<String>("weight_trend_selected"))
    }

    @Test
    fun circumferences_areLoadedOnceAndWaistIsDerivedFromMemory() = runTest(dispatcher) {
        circumferences.rows += circumference(today, 90.0)
        circumferences.rows += circumference(LocalDate(2026, 10, 18), 92.0)
        val vm = viewModel(FakeWeightRepository(*series(80.0, 0.0).toTypedArray()))
        advanceUntilIdle()
        assertEquals(1, circumferences.getAllCalls)

        vm.onEvent(WeightTrendEvent.SelectMetric(BodyMetric.Waist))
        advanceUntilIdle()

        assertEquals(1, circumferences.getAllCalls)
        assertEquals(listOf(92.0, 90.0), vm.content().trend.points.map { it.dailyKg })
        assertTrue(vm.content().trend.hasEnoughData)
    }

    @Test
    fun initialLoad_makesExactlyOneCallToEachSource() = runTest(dispatcher) {
        val repository = FakeWeightRepository(*series(80.0, 0.0).toTypedArray())
        viewModel(repository)
        advanceUntilIdle()

        assertEquals(1, repository.getMeasurementsCalls)
        assertEquals(1, circumferences.getAllCalls)
    }

    @Test
    fun switchingMetricAndRange_makesNoCallAndNeverEmitsLoading() = runTest(dispatcher) {
        val rows = series(80.0, -0.1).map { it.copy(bodyFatPct = 20.0) }
        circumferences.rows += circumference(today, 90.0)
        val repository = FakeWeightRepository(*rows.toTypedArray())
        val vm = viewModel(repository)
        advanceUntilIdle()
        val states = mutableListOf<WeightTrendUiState>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.toList(states) }

        vm.onEvent(WeightTrendEvent.SelectMetric(BodyMetric.BodyFat))
        vm.onEvent(WeightTrendEvent.SelectMetric(BodyMetric.Waist))
        vm.onEvent(WeightTrendEvent.SelectRange(WeightChartRange.Week))
        vm.onEvent(WeightTrendEvent.SelectRange(WeightChartRange.Year))
        advanceUntilIdle()
        collector.cancel()

        assertEquals(1, repository.getMeasurementsCalls)
        assertEquals(1, circumferences.getAllCalls)
        assertTrue(states.none { it is WeightTrendUiState.Loading })
        assertEquals(BodyMetric.Waist, vm.content().metric)
        assertEquals(WeightChartRange.Year, vm.content().range)
    }

    @Test
    fun metricTabs_showTheSameValuesAsAFreshLoadOfThatMetric() = runTest(dispatcher) {
        val rows = series(80.0, -0.1).map { it.copy(bodyFatPct = 20.0) }
        val vm = viewModel(FakeWeightRepository(*rows.toTypedArray()))
        advanceUntilIdle()
        vm.onEvent(WeightTrendEvent.SelectMetric(BodyMetric.BodyFat))
        advanceUntilIdle()
        val switched = vm.content()

        prefs.metricState.value = BodyMetric.BodyFat
        val fresh = viewModel(FakeWeightRepository(*rows.toTypedArray()))
        advanceUntilIdle()

        assertEquals(fresh.content().trend, switched.trend)
        assertEquals(fresh.content().weeklyRate, switched.weeklyRate)
    }

    @Test
    fun refresh_reloadsSilentlyWithoutEmittingLoading() = runTest(dispatcher) {
        val repository = FakeWeightRepository(*series(80.0, 0.0).toTypedArray())
        val vm = viewModel(repository)
        advanceUntilIdle()
        val states = mutableListOf<WeightTrendUiState>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.toList(states) }

        vm.onEvent(WeightTrendEvent.Refresh)
        advanceUntilIdle()
        collector.cancel()

        assertEquals(2, repository.getMeasurementsCalls)
        assertTrue(states.none { it is WeightTrendUiState.Loading })
    }

    @Test
    fun metricWithoutData_isNotEnoughData() = runTest(dispatcher) {
        val vm = viewModel(FakeWeightRepository(*series(80.0, 0.0).toTypedArray()))
        advanceUntilIdle()

        vm.onEvent(WeightTrendEvent.SelectMetric(BodyMetric.Bone))
        advanceUntilIdle()

        val content = vm.content()
        assertTrue(content.trend.points.isEmpty())
        assertFalse(content.trend.hasEnoughData)
    }

    @Test
    fun sparseMetric_withASingleReading_stillHasAPlotButNoAverage() = runTest(dispatcher) {
        circumferences.rows += circumference(today, 90.0)
        prefs.metricState.value = BodyMetric.Waist
        val vm = viewModel(FakeWeightRepository(*series(80.0, 0.0).toTypedArray()))
        advanceUntilIdle()

        val content = vm.content()
        assertEquals(listOf(90.0), content.trend.points.map { it.dailyKg })
        assertNull(content.trend.points.single().ma7Kg)
        assertTrue(content.hasPlot)
    }

    @Test
    fun metricWithoutData_hasNoPlot() = runTest(dispatcher) {
        prefs.metricState.value = BodyMetric.Hip
        val vm = viewModel(FakeWeightRepository(*series(80.0, 0.0).toTypedArray()))
        advanceUntilIdle()

        assertFalse(vm.content().hasPlot)
    }

    @Test
    fun circumferenceFailure_showsError_andRetryRecovers() = runTest(dispatcher) {
        prefs.metricState.value = BodyMetric.Hip
        circumferences.failReads = true
        val vm = viewModel(FakeWeightRepository(*series(80.0, 0.0).toTypedArray()))
        advanceUntilIdle()
        assertEquals(WeightTrendUiState.Error, vm.uiState.value)

        circumferences.failReads = false
        vm.onEvent(WeightTrendEvent.Retry)
        advanceUntilIdle()

        assertEquals(BodyMetric.Hip, vm.content().metric)
    }

    @Test
    fun rangeChange_keepsTheMetric() = runTest(dispatcher) {
        val vm = viewModel(FakeWeightRepository(*series(80.0, 0.0).toTypedArray()))
        advanceUntilIdle()
        vm.onEvent(WeightTrendEvent.SelectMetric(BodyMetric.LeanMass))
        advanceUntilIdle()

        vm.onEvent(WeightTrendEvent.SelectRange(WeightChartRange.Month))
        advanceUntilIdle()

        assertEquals(BodyMetric.LeanMass, vm.content().metric)
        assertEquals(WeightChartRange.Month, vm.content().range)
    }

    @Test
    fun recreatedViewModel_keepsMetricAndSelectedDate() = runTest(dispatcher) {
        val repository = FakeWeightRepository(*series(80.0, 0.0).map { it.copy(waterKg = 40.0) }.toTypedArray())
        val state = SavedStateHandle()
        val first = viewModel(repository, state)
        advanceUntilIdle()
        first.onEvent(WeightTrendEvent.SelectMetric(BodyMetric.Water))
        advanceUntilIdle()
        first.onEvent(WeightTrendEvent.SelectPoint(LocalDate(2026, 10, 15)))
        advanceUntilIdle()

        val recreated = viewModel(repository, state)
        advanceUntilIdle()

        assertEquals(BodyMetric.Water, recreated.content().metric)
        assertEquals(LocalDate(2026, 10, 15), recreated.content().selectedPoint?.date)
    }

    @Test
    fun silentRefreshFailure_keepsTheCurrentTrend() = runTest(dispatcher) {
        val repository = FakeWeightRepository(*series(80.0, 0.0).toTypedArray())
        val vm = viewModel(repository)
        advanceUntilIdle()

        repository.failReads = true
        vm.onEvent(WeightTrendEvent.Refresh)
        advanceUntilIdle()

        assertTrue(vm.uiState.value is WeightTrendUiState.Content)
    }
}
