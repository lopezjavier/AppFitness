package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.BodyMetric
import io.github.lopezjavier.appfitness.domain.usecase.RateTone
import io.github.lopezjavier.appfitness.domain.usecase.TrendData
import io.github.lopezjavier.appfitness.domain.usecase.WeeklyRate
import io.github.lopezjavier.appfitness.domain.usecase.WeightChartRange
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The open metric picker and the open "Media 7 días" help of the chart card survive recreation (ADR-40). The chart
 * itself keeps a scroll state Robolectric cannot parcel, so the state has too little data and shows the hint instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class WeightTrendRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val state = WeightTrendUiState.Content(
        range = WeightChartRange.Quarter,
        metric = BodyMetric.Weight,
        trend = TrendData(emptyList(), WeeklyRate(null, RateTone.Neutral), hasEnoughData = false),
        weeklyRate = WeeklyRate(null, RateTone.Neutral),
        selectedPoint = null,
    )

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    @Test
    fun metricPicker_staysOpenWithTheCurrentMetricChecked_afterRecreation() {
        val tester = StateRestorationTester(rule)
        tester.setContent { AppTheme { WeightChart(state, {}) } }
        rule.waitForIdle()
        tap("weight_chart_metric_title")

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("weight_chart_metric_picker_sheet_title").assertIsDisplayed()
        rule.onNodeWithTag("weight_chart_metric_weight").assertIsSelected()
    }

    @Test
    fun maHelpSheet_staysOpen_afterRecreation() {
        val tester = StateRestorationTester(rule)
        tester.setContent { AppTheme { WeightChart(state, {}) } }
        rule.waitForIdle()
        tap("weight_chart_ma_help")

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("weight_chart_ma_help_sheet_body").assertIsDisplayed()
    }

    @Test
    fun rangeSelection_comesFromTheStateAfterRecreation() {
        val tester = StateRestorationTester(rule)
        tester.setContent { AppTheme { WeightChart(state.copy(range = WeightChartRange.Year), {}) } }
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("weight_chart_range_year").assertIsSelected()
    }
}
