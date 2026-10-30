package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeNutritionGoalsRepository
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.BodyMetric
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.WeightChartPreferences
import io.github.lopezjavier.appfitness.domain.usecase.MeasurementsByDay
import io.github.lopezjavier.appfitness.domain.usecase.SaveBodyMeasurement
import io.github.lopezjavier.appfitness.domain.usecase.ValidateBodyMeasurement
import io.github.lopezjavier.appfitness.domain.usecase.WeightChartRange
import io.github.lopezjavier.appfitness.domain.usecase.WeightTrend
import io.github.lopezjavier.appfitness.feature.trainer.FakeCoachPlanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

private val Now = LocalDate(2026, 10, 21)

private object DeleteClock : Clock {
    override fun today(): LocalDate = Now

    override fun now(): Instant = Instant.parse("2026-10-21T10:30:00Z")
}

private class Prefs : WeightChartPreferences {
    private val ranges = MutableStateFlow(WeightChartRange.Quarter)
    override val range: Flow<WeightChartRange> = ranges
    private val metrics = MutableStateFlow(BodyMetric.Weight)
    override val metric: Flow<BodyMetric> = metrics

    override suspend fun setRange(range: WeightChartRange) {
        ranges.value = range
    }

    override suspend fun setMetric(metric: BodyMetric) {
        metrics.value = metric
    }
}

private fun manual(day: LocalDate, kg: Double) =
    BodyMeasurement("m-$day", Instant.parse("${day}T08:00:00Z"), kg, MeasurementSource.Manual)

/** T-086: deleting an old manual weight (calendar, day sheet, Eliminar) keeps the screen alive and consistent. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class WeightDeleteOldTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var repository: FakeWeightRepository

    @Composable
    private fun Host(weight: WeightViewModel, trend: WeightTrendViewModel) {
        val state by weight.uiState.collectAsState()
        val trendState by trend.uiState.collectAsState()
        val measurements = (state as? WeightUiState.Content)?.content?.let { it.current to it.days }
        // Same wiring as WeightRoot: a change of the measurements refreshes the trend.
        LaunchedEffect(measurements) {
            if (measurements != null) trend.onEvent(WeightTrendEvent.Refresh)
        }
        AppTheme {
            // The calendar lives on the history route, the chart on the Peso screen: both read the same data here.
            Column {
                WeightHistoryScreen(state, weight::onEvent, {}, Modifier.weight(1f), TimeZone.UTC)
                WeightChart(trendState, trend::onEvent)
            }
        }
    }

    private fun show(rows: List<BodyMeasurement>) {
        repository = FakeWeightRepository().also { it.rows += rows }
        val weight = WeightViewModel(
            repository = repository,
            measurementsByDay = MeasurementsByDay(),
            saveBodyMeasurement = SaveBodyMeasurement(repository, DeleteClock, TimeZone.UTC),
            validate = ValidateBodyMeasurement(),
            connectivity = FakeConnectivityObserver(),
            clock = DeleteClock,
            timeZone = TimeZone.UTC,
        )
        val trend = WeightTrendViewModel(
            measurements = repository,
            circumferences = FakeCircumferenceRepository(),
            goals = FakeNutritionGoalsRepository(null),
            plans = FakeCoachPlanRepository(),
            preferences = Prefs(),
            weightTrend = WeightTrend(),
            clock = DeleteClock,
            timeZone = TimeZone.UTC,
        )
        rule.setContent { Host(weight, trend) }
        rule.waitForIdle()
    }

    private fun deleteDayInPreviousMonth(day: LocalDate) {
        rule.onNodeWithTag("month_calendar_prev").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("month_calendar_day_$day").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("weight_sheet_delete").performClick()
        rule.waitForIdle()
    }

    private fun recentDays() = (1..20).map { manual(LocalDate(2026, 10, it), 75.0 - it * 0.1) }

    @Test
    fun deletingTheOldestChartPoint_keepsTheChart() {
        val old = LocalDate(2026, 9, 15)
        show(listOf(manual(old, 76.0)) + recentDays())

        deleteDayInPreviousMonth(old)

        assertEquals(listOf("m-$old"), repository.deletes)
        rule.onNodeWithTag("weight_chart").assertIsDisplayed()
    }

    @Test
    fun deletingTheOnlyOlderPoint_leavingTooFewPoints_showsTheEmptyHint() {
        val old = LocalDate(2026, 9, 15)
        show(listOf(manual(old, 76.0), manual(Now, 74.0)))

        deleteDayInPreviousMonth(old)

        assertEquals(listOf("m-$old"), repository.deletes)
        rule.onNodeWithTag("weight_chart_empty").assertIsDisplayed()
    }
}
