package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.BodyCircumference
import io.github.lopezjavier.appfitness.domain.model.CircumferenceSite
import io.github.lopezjavier.appfitness.domain.model.Sex
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.usecase.MeasurementsByDay
import io.github.lopezjavier.appfitness.domain.usecase.MetricInterpretation
import io.github.lopezjavier.appfitness.domain.usecase.MetricKind
import io.github.lopezjavier.appfitness.domain.usecase.MetricStatus
import io.github.lopezjavier.appfitness.domain.usecase.SaveBodyMeasurement
import io.github.lopezjavier.appfitness.domain.usecase.SummarizeCircumferences
import io.github.lopezjavier.appfitness.domain.usecase.ValidateBodyMeasurement
import io.github.lopezjavier.appfitness.domain.usecase.ValidateCircumference
import io.github.lopezjavier.appfitness.domain.usecase.WaistHipRatio
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Instant

private object SectionClock : Clock {
    override fun today(): LocalDate = LocalDate(2026, 10, 21)

    override fun now(): Instant = Instant.parse("2026-10-21T10:30:00Z")
}

/** About one pixel at xxhdpi (1 px is about 0.33 dp): the tolerance of "same position" and "same size". */
private const val PX_TOLERANCE_DP = 0.4f

/**
 * US-25 circumferences on the weight screen: logging, ratio, validation and states. US-148 (T-326): a 2-column grid,
 * the waist-hip ratio card first, then one cell per site.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class CircumferenceSectionTest {
    @get:Rule
    val rule = createComposeRule()

    private val connectivity = FakeConnectivityObserver()
    private lateinit var repository: FakeCircumferenceRepository

    private fun row(id: String, site: CircumferenceSite, cm: Double, at: String = "2026-10-18T08:30:00Z") =
        BodyCircumference(id, site, cm, Instant.parse(at), Instant.parse(at))

    private fun weightModel(): WeightViewModel {
        val weights = FakeWeightRepository()
        return WeightViewModel(
            repository = weights,
            measurementsByDay = MeasurementsByDay(),
            saveBodyMeasurement = SaveBodyMeasurement(weights, SectionClock, TimeZone.UTC),
            validate = ValidateBodyMeasurement(),
            connectivity = connectivity,
            clock = SectionClock,
            timeZone = TimeZone.UTC,
            savedState = SavedStateHandle(),
        )
    }

    private fun circumferenceModel(state: SavedStateHandle = SavedStateHandle()) = CircumferencesViewModel(
        repository = repository,
        validate = ValidateCircumference(),
        waistHipRatio = WaistHipRatio(),
        summarize = SummarizeCircumferences(),
        connectivity = connectivity,
        clock = SectionClock,
        timeZone = TimeZone.UTC,
        savedState = state,
    )

    /** The font scale of every host of this class; one `setContent` per test, the scale changes through it. */
    private val fontScale = mutableFloatStateOf(1f)

    @Composable
    private fun Scaled(content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale.floatValue)) {
            AppTheme { content() }
        }
    }

    @Composable
    private fun Host(weight: WeightViewModel, circumferences: CircumferencesViewModel) {
        val weightState by weight.uiState.collectAsState()
        val circumferenceState by circumferences.uiState.collectAsState()
        Scaled {
            WeightScreen(
                state = weightState,
                onEvent = weight::onEvent,
                circumferences = circumferenceState,
                onCircumferenceEvent = circumferences::onEvent,
                timeZone = TimeZone.UTC,
            )
        }
    }

    private fun setScale(scale: Float) {
        fontScale.floatValue = scale
        rule.waitForIdle()
    }

    private fun show(vararg rows: BodyCircumference) {
        repository = FakeCircumferenceRepository(*rows)
        val weight = weightModel()
        shownModel = circumferenceModel()
        rule.setContent { Host(weight, shownModel) }
        rule.waitForIdle()
    }

    private lateinit var shownModel: CircumferencesViewModel

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun assertCardShows(cardTag: String, text: String) {
        rule.onNode(
            hasText(text, substring = true, ignoreCase = true) and hasAnyAncestor(hasTestTag(cardTag)),
            useUnmergedTree = true,
        ).assertExists()
    }

    private fun type(tag: String, text: String) {
        rule.onNodeWithTag(tag).performTextReplacement(text)
        rule.waitForIdle()
    }

    private fun logMeasurement(value: String) {
        tap("circumference_add_button")
        type("circumference_sheet_value_input_waist", value)
        tap("circumference_sheet_commit")
    }

    // -- Logging a circumference ------------------------------------------------------------------------------------

    @Test
    fun section_hasAHeadingAndAnAddItemButton() {
        show()

        rule.onNodeWithTag("circumference_title").assertTextEquals("Circunferencias")
        rule.onNodeWithTag("circumference_add_button").assertTextEquals("Añadir circunferencia")
    }

    @Test
    fun addButton_isTheLastElementOfTheList() {
        show(row("w", CircumferenceSite.Waist, 84.5), row("h", CircumferenceSite.Hip, 98.0))

        val add = rule.onNodeWithTag("circumference_add_button").getUnclippedBoundsInRoot()
        listOf("circumference_site_cell_waist", "circumference_site_cell_hip", "weight_whr_card").forEach {
            assertTrue("$it above the add button", rule.onNodeWithTag(it).getUnclippedBoundsInRoot().bottom <= add.top)
        }
    }

    @Test
    fun loggingWaist_showsTheWaistSiteCell() {
        show()

        tap("circumference_add_button")
        rule.onNodeWithTag("circumference_sheet_title").assertTextEquals("Añadir medida")
        type("circumference_sheet_value_input_waist", "84,5")
        tap("circumference_sheet_commit")

        val saved = repository.rows.single()
        assertEquals(CircumferenceSite.Waist, saved.site)
        assertEquals(84.5, saved.valueCm, 0.0)
        assertEquals(Instant.parse("2026-10-21T10:30:00Z"), saved.measuredAt)
        rule.onNodeWithTag("circumference_sheet").assertDoesNotExist()
        value(CircumferenceSite.Waist, cm("84,5")).assertExists()
    }

    @Test
    fun addSheet_hasSevenFields_andNoDropdownNorTime() {
        show()

        tap("circumference_add_button")

        CircumferenceSite.entries.forEach { site ->
            val tag = site.name.lowercase()
            rule.onNodeWithTag("circumference_sheet_value_input_$tag").assertExists()
            rule.onNodeWithTag("circumference_sheet_help_$tag").assertExists()
        }
        rule.onNodeWithTag("circumference_sheet_site_field").assertDoesNotExist()
        rule.onNodeWithTag("circumference_sheet_time_value").assertDoesNotExist()
        rule.onNodeWithTag("circumference_sheet_hour", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun waistAndHipTogether_areSavedAtOnce_andShowTheRatio() {
        show()

        tap("circumference_add_button")
        rule.onNodeWithTag("circumference_sheet_commit").assertIsNotEnabled()
        type("circumference_sheet_value_input_waist", "84,5")
        type("circumference_sheet_value_input_hip", "98,0")
        tap("circumference_sheet_commit")

        assertEquals(setOf(CircumferenceSite.Waist, CircumferenceSite.Hip), repository.rows.map { it.site }.toSet())
        assertEquals(2, repository.rows.size)
        rule.onNodeWithTag("circumference_site_cell_hip").assertExists()
        assertCardShows("weight_whr_card", "0,86")
    }

    @Test
    fun tappingTheHelp_opensAnInfoSheetWithTheInstructionsOfThatSite_andXClosesIt() {
        show()
        tap("circumference_add_button")

        tap("circumference_sheet_help_waist")
        rule.onNodeWithTag(
            "circumference_sheet_help_waist_sheet_body",
        ).assertTextContains("a medio camino", substring = true)
        tap("circumference_sheet_help_waist_sheet_close")

        rule.onNodeWithTag("circumference_sheet_help_waist_sheet_body").assertDoesNotExist()
        // Nothing expands in place and the add sheet is still open.
        rule.onNodeWithTag("circumference_sheet_help_text_waist").assertDoesNotExist()
        rule.onNodeWithTag("circumference_sheet_title").assertExists()
    }

    @Test
    fun theDayStepper_goesBackToYesterday_andStopsAtToday() {
        show()

        tap("circumference_add_button")
        rule.onNodeWithTag("circumference_sheet_date_text").assertTextEquals("Hoy")
        rule.onNodeWithTag("circumference_sheet_date_next").assertIsNotEnabled()
        tap("circumference_sheet_date_prev")
        rule.onNodeWithTag("circumference_sheet_date_text").assertTextEquals("Ayer")
        rule.onNodeWithTag("circumference_sheet_date_next").assertIsEnabled()
        tap("circumference_sheet_date_prev")
        rule.onNodeWithTag("circumference_sheet_date_text").assertTextEquals("lun, 19 oct")
        type("circumference_sheet_value_input_waist", "84,5")
        tap("circumference_sheet_commit")

        assertEquals(Instant.parse("2026-10-19T12:00:00Z"), repository.rows.single().measuredAt)
    }

    // -- Waist-to-hip ratio -----------------------------------------------------------------------------------------

    @Test
    fun waistAndHip_showTheRatio() {
        show(row("w", CircumferenceSite.Waist, 84.5), row("h", CircumferenceSite.Hip, 98.0))

        assertCardShows("weight_whr_card", "0,86")
        assertCardShows("weight_whr_card", "Relación cintura-cadera")
    }

    @Test
    fun onlyWaist_showsNotEnoughData() {
        show(row("w", CircumferenceSite.Waist, 84.5))

        assertCardShows("weight_whr_card", "Sin datos suficientes")
    }

    // -- The grid: ratio card first, then one cell per site (T-326) -------------------------------------------------

    private val openedSites = mutableListOf<CircumferenceSite>()

    private fun showSection(vararg rows: BodyCircumference, ratioGauge: (@Composable () -> Unit)? = null) {
        val state = circumferenceSectionState(rows.toList())
        rule.setContent {
            Scaled {
                CircumferenceSection(
                    state,
                    SectionClock.today(),
                    {},
                    ratioGauge = ratioGauge,
                    onOpenSite = { openedSites += it },
                )
            }
        }
        rule.waitForIdle()
    }

    private fun siteTag(site: CircumferenceSite) = "circumference_site_cell_${site.name.lowercase()}"

    private fun child(site: CircumferenceSite, part: String) =
        rule.onNodeWithTag("${siteTag(site)}_$part", useUnmergedTree = true)

    /** The headline value of [site]'s cell, e.g. "84,5 cm". */
    private fun value(site: CircumferenceSite, text: String) =
        rule.onNode(hasText(text) and hasAnyAncestor(hasTestTag(siteTag(site))), useUnmergedTree = true)

    private fun bounds(tag: String): DpRect = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private fun bounds(site: CircumferenceSite): DpRect = bounds(siteTag(site))

    /** A centimetre value as the app writes it: number and unit joined by U+00A0 (R-93). */
    private fun cm(number: String) = "$number cm"

    private fun assertSame(message: String, expected: Dp, actual: Dp) =
        assertEquals(message, expected.value, actual.value, PX_TOLERANCE_DP)

    private fun inChange(site: CircumferenceSite, text: String) = rule.onNode(
        hasText(text, substring = true) and hasAnyAncestor(hasTestTag("${siteTag(site)}_change")),
        useUnmergedTree = true,
    )

    private fun textLayout(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        rule.onNode(hasText(text), useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!(results)
        return results.single()
    }

    private val perMeasurementRow = SemanticsMatcher("has a per-measurement row tag") { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("circumference_row_") == true
    }

    @Test
    fun grid_ratioIsTheFirstCell_thenSitesInSiteOrder_twoPerRow() {
        showSection(
            row("h1", CircumferenceSite.Hip, 99.0, "2026-10-01T08:30:00Z"),
            row("h2", CircumferenceSite.Hip, 98.0),
            row("w1", CircumferenceSite.Waist, 86.0, "2026-10-01T08:30:00Z"),
            row("w2", CircumferenceSite.Waist, 84.5),
            row("c1", CircumferenceSite.Chest, 101.0),
        )

        val ratio = bounds("weight_whr_card")
        val chest = bounds(CircumferenceSite.Chest)
        val waist = bounds(CircumferenceSite.Waist)
        val hip = bounds(CircumferenceSite.Hip)
        assertSame("row 1 shares its top", ratio.top, chest.top)
        assertTrue("the ratio is the first cell", ratio.right <= chest.left)
        assertTrue("row 2 is under row 1", waist.top >= maxOf(ratio.bottom, chest.bottom))
        assertSame("row 2 shares its top", waist.top, hip.top)
        assertTrue("Cintura before Cadera", waist.right <= hip.left)
        listOf("Pecho", "Cintura", "Cadera").forEach {
            assertEquals(it, 1, rule.onAllNodesWithText(it, useUnmergedTree = true).fetchSemanticsNodes().size)
        }
        value(CircumferenceSite.Waist, cm("84,5")).assertExists()
        assertEquals(0, rule.onAllNodes(perMeasurementRow, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun cellsOfARow_shareWidthAndHeight() {
        showSection(
            row("c1", CircumferenceSite.Chest, 101.0),
            row("w1", CircumferenceSite.Waist, 84.5),
            row("h1", CircumferenceSite.Hip, 100.0, "2026-09-20T08:30:00Z"),
            row("h2", CircumferenceSite.Hip, 98.0),
        )

        val waist = bounds(CircumferenceSite.Waist)
        val hip = bounds(CircumferenceSite.Hip)
        assertSame("same top", waist.top, hip.top)
        assertSame("same width", waist.width, hip.width)
        assertSame("same height", waist.height, hip.height)
        child(CircumferenceSite.Waist, "change").assertTextEquals("dom, 18 oct")
        child(CircumferenceSite.Waist, "sparkline").assertDoesNotExist()
        child(CircumferenceSite.Hip, "sparkline").assertExists()
    }

    @Test
    fun oddCount_leavesTheLastSlotEmpty() {
        showSection(row("w", CircumferenceSite.Waist, 84.5), row("h", CircumferenceSite.Hip, 98.0))

        val ratio = bounds("weight_whr_card")
        val waist = bounds(CircumferenceSite.Waist)
        val hip = bounds(CircumferenceSite.Hip)
        assertSame("Cintura shares the first row", ratio.top, waist.top)
        assertTrue("Cadera is alone on row 2", hip.top >= maxOf(ratio.bottom, waist.bottom))
        assertSame("half width", ratio.width, hip.width)
        assertSame("start-aligned", ratio.left, hip.left)
    }

    @Test
    fun oneSite_sharesTheFirstRowWithTheRatio() {
        showSection(row("w", CircumferenceSite.Waist, 84.5))

        val ratio = bounds("weight_whr_card")
        val waist = bounds(CircumferenceSite.Waist)
        assertSame("one row", ratio.top, waist.top)
        assertTrue("the ratio first", ratio.right <= waist.left)
        assertCardShows("weight_whr_card", "Sin datos suficientes")
    }

    @Test
    fun ratioCell_showsTheStatusChip_whenTheGoalHasTheMetric() {
        val metric = MetricInterpretation(MetricKind.WaistToHip, 0.86, MetricStatus.WhrLowRisk)
        showSection(
            row("w", CircumferenceSite.Waist, 84.5),
            row("h", CircumferenceSite.Hip, 98.0),
            ratioGauge = { MetricStatusGauge(metric, Sex.Male) },
        )

        assertCardShows("weight_whr_card", "0,86")
        assertCardShows("weight_whr_card", "Riesgo bajo")
    }

    @Test
    fun ratioCell_withoutTheMetric_hasNoStatusChip() {
        showSection(row("w", CircumferenceSite.Waist, 84.5), row("h", CircumferenceSite.Hip, 98.0))

        assertCardShows("weight_whr_card", "0,86")
        rule.onNode(hasText("Riesgo", substring = true), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun tapOnASecondRowCell_opensThatSite() {
        showSection(
            row("c", CircumferenceSite.Chest, 101.0),
            row("w", CircumferenceSite.Waist, 84.5),
            row("h", CircumferenceSite.Hip, 98.0),
        )

        rule.onNodeWithTag(siteTag(CircumferenceSite.Hip)).performClick()

        assertEquals(listOf(CircumferenceSite.Hip), openedSites)
    }

    @Test
    fun changeBeyondTheMargin_showsThePillAndTheReferenceDay_withNoMarginNote() {
        showSection(
            row("w1", CircumferenceSite.Waist, 88.5, "2026-09-20T08:30:00Z"),
            row("w2", CircumferenceSite.Waist, 84.5),
        )

        inChange(CircumferenceSite.Waist, cm("−4,0")).assertExists()
        inChange(CircumferenceSite.Waist, "desde el 20 sep").assertExists()
        child(CircumferenceSite.Waist, "margin_note").assertDoesNotExist()
    }

    @Test
    fun changeInsideTheMargin_readsDentroDelMargenDeMedida() {
        showSection(
            row("w1", CircumferenceSite.Waist, 86.5, "2026-09-20T08:30:00Z"),
            row("w2", CircumferenceSite.Waist, 84.5),
        )

        inChange(CircumferenceSite.Waist, cm("−2,0")).assertExists()
        child(CircumferenceSite.Waist, "margin_note").assertTextEquals("dentro del margen de medida")
    }

    @Test
    fun hipChangeAtItsMargin_hasNoMarginNote() {
        showSection(
            row("h1", CircumferenceSite.Hip, 100.0, "2026-09-20T08:30:00Z"),
            row("h2", CircumferenceSite.Hip, 98.0),
        )

        inChange(CircumferenceSite.Hip, cm("−2,0")).assertExists()
        child(CircumferenceSite.Hip, "margin_note").assertDoesNotExist()
    }

    @Test
    fun chestChange_hasNoMarginNote() {
        showSection(
            row("c1", CircumferenceSite.Chest, 100.0, "2026-09-20T08:30:00Z"),
            row("c2", CircumferenceSite.Chest, 99.5),
        )

        inChange(CircumferenceSite.Chest, cm("−0,5")).assertExists()
        child(CircumferenceSite.Chest, "margin_note").assertDoesNotExist()
    }

    @Test
    fun oneMeasurement_showsItsDay_andNoSparkline() {
        showSection(row("w", CircumferenceSite.Waist, 84.5))

        child(CircumferenceSite.Waist, "change").assertTextEquals("dom, 18 oct")
        child(CircumferenceSite.Waist, "sparkline").assertDoesNotExist()
    }

    @Test
    fun noPairTwentyEightDaysApart_showsTheSparklineAndTheDay_withNoPill() {
        showSection(
            row("w1", CircumferenceSite.Waist, 86.0, "2026-10-01T08:30:00Z"),
            row("w2", CircumferenceSite.Waist, 84.5),
        )

        child(CircumferenceSite.Waist, "sparkline").assertExists()
        child(CircumferenceSite.Waist, "change").assertTextEquals("dom, 18 oct")
        inChange(CircumferenceSite.Waist, "cm").assertDoesNotExist()
    }

    @Test
    fun siteCell_isOneButtonReadingVerHistorial_atLeast48dpTall_andOpensTheSite() {
        showSection(row("w", CircumferenceSite.Waist, 84.5))

        val node = rule.onNodeWithTag(siteTag(CircumferenceSite.Waist))
        node.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        assertEquals("Ver historial", node.fetchSemanticsNode().config[SemanticsActions.OnClick].label)
        node.assertHeightIsAtLeast(48.dp)
        node.assertTextContains("Cintura", substring = true).assertTextContains(cm("84,5"), substring = true)

        node.performClick()

        assertEquals(listOf(CircumferenceSite.Waist), openedSites)
    }

    @Test
    fun addingCintura_updatesItsCellValueChangeAndSparkline() {
        show(row("w1", CircumferenceSite.Waist, 86.5, "2026-09-23T08:30:00Z"))
        child(CircumferenceSite.Waist, "sparkline").assertDoesNotExist()

        logMeasurement("84,5")

        value(CircumferenceSite.Waist, cm("84,5")).assertExists()
        inChange(CircumferenceSite.Waist, cm("−2,0")).assertExists()
        inChange(CircumferenceSite.Waist, "desde el 23 sep").assertExists()
        child(CircumferenceSite.Waist, "sparkline").assertExists()
    }

    @Test
    fun sparkline_spansAtLeastThreeQuartersOfTheCell_atEveryFontScale() {
        showSection(
            row("w1", CircumferenceSite.Waist, 86.5, "2026-09-20T08:30:00Z"),
            row("w2", CircumferenceSite.Waist, 84.5),
        )
        listOf(1f, 1.3f, 2f).forEach {
            setScale(it)

            val cellWidth = bounds(CircumferenceSite.Waist).width
            val lineWidth = child(CircumferenceSite.Waist, "sparkline").getUnclippedBoundsInRoot().width
            assertTrue("font scale $it: $lineWidth of $cellWidth", lineWidth >= cellWidth * 0.75f)
        }
    }

    /** Pantorrilla, the longest site name, with a three-digit value and (with [calfBefore]) a change pill. */
    private val calfLatest = row("c2", CircumferenceSite.Calf, 123.4)
    private val calfBefore = row("c1", CircumferenceSite.Calf, 125.4, "2026-09-20T08:30:00Z")

    /**
     * Every line of [text] ends inside its text box, nothing is cut at the bottom and the last line is not ellipsized.
     * `hasVisualOverflow` is not used: a `Text` measured twice (the label row) keeps the paragraph of the wider pass,
     * so it reports a width overflow although every glyph is inside the box.
     */
    private fun assertWhole(text: String, context: String) {
        val layout = textLayout(text)
        for (line in 0 until layout.lineCount) {
            val right = layout.getLineRight(line)
            assertTrue(
                "$context: '$text' line $line ends at $right of ${layout.size.width}",
                right <= layout.size.width,
            )
        }
        assertFalse("$context: '$text' cut at the bottom", layout.didOverflowHeight)
        assertFalse("$context: '$text' ellipsized", layout.isLineEllipsized(layout.lineCount - 1))
    }

    /** "Pantorrilla" whole, "123,4 cm" and the change pill on one line each, the change slot on screen. */
    private fun assertCalfCellWhole(context: String) {
        assertWhole("Pantorrilla", context)
        assertWhole(cm("123,4"), context)
        assertEquals("$context: value on one line", 1, textLayout(cm("123,4")).lineCount)
        value(CircumferenceSite.Calf, cm("123,4")).assertIsDisplayed()
        assertWhole(cm("−2,0"), context)
        assertEquals("$context: pill on one line", 1, textLayout(cm("−2,0")).lineCount)
        child(CircumferenceSite.Calf, "change").assertIsDisplayed()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // Real text metrics: the fit checks measure actual glyph widths.
    fun largeFonts_stackIntoOneColumn_withNothingClipped() {
        showSection(row("p", CircumferenceSite.Chest, 101.0), calfBefore, calfLatest)
        listOf(1.3f, 2f).forEach {
            setScale(it)

            val grid = bounds("circumference_sites")
            val ratio = bounds("weight_whr_card")
            val chest = bounds(CircumferenceSite.Chest)
            val calf = bounds(CircumferenceSite.Calf)
            assertTrue(
                "font scale $it: ratio, Pecho, Pantorrilla",
                ratio.bottom <= chest.top && chest.bottom <= calf.top,
            )
            listOf(ratio, chest, calf).forEach { cell ->
                assertSame("font scale $it: full width", grid.width, cell.width)
            }
            assertCalfCellWhole("font scale $it")
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h2400dp-xxhdpi")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun narrowWindow320_keepsTwoColumns_withPantorrillaAndValueWhole() {
        show(calfBefore, calfLatest)
        // 1.15 is the largest Android font step that keeps two columns.
        listOf(1f, 1.15f).forEach {
            setScale(it)

            val ratio = bounds("weight_whr_card")
            val calf = bounds(CircumferenceSite.Calf)
            assertSame("font scale $it: one row", ratio.top, calf.top)
            assertTrue("font scale $it: the ratio first", ratio.right <= calf.left)
            assertCalfCellWhole("font scale $it")
        }
    }

    @Test
    @Config(qualifiers = "w840dp-h1200dp-xxhdpi")
    fun tablet_sectionInTheRightPane_inTwoColumns() {
        show(row("w", CircumferenceSite.Waist, 84.5), row("h", CircumferenceSite.Hip, 98.0))

        rule.onNodeWithTag("weight_two_column").assertExists()
        val section = bounds("circumference_section")
        assertTrue("right pane", section.left >= bounds("weight_current_card").right)
        val ratio = bounds("weight_whr_card")
        val waist = bounds(CircumferenceSite.Waist)
        assertSame("two columns", ratio.top, waist.top)
        assertTrue("inside the pane", ratio.left >= section.left && waist.right <= section.right)
    }

    // -- Validation -------------------------------------------------------------------------------------------------

    @Test
    fun valueBelowTheRange_disablesGuardar_andShowsTheRangeUnderThatSite() {
        show()
        tap("circumference_add_button")

        type("circumference_sheet_value_input_neck", "19,9")

        rule.onNodeWithTag("circumference_sheet_commit").assertIsNotEnabled()
        rule.onNodeWithTag("circumference_sheet_range_hint_neck").assertTextEquals("Entre 20 y 250\u00A0cm")
    }

    @Test
    fun valueAboveTheRange_disablesGuardar_andShowsTheRange() {
        show()
        tap("circumference_add_button")

        type("circumference_sheet_value_input_calf", "251")

        rule.onNodeWithTag("circumference_sheet_commit").assertIsNotEnabled()
        rule.onNodeWithTag("circumference_sheet_range_hint_calf").assertTextEquals("Entre 20 y 250\u00A0cm")
    }

    @Test
    fun oneInvalidSite_blocksGuardarEvenWithAValidOne() {
        show()
        tap("circumference_add_button")

        type("circumference_sheet_value_input_waist", "84,5")
        type("circumference_sheet_value_input_hip", "251")

        rule.onNodeWithTag("circumference_sheet_commit").assertIsNotEnabled()
    }

    @Test
    fun valuesAtTheLimits_areAccepted() {
        show()
        tap("circumference_add_button")

        type("circumference_sheet_value_input_waist", "20")
        rule.onNodeWithTag("circumference_sheet_commit").assertIsEnabled()
        type("circumference_sheet_value_input_waist", "250")

        rule.onNodeWithTag("circumference_sheet_commit").assertIsEnabled()
        rule.onNodeWithTag("circumference_sheet_range_hint_waist").assertDoesNotExist()
    }

    @Test
    fun aBlankValue_disablesGuardarWithoutAHint() {
        show()
        tap("circumference_add_button")

        rule.onNodeWithTag("circumference_sheet_commit").assertIsNotEnabled()
        rule.onNodeWithTag("circumference_sheet_range_hint_waist").assertDoesNotExist()
    }

    @Test
    fun closingTheSheetWithoutGuardar_savesNothing() {
        show()
        tap("circumference_add_button")
        type("circumference_sheet_value_input_waist", "84,5")

        rule.runOnUiThread { shownModel.onEvent(CircumferencesEvent.CloseSheet) }
        rule.waitForIdle()

        rule.onNodeWithTag("circumference_sheet").assertDoesNotExist()
        assertTrue(repository.rows.isEmpty())
    }

    // -- States -----------------------------------------------------------------------------------------------------

    @Test
    fun noRows_showsTheEmptyState() {
        show()

        rule.onNodeWithTag("circumference_empty").assertExists()
        rule.onNodeWithText("Aún no hay circunferencias").assertExists()
        rule.onNodeWithText("Registra tu primera circunferencia.").assertExists()
        rule.onNodeWithTag("circumference_add_button").assertTextEquals("Añadir circunferencia")
        rule.onNodeWithTag("circumference_sites").assertDoesNotExist()
        rule.onNodeWithTag("weight_whr_card").assertDoesNotExist()
    }

    @Test
    fun loading_showsFourSkeletonCells_twoPerRow() {
        rule.setContent {
            Scaled { CircumferenceSection(CircumferencesUiState.Loading, LocalDate(2026, 10, 21), {}) }
        }
        rule.waitForIdle()

        // Two rows of two 120 dp cells with a 12 dp gap; stacked, four rows (font scale 1.3).
        rule.onNodeWithTag("circumference_loading").assertHeightIsEqualTo(252.dp)
        setScale(1.3f)
        rule.onNodeWithTag("circumference_loading").assertHeightIsEqualTo(516.dp)
    }

    @Test
    fun failedLoad_showsALocalError_andRetryReloads_whileTheWeightScreenStaysUsable() {
        repository = FakeCircumferenceRepository(row("w", CircumferenceSite.Waist, 84.5))
        repository.failReads = true
        val weight = weightModel()
        val circumferences = circumferenceModel()
        rule.setContent { Host(weight, circumferences) }
        rule.waitForIdle()

        rule.onNodeWithTag("circumference_error").assertExists()
        rule.onNodeWithTag("weight_edit_today_button").assertExists()
        repository.failReads = false
        tap("circumference_error_action")

        rule.onNodeWithTag("circumference_site_cell_waist").assertExists()
    }

    @Test
    fun offline_disablesGuardarInTheAddSheet_withTheHint() {
        connectivity.state.value = false
        show(row("w", CircumferenceSite.Waist, 84.5))

        tap("circumference_add_button")
        type("circumference_sheet_value_input_waist", "84,0")

        rule.onNodeWithTag("circumference_sheet_commit").assertIsNotEnabled()
        rule.onNodeWithTag("circumference_sheet_commit_hint").assertTextEquals("Sin conexión: no se puede guardar")
    }

    @Test
    fun failedSave_keepsTheSheetOpenAndGuardarEnabled_withNoInlineError() {
        show()
        repository.writeFailure = AppException(AppError.Unknown)

        logMeasurement("84,5")

        rule.onNodeWithTag("circumference_sheet").assertExists()
        rule.onNodeWithTag("circumference_sheet_commit").assertIsEnabled()
        rule.onNodeWithTag("circumference_sheet_message").assertDoesNotExist()
    }

    @Test
    fun closingWithX_neverCommits() {
        show()
        tap("circumference_add_button")
        type("circumference_sheet_value_input_waist", "84,5")

        tap("circumference_sheet_close")
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag("circumference_sheet_title").fetchSemanticsNodes().isEmpty()
        }

        assertTrue(repository.rows.isEmpty())
    }

    // -- Layout -----------------------------------------------------------------------------------------------------

    @Test
    fun noCalendarGrid_isOnThePesoScreen_andTheSectionIsBelowThePesoActualCard() {
        show(row("w", CircumferenceSite.Waist, 84.5))

        rule.onNodeWithTag("weight_calendar").assertDoesNotExist()
        val card = rule.onNodeWithTag("weight_current_card").getUnclippedBoundsInRoot()
        val section = rule.onNodeWithTag("circumference_section").getUnclippedBoundsInRoot()
        assertTrue(section.top >= card.bottom)
    }
}
