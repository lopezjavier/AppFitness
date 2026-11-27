package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableFloatState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.BodyCircumference
import io.github.lopezjavier.appfitness.domain.model.CircumferenceSite
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.usecase.MeasurementsByDay
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
import kotlin.time.Instant

private object SectionClock : Clock {
    override fun today(): LocalDate = LocalDate(2026, 10, 21)

    override fun now(): Instant = Instant.parse("2026-10-21T10:30:00Z")
}

/** US-25 circumferences on the weight screen: logging, ratio, validation and states (T-324: one row per site). */
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

    @Composable
    private fun Host(weight: WeightViewModel, circumferences: CircumferencesViewModel) {
        val weightState by weight.uiState.collectAsState()
        val circumferenceState by circumferences.uiState.collectAsState()
        AppTheme {
            WeightScreen(
                state = weightState,
                onEvent = weight::onEvent,
                circumferences = circumferenceState,
                onCircumferenceEvent = circumferences::onEvent,
                timeZone = TimeZone.UTC,
            )
        }
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
        listOf("circumference_site_row_waist", "circumference_site_row_hip", "weight_whr_card").forEach {
            assertTrue("$it above the add button", rule.onNodeWithTag(it).getUnclippedBoundsInRoot().bottom <= add.top)
        }
    }

    @Test
    fun loggingWaist_showsTheWaistSiteRow() {
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
        rule.onNodeWithTag("circumference_site_row_waist_value", useUnmergedTree = true).assertTextEquals("84,5 cm")
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
        rule.onNodeWithTag("circumference_site_row_hip").assertExists()
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

    // -- One row per site (T-324) -----------------------------------------------------------------------------------

    private val openedSites = mutableListOf<CircumferenceSite>()

    private fun showSection(vararg rows: BodyCircumference) {
        val state = circumferenceSectionState(rows.toList())
        rule.setContent {
            AppTheme {
                CircumferenceSection(state, SectionClock.today(), {}, onOpenSite = { openedSites += it })
            }
        }
        rule.waitForIdle()
    }

    /** One `setContent` per test: the font scale changes through [scale]. */
    private fun showScaled(scale: MutableFloatState, vararg rows: BodyCircumference) {
        val state = circumferenceSectionState(rows.toList())
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale.floatValue)) {
                AppTheme { CircumferenceSection(state, SectionClock.today(), {}) }
            }
        }
        rule.waitForIdle()
    }

    private fun siteTag(site: CircumferenceSite) = "circumference_site_row_${site.name.lowercase()}"

    private fun child(site: CircumferenceSite, part: String) =
        rule.onNodeWithTag("${siteTag(site)}_$part", useUnmergedTree = true)

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
    fun severalSites_showOneRowPerSiteInSiteOrder_withEachNameOnce() {
        showSection(
            row("h1", CircumferenceSite.Hip, 99.0, "2026-10-01T08:30:00Z"),
            row("h2", CircumferenceSite.Hip, 98.0),
            row("w1", CircumferenceSite.Waist, 86.0, "2026-10-01T08:30:00Z"),
            row("w2", CircumferenceSite.Waist, 84.5),
            row("c1", CircumferenceSite.Chest, 101.0),
        )

        val chest = rule.onNodeWithTag(siteTag(CircumferenceSite.Chest)).getUnclippedBoundsInRoot()
        val waist = rule.onNodeWithTag(siteTag(CircumferenceSite.Waist)).getUnclippedBoundsInRoot()
        val hip = rule.onNodeWithTag(siteTag(CircumferenceSite.Hip)).getUnclippedBoundsInRoot()
        assertTrue(chest.bottom <= waist.top && waist.bottom <= hip.top)
        listOf("Pecho", "Cintura", "Cadera").forEach {
            assertEquals(it, 1, rule.onAllNodesWithText(it, useUnmergedTree = true).fetchSemanticsNodes().size)
        }
        child(CircumferenceSite.Waist, "value").assertTextEquals("84,5 cm")
        assertEquals(0, rule.onAllNodes(perMeasurementRow, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun changeBeyondTheMargin_showsThePillAndTheReferenceDay_withNoMarginNote() {
        showSection(
            row("w1", CircumferenceSite.Waist, 88.5, "2026-09-20T08:30:00Z"),
            row("w2", CircumferenceSite.Waist, 84.5),
        )

        inChange(CircumferenceSite.Waist, "−4,0 cm").assertExists()
        inChange(CircumferenceSite.Waist, "desde el 20 sep").assertExists()
        child(CircumferenceSite.Waist, "margin_note").assertDoesNotExist()
    }

    @Test
    fun changeInsideTheMargin_readsDentroDelMargenDeMedida() {
        showSection(
            row("w1", CircumferenceSite.Waist, 86.5, "2026-09-20T08:30:00Z"),
            row("w2", CircumferenceSite.Waist, 84.5),
        )

        inChange(CircumferenceSite.Waist, "−2,0 cm").assertExists()
        child(CircumferenceSite.Waist, "margin_note").assertTextEquals("dentro del margen de medida")
    }

    @Test
    fun hipChangeAtItsMargin_hasNoMarginNote() {
        showSection(
            row("h1", CircumferenceSite.Hip, 100.0, "2026-09-20T08:30:00Z"),
            row("h2", CircumferenceSite.Hip, 98.0),
        )

        inChange(CircumferenceSite.Hip, "−2,0 cm").assertExists()
        child(CircumferenceSite.Hip, "margin_note").assertDoesNotExist()
    }

    @Test
    fun chestChange_hasNoMarginNote() {
        showSection(
            row("c1", CircumferenceSite.Chest, 100.0, "2026-09-20T08:30:00Z"),
            row("c2", CircumferenceSite.Chest, 99.5),
        )

        inChange(CircumferenceSite.Chest, "−0,5 cm").assertExists()
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
    fun siteRow_isOneButtonReadingVerHistorial_atLeast48dpTall_andOpensTheSite() {
        showSection(row("w", CircumferenceSite.Waist, 84.5))

        val node = rule.onNodeWithTag(siteTag(CircumferenceSite.Waist))
        node.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        assertEquals("Ver historial", node.fetchSemanticsNode().config[SemanticsActions.OnClick].label)
        node.assertHeightIsAtLeast(48.dp)
        node.assertTextContains("Cintura", substring = true).assertTextContains("84,5 cm", substring = true)

        node.performClick()

        assertEquals(listOf(CircumferenceSite.Waist), openedSites)
    }

    @Test
    fun addingCintura_updatesItsRowValueChangeAndSparkline() {
        show(row("w1", CircumferenceSite.Waist, 86.5, "2026-09-23T08:30:00Z"))
        child(CircumferenceSite.Waist, "sparkline").assertDoesNotExist()

        logMeasurement("84,5")

        child(CircumferenceSite.Waist, "value").assertTextEquals("84,5 cm")
        inChange(CircumferenceSite.Waist, "−2,0 cm").assertExists()
        inChange(CircumferenceSite.Waist, "desde el 23 sep").assertExists()
        child(CircumferenceSite.Waist, "sparkline").assertExists()
    }

    @Test
    fun sparkline_spansAtLeastThreeQuartersOfTheRow_atEveryFontScale() {
        val scale = mutableFloatStateOf(1f)
        showScaled(
            scale,
            row("w1", CircumferenceSite.Waist, 86.5, "2026-09-20T08:30:00Z"),
            row("w2", CircumferenceSite.Waist, 84.5),
        )
        listOf(1f, 1.3f, 2f).forEach {
            scale.floatValue = it
            rule.waitForIdle()

            val rowWidth = rule.onNodeWithTag(siteTag(CircumferenceSite.Waist)).getUnclippedBoundsInRoot().width
            val lineWidth = child(CircumferenceSite.Waist, "sparkline").getUnclippedBoundsInRoot().width
            assertTrue("font scale $it: $lineWidth of $rowWidth", lineWidth >= rowWidth * 0.75f)
        }
    }

    @Test
    fun largeFonts_keepPantorrillaWhole_withTheValueUnderItAndTheChangeShown() {
        val scale = mutableFloatStateOf(1.3f)
        showScaled(scale, row("c", CircumferenceSite.Calf, 123.4))
        listOf(1.3f, 2f).forEach {
            scale.floatValue = it
            rule.waitForIdle()

            assertFalse("font scale $it", textLayout("Pantorrilla").hasVisualOverflow)
            child(CircumferenceSite.Calf, "value").assertIsDisplayed()
            child(CircumferenceSite.Calf, "change").assertIsDisplayed()
            val name = rule.onNode(hasText("Pantorrilla"), useUnmergedTree = true).getUnclippedBoundsInRoot()
            val value = child(CircumferenceSite.Calf, "value").getUnclippedBoundsInRoot()
            assertTrue("font scale $it: the value goes under the name", value.top >= name.bottom)
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h2400dp-xxhdpi")
    fun narrowWindow_keepsPantorrillaWholeBesideAThreeDigitValue() {
        showSection(row("c", CircumferenceSite.Calf, 123.4))

        val name = textLayout("Pantorrilla")
        assertFalse(name.hasVisualOverflow)
        assertFalse(name.isLineEllipsized(name.lineCount - 1))
        child(CircumferenceSite.Calf, "value").assertTextEquals("123,4 cm").assertIsDisplayed()
    }

    // -- Validation -------------------------------------------------------------------------------------------------

    @Test
    fun valueBelowTheRange_disablesGuardar_andShowsTheRangeUnderThatSite() {
        show()
        tap("circumference_add_button")

        type("circumference_sheet_value_input_neck", "19,9")

        rule.onNodeWithTag("circumference_sheet_commit").assertIsNotEnabled()
        rule.onNodeWithTag("circumference_sheet_range_hint_neck").assertTextEquals("Entre 20 y 250 cm")
    }

    @Test
    fun valueAboveTheRange_disablesGuardar_andShowsTheRange() {
        show()
        tap("circumference_add_button")

        type("circumference_sheet_value_input_calf", "251")

        rule.onNodeWithTag("circumference_sheet_commit").assertIsNotEnabled()
        rule.onNodeWithTag("circumference_sheet_range_hint_calf").assertTextEquals("Entre 20 y 250 cm")
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
    }

    @Test
    fun loading_showsTheSkeleton() {
        rule.setContent {
            AppTheme {
                CircumferenceSection(CircumferencesUiState.Loading, LocalDate(2026, 10, 21), {})
            }
        }

        rule.onNodeWithTag("circumference_loading").assertExists()
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

        rule.onNodeWithTag("circumference_site_row_waist").assertExists()
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
