package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeNutritionGoalsRepository
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.BodyMetric
import io.github.lopezjavier.appfitness.domain.model.DayMeasurements
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.WeightChartPreferences
import io.github.lopezjavier.appfitness.domain.usecase.RateTone
import io.github.lopezjavier.appfitness.domain.usecase.TrendData
import io.github.lopezjavier.appfitness.domain.usecase.TrendPoint
import io.github.lopezjavier.appfitness.domain.usecase.WeeklyRate
import io.github.lopezjavier.appfitness.domain.usecase.WeightChartRange
import io.github.lopezjavier.appfitness.domain.usecase.WeightTrend
import io.github.lopezjavier.appfitness.feature.trainer.FakeCoachPlanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

private val Today = LocalDate(2026, 10, 21)

private object TrendClock : Clock {
    override fun today(): LocalDate = Today

    override fun now(): Instant = Instant.parse("2026-10-21T10:30:00Z")
}

private fun points(count: Int) = List(count) {
    TrendPoint(LocalDate(2026, 10, 1 + it), 75.0 - it * 0.1, if (it == 0) null else 75.0 - it * 0.1)
}

private fun content(
    range: WeightChartRange = WeightChartRange.Quarter,
    points: List<TrendPoint> = points(10),
    rate: WeeklyRate = WeeklyRate(-0.45, RateTone.Favorable),
    selected: TrendPoint? = null,
    targetKg: Double? = null,
    metric: BodyMetric = BodyMetric.Weight,
    weeklyRate: WeeklyRate = rate,
    startKg: Double? = null,
) = WeightTrendUiState.Content(
    range = range,
    metric = metric,
    trend = TrendData(points, rate, hasEnoughData = points.size >= 2),
    weeklyRate = weeklyRate,
    selectedPoint = selected,
    targetKg = targetKg,
    startKg = startKg,
)

/** US-27 on the weight screen: the metrics pill, the two-layer chart, the range filter and the point detail. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class WeightTrendScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<WeightTrendEvent>()

    private fun showChart(state: WeightTrendUiState) {
        rule.setContent { AppTheme { WeightChart(state, { events += it }) } }
        rule.waitForIdle()
    }

    private fun weightContent(): WeightUiState.Content {
        val current = BodyMeasurement("m1", Instant.parse("2026-10-21T08:00:00Z"), 74.2, MeasurementSource.Manual)
        return WeightUiState.Content(
            WeightContent(
                month = YearMonth(2026, 10),
                today = Today,
                canGoNext = false,
                current = current,
                days = mapOf(Today to DayMeasurements(current, emptyList())),
                isOnline = true,
                sheet = null,
            ),
        )
    }

    private fun showScreen(trend: WeightTrendUiState?) {
        rule.setContent {
            AppTheme {
                WeightScreen(
                    weightContent(),
                    {},
                    CircumferencesUiState.Loading,
                    {},
                    trend = trend,
                    onTrendEvent = { events += it },
                    timeZone = TimeZone.UTC,
                )
            }
        }
        rule.waitForIdle()
    }

    private fun assertCurrentWeight(text: String) {
        rule.onNode(
            hasText(text, substring = true) and hasAnyAncestor(hasTestTag("weight_current_card")),
            useUnmergedTree = true,
        ).assertExists()
    }

    private fun openPicker() {
        rule.onNodeWithTag("weight_chart_metric_title").performClick()
        rule.waitForIdle()
    }

    private fun pick(tag: String) {
        openPicker()
        rule.onNodeWithTag("weight_chart_metric_$tag").performClick()
        rule.waitForIdle()
    }

    private fun toneOf(tag: String) =
        rule.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

    @Test
    fun metricsPanel_showsTheWeeklyRateWithTheTrueMinus() {
        showScreen(content())

        rule.onNodeWithTag("weight_trend_pill").assertTextContains("−0,45 kg esta semana")
        assertCurrentWeight("74,2 kg")
    }

    @Test
    fun paceAgainstTheGoal_favourableAndUnfavourableAreAnnouncedAsText() {
        showScreen(content(rate = WeeklyRate(-0.45, RateTone.Favorable)))
        assertEquals("A favor de tu objetivo", toneOf("weight_trend_pill"))
    }

    @Test
    fun paceAgainstTheGoal_unfavourableIsAnnouncedAsText() {
        showScreen(content(rate = WeeklyRate(0.30, RateTone.Unfavorable)))

        rule.onNodeWithTag("weight_trend_pill").assertTextContains("+0,30 kg esta semana")
        assertEquals("En contra de tu objetivo", toneOf("weight_trend_pill"))
    }

    @Test
    fun notEnoughData_panelHidesThePillAndKeepsTheLastWeight() {
        showScreen(content(points = points(1), rate = WeeklyRate(null, RateTone.Neutral)))

        rule.onNodeWithTag("weight_trend_pill").assertDoesNotExist()
        rule.onNodeWithText("Sin datos suficientes").assertDoesNotExist()
        assertCurrentWeight("74,2 kg")
        rule.onNodeWithTag("weight_chart_empty").assertTextEquals("Registra tu peso varios días para ver la tendencia")
    }

    @Test
    fun withoutTrendState_theScreenIsAsBefore() {
        showScreen(null)

        rule.onNodeWithTag("weight_current_card").assertIsDisplayed()
        rule.onAllNodesCount("weight_chart_card", 0)
    }

    @Test
    fun twoLayerChart_showsTheLegendAndFiveRangesWithThreeMonthsSelected() {
        showChart(content())

        rule.onNodeWithTag("weight_chart").assertIsDisplayed()
        rule.onNodeWithTag("weight_chart_legend").assertTextContains("Media 7 días")
        rule.onNodeWithTag("weight_chart_range_threemonths").assertIsSelected()
        val ranges = listOf("week", "month", "threemonths", "sixmonths", "year")
        ranges.forEach { rule.onNodeWithTag("weight_chart_range_$it").assertIsDisplayed() }
        val segments = rule.onAllNodes(
            SemanticsMatcher("range segment") {
                it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("weight_chart_range_") == true
            },
        ).fetchSemanticsNodes()
        assertEquals(ranges.size, segments.size)
        rule.onNodeWithTag("weight_chart_metric_selector").assertDoesNotExist()
    }

    @Test
    fun rangeSelector_speaksTheSpokenRanges() {
        showChart(content())

        val spoken = mapOf(
            "week" to "1 semana",
            "month" to "1 mes",
            "threemonths" to "3 meses",
            "sixmonths" to "6 meses",
            "year" to "1 año",
        )
        spoken.forEach { (tag, text) ->
            rule.onNodeWithTag("weight_chart_range_$tag").assert(
                SemanticsMatcher("speaks $text") { node ->
                    node.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(text) == true
                },
            )
        }
    }

    @Test
    fun timeFilter_tappingOneMonthSendsTheEvent() {
        showChart(content())

        rule.onNodeWithTag("weight_chart_range_month").performClick()

        assertEquals(listOf<WeightTrendEvent>(WeightTrendEvent.SelectRange(WeightChartRange.Month)), events)
    }

    @Test
    fun timeFilter_theSavedRangeIsTheSelectedSegment() {
        showChart(content(range = WeightChartRange.Year))

        rule.onNodeWithTag("weight_chart_range_year").assertIsSelected()
    }

    @Test
    fun notEnoughData_chartCardKeepsTheTitleAndRangesAndShowsTheHint() {
        showChart(content(points = points(1)))

        rule.onNodeWithTag("weight_chart_empty").assertIsDisplayed()
        rule.onNodeWithTag("weight_chart_range_threemonths").assertIsDisplayed()
    }

    @Test
    fun loadingAndError_showSkeletonAndRetry() {
        showChart(WeightTrendUiState.Loading)
        rule.onNodeWithTag("weight_chart_loading").assertIsDisplayed()
    }

    @Test
    fun error_retryButtonSendsRetry() {
        showChart(WeightTrendUiState.Error)

        rule.onNodeWithTag("weight_trend_error_action").performClick()

        assertEquals(listOf<WeightTrendEvent>(WeightTrendEvent.Retry), events)
    }

    @Test
    fun pointDetail_longPressSelectsTheNearestPoint() {
        showChart(content())

        rule.onNodeWithTag("weight_chart").performTouchInput { longClick(centerRight.copy(x = right - 1f)) }

        assertEquals(listOf<WeightTrendEvent>(WeightTrendEvent.SelectPoint(LocalDate(2026, 10, 10))), events)
    }

    @Test
    fun pointDetail_isAlsoReachableThroughTheAccessibilityAction() {
        showChart(content())

        rule.onNodeWithTag("weight_chart").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(listOf<WeightTrendEvent>(WeightTrendEvent.SelectPoint(LocalDate(2026, 10, 10))), events)
    }

    @Test
    fun pointDetail_showsDateWeightAndMa7AndClosesWithTheButton() {
        val point = points(10)[3]
        showChart(content(selected = point))

        rule.onNodeWithTag("weight_chart_point_detail").assertIsDisplayed()
        rule.onNodeWithText("Peso: 74,7 kg").assertIsDisplayed()
        rule.onNodeWithText("Media 7 días: 74,7 kg").assertIsDisplayed()
        rule.onNodeWithTag("weight_chart_point_detail_close").performClick()

        assertEquals(listOf<WeightTrendEvent>(WeightTrendEvent.SelectPoint(null)), events)
    }

    @Test
    fun maHelp_opensAnInfoSheetFromTheHelpButton_andNothingExpandsInPlace() {
        showChart(content())

        rule.onNodeWithTag("weight_chart_ma_help_sheet_body").assertDoesNotExist()
        rule.onNodeWithTag("weight_chart_ma_help").assertHeightIsAtLeast(48.dp).performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("weight_chart_ma_help_sheet_body")
            .assertTextContains("Promedio de tu peso en ese día y los 6 anteriores.", substring = true)
        rule.onNodeWithTag("weight_chart_ma_help_text").assertDoesNotExist()
        rule.onNodeWithTag("weight_chart_ma_help_sheet_close").performClick()
    }

    @Test
    fun maHelp_hasTheSpokenAyudaLabel() {
        showChart(content())

        val cd = rule.onNodeWithTag("weight_chart_ma_help").fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.ContentDescription)
        assertEquals(listOf("Ayuda: Media 7 días"), cd)
    }

    @Test
    fun targetLine_legendAppearsOnlyWithATargetAndTheSummaryMentionsIt() {
        showChart(content(targetKg = 70.0))

        rule.onNodeWithTag("weight_chart_target_legend").assertTextContains("Objetivo")
        assertTrue(toneOf("weight_chart").orEmpty().endsWith("Línea de objetivo en 70,0 kg."))
    }

    @Test
    fun goalOverlay_summaryTellsTheTargetAndTheStartingWeightForTalkBack() {
        showChart(content(targetKg = 70.0, startKg = 78.0))

        assertTrue(toneOf("weight_chart").orEmpty().endsWith("Objetivo 70,0 kg; peso inicial 78,0 kg."))
        rule.onNodeWithTag("weight_chart_target_legend").assertIsDisplayed()
    }

    @Test
    fun targetLine_withoutATargetHasNoLegendEntry() {
        showChart(content(targetKg = null))

        rule.onNodeWithTag("weight_chart_target_legend").assertDoesNotExist()
    }

    @Test
    fun chart_summaryNamesRangeCountEndsAndRate() {
        showChart(content())

        val summary = toneOf("weight_chart")
        assertEquals(
            "Peso, 3 meses: 10 días con datos, de 75,0 kg a 74,1 kg. −0,45 kg esta semana",
            summary,
        )
    }

    private val metricTags = listOf("weight", "body_fat", "lean_mass", "water", "bone", "bmr", "waist", "hip")

    private fun fatPoints() = List(10) {
        TrendPoint(LocalDate(2026, 10, 1 + it), 18.0 + it * 0.1, if (it == 0) null else 18.0 + it * 0.1)
    }

    @Test
    fun metricTitle_isAButtonNamedForScreenReaders_andOpensThePickerWithTheEightMetrics() {
        showChart(content())

        val cd = rule.onNodeWithTag("weight_chart_metric_title").fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.ContentDescription)
        assertEquals(listOf("Métrica del gráfico: Peso. Cambiar"), cd)
        rule.onNodeWithTag("weight_chart_metric_title").assertHeightIsAtLeast(48.dp)
        openPicker()

        rule.onNodeWithTag("weight_chart_metric_picker_sheet_title").assertTextEquals("Métrica del gráfico")
        metricTags.forEach { rule.onNodeWithTag("weight_chart_metric_$it").assertExists() }
        rule.onNodeWithTag("weight_chart_metric_weight").assertIsSelected()
        rule.onNodeWithTag("weight_chart_metric_selector").assertDoesNotExist()
    }

    @Test
    fun metricPicker_usesTheGlossaryNames() {
        showChart(content())
        openPicker()

        listOf("Grasa corporal", "Masa ósea", "Metabolismo basal").forEach {
            rule.onNodeWithText(it).assertExists()
        }
    }

    @Test
    fun metricPicker_tappingBodyFatAppliesAtOnceAndClosesThePicker() {
        showChart(content())

        pick("body_fat")

        assertEquals(listOf<WeightTrendEvent>(WeightTrendEvent.SelectMetric(BodyMetric.BodyFat)), events)
        rule.onNodeWithTag("weight_chart_metric_picker_sheet_title").assertDoesNotExist()
    }

    @Test
    fun metricPicker_closingWithXAppliesNothing() {
        showChart(content())
        openPicker()

        rule.onNodeWithTag("weight_chart_metric_picker_sheet_close").performClick()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodes(hasTestTag("weight_chart_metric_picker_sheet_title")).fetchSemanticsNodes().isEmpty()
        }

        assertTrue(events.isEmpty())
    }

    @Test
    fun metric_bodyFatShowsItsChartAndDetailInPercent() {
        showChart(content(metric = BodyMetric.BodyFat, points = fatPoints(), selected = fatPoints()[3]))

        rule.onNodeWithTag("weight_chart_metric_title").assertTextContains("Grasa corporal")
        rule.onNodeWithTag("weight_chart").assertIsDisplayed()
        rule.onNodeWithText("Grasa corporal: 18,3 %").assertIsDisplayed()
        rule.onNodeWithText("Media 7 días: 18,3 %").assertIsDisplayed()
    }

    @Test
    fun metric_rangesStayUsableWithAnotherMetric() {
        showChart(content(metric = BodyMetric.BodyFat, points = fatPoints()))

        rule.onNodeWithTag("weight_chart_range_month").performClick()

        assertEquals(listOf<WeightTrendEvent>(WeightTrendEvent.SelectRange(WeightChartRange.Month)), events)
    }

    @Test
    fun metric_longPressReadsTheMetricValue() {
        showChart(content(metric = BodyMetric.BodyFat, points = fatPoints()))

        rule.onNodeWithTag("weight_chart").performTouchInput { longClick(centerRight.copy(x = right - 1f)) }

        assertEquals(listOf<WeightTrendEvent>(WeightTrendEvent.SelectPoint(LocalDate(2026, 10, 10))), events)
    }

    @Test
    fun metric_targetLegendIsWeightOnly() {
        showChart(content(metric = BodyMetric.BodyFat, points = fatPoints(), targetKg = null))
        rule.onNodeWithTag("weight_chart_target_legend").assertDoesNotExist()
    }

    @Test
    fun metric_summaryNamesTheMetricAndOmitsTheWeeklyRate() {
        showChart(content(metric = BodyMetric.BodyFat, points = fatPoints()))

        assertEquals(
            "Grasa corporal, 3 meses: 10 días con datos, de 18,0 % a 18,9 %.",
            toneOf("weight_chart"),
        )
    }

    @Test
    fun metric_weeklyRatePillStaysOnWeightWhileAnotherMetricIsCharted() {
        showScreen(
            content(
                metric = BodyMetric.BodyFat,
                points = fatPoints(),
                weeklyRate = WeeklyRate(-0.45, RateTone.Favorable),
            ),
        )

        rule.onNodeWithTag("weight_trend_pill").assertTextContains("−0,45 kg esta semana")
    }

    @Test
    fun metric_bmrReadoutIsAnIntegerInKcal() {
        val bmr = List(5) { TrendPoint(LocalDate(2026, 10, 1 + it), 1650.0 + it, if (it == 0) null else 1650.0 + it) }
        showChart(content(metric = BodyMetric.Bmr, points = bmr, selected = bmr[2]))

        rule.onNodeWithText("Metabolismo basal: 1652 kcal").assertIsDisplayed()
    }

    @Test
    fun metric_metricWithoutDataShowsTheGenericHintAndKeepsTheTitleAndRanges() {
        showChart(content(metric = BodyMetric.Bone, points = emptyList()))

        rule.onNodeWithTag(
            "weight_chart_empty",
        ).assertTextEquals("Masa ósea: sin datos en este periodo. Regístralos para ver la tendencia")
        rule.onNodeWithTag("weight_chart_metric_title").assertIsDisplayed()
        rule.onNodeWithTag("weight_chart_range_threemonths").assertIsDisplayed()
    }

    @Test
    fun metric_sparseMetricWithoutAnAverageStillPlotsItsDots() {
        val sparse = listOf(TrendPoint(LocalDate(2026, 10, 5), 84.0, null))
        showChart(content(metric = BodyMetric.Waist, points = sparse))

        rule.onNodeWithTag("weight_chart").assertIsDisplayed()
        rule.onNodeWithTag("weight_chart_empty").assertDoesNotExist()
    }

    @Test
    fun sparseWeighIns_theChartIsDrawnWithTwoPointsFiveDaysApart() {
        val sparse = listOf(
            TrendPoint(LocalDate(2026, 10, 14), 79.5, 79.5),
            TrendPoint(LocalDate(2026, 10, 19), 79.4, 79.45),
        )
        showChart(content(points = sparse))

        rule.onNodeWithTag("weight_chart").assertIsDisplayed()
        rule.onNodeWithTag("weight_chart_empty").assertDoesNotExist()
    }

    @Test
    fun sparseWeighIns_theChartIsDrawnWithTwoPointsAYearApart() {
        val sparse = listOf(
            TrendPoint(LocalDate(2025, 10, 19), 82.0, 82.0),
            TrendPoint(LocalDate(2026, 10, 19), 79.4, 79.4),
        )
        showChart(content(range = WeightChartRange.Year, points = sparse))

        rule.onNodeWithTag("weight_chart").assertIsDisplayed()
        rule.onNodeWithTag("weight_chart_empty").assertDoesNotExist()
    }

    @Test
    fun sparseWeighIns_aNonWeightMetricAlsoDrawsTheChart() {
        val sparse = listOf(
            TrendPoint(LocalDate(2026, 10, 14), 18.0, 18.0),
            TrendPoint(LocalDate(2026, 10, 19), 17.8, 17.9),
        )
        showChart(content(metric = BodyMetric.BodyFat, points = sparse))

        rule.onNodeWithTag("weight_chart").assertIsDisplayed()
        rule.onNodeWithTag("weight_chart_empty").assertDoesNotExist()
    }

    @Test
    fun sparseWeighIns_thePillShowsThePaceBetweenTheLastTwoWeighIns() {
        val sparse = listOf(
            TrendPoint(LocalDate(2026, 10, 14), 79.5, 79.5),
            TrendPoint(LocalDate(2026, 10, 19), 79.4, 79.45),
        )
        showScreen(content(points = sparse, rate = WeeklyRate(-0.14, RateTone.Favorable)))

        rule.onNodeWithTag("weight_trend_pill").assertTextContains("−0,14 kg esta semana")
    }

    @Test
    fun singleWeighIn_showsTheHintInsteadOfTheChart() {
        showChart(content(points = points(1), rate = WeeklyRate(null, RateTone.Neutral)))

        rule.onNodeWithTag("weight_chart_empty").assertIsDisplayed()
        rule.onNodeWithTag("weight_chart").assertDoesNotExist()
    }

    @Test
    fun metricPicker_marksTheCurrentMetricWhenHipIsSelected() {
        showChart(content(metric = BodyMetric.Hip, points = fatPoints()))
        openPicker()

        rule.onNodeWithTag("weight_chart_metric_hip").assertIsDisplayed().assertIsSelected()
    }

    /** The device preferences: they outlive a recreated ViewModel, like the DataStore does. */
    private class MemoryPreferences : WeightChartPreferences {
        val rangeState = MutableStateFlow(WeightChartRange.Quarter)
        val metricState = MutableStateFlow(BodyMetric.Weight)
        override val range: Flow<WeightChartRange> = rangeState

        override suspend fun setRange(range: WeightChartRange) {
            rangeState.value = range
        }

        override val metric: Flow<BodyMetric> = metricState

        override suspend fun setMetric(metric: BodyMetric) {
            metricState.value = metric
        }
    }

    private fun trendViewModel(
        savedState: SavedStateHandle,
        preferences: WeightChartPreferences = MemoryPreferences(),
    ): WeightTrendViewModel {
        val rows =
            List(10) {
                BodyMeasurement(
                    "m$it",
                    Instant.parse("2026-10-${12 + it}T08:00:00Z"),
                    75.0 - it * 0.1,
                    MeasurementSource.Manual,
                    bodyFatPct = 20.0 + it,
                )
            }
        return WeightTrendViewModel(
            measurements = FakeWeightRepository().apply { this.rows += rows },
            circumferences = FakeCircumferenceRepository(),
            goals = FakeNutritionGoalsRepository(),
            plans = FakeCoachPlanRepository(),
            preferences = preferences,
            weightTrend = WeightTrend(),
            clock = TrendClock,
            timeZone = TimeZone.UTC,
            savedState = savedState,
        )
    }

    @Composable
    private fun TrendHost(model: WeightTrendViewModel) {
        val state by model.uiState.collectAsState()
        AppTheme { WeightChart(state, model::onEvent) }
    }

    // The Vico chart keeps its scroll state in a rememberSaveable that Robolectric cannot parcel, so
    // StateRestorationTester cannot host it; the host is recreated instead and the selection comes back from the
    // same SavedStateHandle.
    @Test
    fun selectedPoint_isShownAgainWhenTheHostAndViewModelAreRecreated() {
        val savedState = SavedStateHandle()
        var generation by mutableIntStateOf(0)
        rule.setContent {
            key(generation) { TrendHost(remember { trendViewModel(savedState) }) }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("weight_chart").performTouchInput { longClick(centerRight.copy(x = right - 1f)) }
        rule.waitForIdle()
        rule.onNodeWithTag("weight_chart_point_detail").assertIsDisplayed()

        generation++
        rule.waitForIdle()

        rule.onNodeWithTag("weight_chart_point_detail").assertIsDisplayed()
    }

    @Test
    fun metricRangeAndPoint_comeBackWhenTheHostAndViewModelAreRecreated() {
        val savedState = SavedStateHandle()
        val preferences = MemoryPreferences()
        var generation by mutableIntStateOf(0)
        rule.setContent {
            key(generation) { TrendHost(remember { trendViewModel(savedState, preferences) }) }
        }
        rule.waitForIdle()
        pick("body_fat")
        rule.onNodeWithTag("weight_chart_range_month").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("weight_chart").performTouchInput { longClick(centerRight.copy(x = right - 1f)) }
        rule.waitForIdle()
        rule.onNodeWithTag("weight_chart_point_detail").assertIsDisplayed()

        generation++
        rule.waitForIdle()

        rule.onNodeWithTag("weight_chart_metric_title").assertTextContains("Grasa corporal")
        rule.onNodeWithTag("weight_chart_range_month").assertIsSelected()
        rule.onNodeWithTag("weight_chart_point_detail").assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesCount(tag: String, expected: Int) {
        assertEquals(expected, onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size)
    }
}
