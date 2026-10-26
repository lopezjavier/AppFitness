package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
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
import io.github.lopezjavier.appfitness.domain.usecase.ValidateBodyMeasurement
import io.github.lopezjavier.appfitness.domain.usecase.ValidateCircumference
import io.github.lopezjavier.appfitness.domain.usecase.WaistHipRatio
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
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

/** US-25 circumferences on the weight screen: logging, ratio, editing, deleting, validation and states. */
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

    private fun assertRowShows(rowTag: String, text: String) {
        rule.onNode(hasText(text, substring = true) and hasAnyAncestor(hasTestTag(rowTag))).assertExists()
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

    private fun deleteBySwipeAction(tag: String, label: String) {
        val actions = rule.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions)
        val action = actions?.firstOrNull { it.label == label } ?: error("No action '$label' on $tag")
        rule.runOnUiThread { action.action() }
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
        listOf("circumference_row_w", "circumference_row_h", "weight_whr_card").forEach {
            assertTrue("$it above the add button", rule.onNodeWithTag(it).getUnclippedBoundsInRoot().bottom <= add.top)
        }
    }

    @Test
    fun history_rowsAreMeasurementRows_withTheListDate() {
        show(row("w", CircumferenceSite.Waist, 84.5))

        assertRowShows("circumference_row_w", "84,5 cm")
        assertRowShows("circumference_row_w", "dom, 18 oct")
    }

    @Test
    fun loggingWaist_showsTheRowInTheWaistHistory() {
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
        rule.onNodeWithTag("circumference_group_waist").assertExists()
        assertRowShows("circumference_row_${saved.id}", "84,5 cm")
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
        rule.onNodeWithTag("circumference_group_hip").assertExists()
        assertCardShows("weight_whr_card", "0,86")
    }

    @Test
    fun tappingTheHelp_opensAnInfoSheetWithTheInstructionsOfThatSite_andXClosesIt() {
        show()
        tap("circumference_add_button")

        tap("circumference_sheet_help_waist")
        rule.onNodeWithTag("circumference_sheet_help_waist_sheet_body").assertTextContains("ombligo", substring = true)
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

    // -- Editing and deleting ---------------------------------------------------------------------------------------

    @Test
    fun editingAValue_updatesTheRowAndTheRatio() {
        show(row("w", CircumferenceSite.Waist, 84.5), row("h", CircumferenceSite.Hip, 98.0))

        tap("circumference_row_w")
        rule.onNodeWithTag("circumference_sheet_title").assertTextEquals("Editar medida")
        rule.onNodeWithTag("circumference_sheet_value_input_waist").assertExists()
        rule.onNodeWithTag("circumference_sheet_value_input_hip").assertDoesNotExist()
        type("circumference_sheet_value_input_waist", "86,0")
        tap("circumference_sheet_commit")

        assertEquals(86.0, repository.rows.first { it.id == "w" }.valueCm, 0.0)
        assertRowShows("circumference_row_w", "86,0 cm")
        assertCardShows("weight_whr_card", "0,88")
    }

    @Test
    fun eliminarInTheEditSheet_removesTheRowAtOnce() {
        show(row("w", CircumferenceSite.Waist, 84.5))

        tap("circumference_row_w")
        tap("circumference_sheet_delete")

        assertEquals(listOf("w"), repository.deletes)
        rule.onNodeWithTag("circumference_sheet").assertDoesNotExist()
        rule.onNodeWithTag("circumference_row_w").assertDoesNotExist()
    }

    @Test
    fun swipeDelete_removesTheRowAtOnceAndUpdatesTheRatio_withNoUndo() {
        show(row("w", CircumferenceSite.Waist, 84.5), row("h", CircumferenceSite.Hip, 98.0))
        assertCardShows("weight_whr_card", "0,86")

        deleteBySwipeAction("circumference_row_w", "Eliminar Cintura 84,5 cm")

        assertEquals(listOf("w"), repository.deletes)
        rule.onNodeWithTag("circumference_row_w").assertDoesNotExist()
        assertCardShows("weight_whr_card", "Sin datos suficientes")
        rule.onNodeWithText("Deshacer").assertDoesNotExist()
    }

    @Test
    fun history_isOrderedNewestFirstWithinASite() {
        show(
            row("old", CircumferenceSite.Waist, 86.0, "2026-10-01T08:30:00Z"),
            row("new", CircumferenceSite.Waist, 84.5, "2026-10-18T08:30:00Z"),
        )

        val newest = rule.onNodeWithTag("circumference_row_new").getUnclippedBoundsInRoot()
        val oldest = rule.onNodeWithTag("circumference_row_old").getUnclippedBoundsInRoot()
        assertTrue(oldest.top >= newest.bottom)
        assertRowShows("circumference_row_new", "18 oct")
        rule.onNode(hasText("08:30", substring = true)).assertDoesNotExist()
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
        rule.onNodeWithText("Aún no hay medidas").assertExists()
        rule.onNodeWithText("Registra tu primera circunferencia.").assertExists()
    }

    @Test
    fun loading_showsTheSkeleton() {
        rule.setContent {
            AppTheme {
                CircumferenceSection(CircumferencesUiState.Loading, LocalDate(2026, 10, 21), TimeZone.UTC, {})
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

        rule.onNodeWithTag("circumference_row_w").assertExists()
    }

    @Test
    fun offline_disablesGuardarAndEliminar_withTheHint_andLocksSwipeDelete() {
        connectivity.state.value = false
        show(row("w", CircumferenceSite.Waist, 84.5))

        val actions = rule.onNodeWithTag("circumference_row_w").fetchSemanticsNode().config
            .getOrNull(SemanticsActions.CustomActions)
        assertTrue(actions.isNullOrEmpty())
        tap("circumference_row_w")

        rule.onNodeWithTag("circumference_sheet_commit").assertIsNotEnabled()
        rule.onNodeWithTag("circumference_sheet_delete").assertIsNotEnabled()
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
