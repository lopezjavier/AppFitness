package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
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
        tap("circumference_sheet_save")
    }

    // -- Logging a circumference ------------------------------------------------------------------------------------

    @Test
    fun section_hasAHeadingAndAnAddIconButton() {
        show()

        rule.onNodeWithTag("circumference_title").assertTextEquals("Circunferencias")
        val description = rule.onNodeWithTag("circumference_add_button").fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
        assertEquals("Añadir medida", description)
    }

    @Test
    fun loggingWaist_showsTheRowInTheWaistHistory() {
        show()

        tap("circumference_add_button")
        rule.onNodeWithTag("circumference_sheet_title").assertTextEquals("Añadir medida")
        type("circumference_sheet_value_input_waist", "84,5")
        tap("circumference_sheet_save")

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
        rule.onNodeWithTag("circumference_sheet_save").assertIsNotEnabled()
        type("circumference_sheet_value_input_waist", "84,5")
        type("circumference_sheet_value_input_hip", "98,0")
        tap("circumference_sheet_save")

        assertEquals(setOf(CircumferenceSite.Waist, CircumferenceSite.Hip), repository.rows.map { it.site }.toSet())
        assertEquals(2, repository.rows.size)
        rule.onNodeWithTag("circumference_group_hip").assertExists()
        rule.onNodeWithTag("circumference_ratio").assertTextContains("0,86", substring = true)
    }

    @Test
    fun tappingTheHelp_showsTheInstructionsOfThatSiteOnly_andTogglesThem() {
        show()
        tap("circumference_add_button")

        tap("circumference_sheet_help_waist")
        rule.onNodeWithTag("circumference_sheet_help_text_waist").assertTextContains("ombligo", substring = true)
        tap("circumference_sheet_help_hip")
        rule.onNodeWithTag("circumference_sheet_help_text_waist").assertDoesNotExist()
        rule.onNodeWithTag("circumference_sheet_help_text_hip").assertExists()
        tap("circumference_sheet_help_hip")

        rule.onNodeWithTag("circumference_sheet_help_text_hip").assertDoesNotExist()
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
        rule.onNodeWithTag("circumference_sheet_date_text").assertTextEquals("19 oct")
        type("circumference_sheet_value_input_waist", "84,5")
        tap("circumference_sheet_save")

        assertEquals(Instant.parse("2026-10-19T12:00:00Z"), repository.rows.single().measuredAt)
    }

    // -- Waist-to-hip ratio -----------------------------------------------------------------------------------------

    @Test
    fun waistAndHip_showTheRatio() {
        show(row("w", CircumferenceSite.Waist, 84.5), row("h", CircumferenceSite.Hip, 98.0))

        rule.onNodeWithTag("circumference_ratio").assertTextContains("0,86", substring = true)
        rule.onNodeWithTag("circumference_ratio")
            .assertTextContains("Relación cintura-cadera", substring = true, ignoreCase = true)
    }

    @Test
    fun onlyWaist_showsNotEnoughData() {
        show(row("w", CircumferenceSite.Waist, 84.5))

        rule.onNodeWithTag("circumference_ratio").assertTextContains("Sin datos suficientes", substring = true)
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
        tap("circumference_sheet_save")

        assertEquals(86.0, repository.rows.first { it.id == "w" }.valueCm, 0.0)
        assertRowShows("circumference_row_w", "86,0 cm")
        rule.onNodeWithTag("circumference_ratio").assertTextContains("0,88", substring = true)
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
        rule.onNodeWithTag("circumference_ratio").assertTextContains("0,86", substring = true)

        deleteBySwipeAction("circumference_row_w", "Eliminar Cintura 84,5 cm")

        assertEquals(listOf("w"), repository.deletes)
        rule.onNodeWithTag("circumference_row_w").assertDoesNotExist()
        rule.onNodeWithTag("circumference_ratio").assertTextContains("Sin datos suficientes", substring = true)
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

        rule.onNodeWithTag("circumference_sheet_save").assertIsNotEnabled()
        rule.onNodeWithTag("circumference_sheet_range_hint_neck").assertTextEquals("Entre 20 y 250 cm")
    }

    @Test
    fun valueAboveTheRange_disablesGuardar_andShowsTheRange() {
        show()
        tap("circumference_add_button")

        type("circumference_sheet_value_input_calf", "251")

        rule.onNodeWithTag("circumference_sheet_save").assertIsNotEnabled()
        rule.onNodeWithTag("circumference_sheet_range_hint_calf").assertTextEquals("Entre 20 y 250 cm")
    }

    @Test
    fun oneInvalidSite_blocksGuardarEvenWithAValidOne() {
        show()
        tap("circumference_add_button")

        type("circumference_sheet_value_input_waist", "84,5")
        type("circumference_sheet_value_input_hip", "251")

        rule.onNodeWithTag("circumference_sheet_save").assertIsNotEnabled()
    }

    @Test
    fun valuesAtTheLimits_areAccepted() {
        show()
        tap("circumference_add_button")

        type("circumference_sheet_value_input_waist", "20")
        rule.onNodeWithTag("circumference_sheet_save").assertIsEnabled()
        type("circumference_sheet_value_input_waist", "250")

        rule.onNodeWithTag("circumference_sheet_save").assertIsEnabled()
        rule.onNodeWithTag("circumference_sheet_range_hint_waist").assertDoesNotExist()
    }

    @Test
    fun aBlankValue_disablesGuardarWithoutAHint() {
        show()
        tap("circumference_add_button")

        rule.onNodeWithTag("circumference_sheet_save").assertIsNotEnabled()
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
        rule.onNodeWithText("Registra tu primera circunferencia con el botón +.").assertExists()
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
    fun offline_disablesGuardarAndEliminar_withTheReason_andLocksSwipeDelete() {
        connectivity.state.value = false
        show(row("w", CircumferenceSite.Waist, 84.5))

        val actions = rule.onNodeWithTag("circumference_row_w").fetchSemanticsNode().config
            .getOrNull(SemanticsActions.CustomActions)
        assertTrue(actions.isNullOrEmpty())
        tap("circumference_row_w")

        rule.onNodeWithTag("circumference_sheet_save").assertIsNotEnabled()
        rule.onNodeWithTag("circumference_sheet_delete").assertIsNotEnabled()
        rule.onNodeWithTag("circumference_sheet_message")
            .assertTextEquals("Sin conexión: no se puede guardar ni eliminar")
    }

    @Test
    fun failedSave_keepsTheSheetOpenWithAnError() {
        show()
        repository.writeFailure = AppException(AppError.Unknown)

        logMeasurement("84,5")

        rule.onNodeWithTag("circumference_sheet").assertExists()
        rule.onNodeWithTag("circumference_sheet_message").assertTextEquals("No se pudo guardar. Inténtalo de nuevo.")
    }

    // -- Layout -----------------------------------------------------------------------------------------------------

    @Test
    fun phone_putsTheSectionBelowTheCalendar() {
        show(row("w", CircumferenceSite.Waist, 84.5))

        val calendar = rule.onNodeWithTag("weight_calendar").getUnclippedBoundsInRoot()
        val section = rule.onNodeWithTag("circumference_section").getUnclippedBoundsInRoot()
        assertTrue(section.top >= calendar.bottom)
    }

    @Test
    @Config(qualifiers = "w900dp-h1200dp-mdpi")
    fun wideWindow_putsTheSectionUnderTheCalendarInTheRightColumn() {
        show(row("w", CircumferenceSite.Waist, 84.5))

        val calendar = rule.onNodeWithTag("weight_calendar").getUnclippedBoundsInRoot()
        val section = rule.onNodeWithTag("circumference_section").getUnclippedBoundsInRoot()
        val panel = rule.onNodeWithTag("weight_edit_today_button").getUnclippedBoundsInRoot()
        assertTrue(section.top >= calendar.bottom)
        assertTrue(section.left >= panel.right)
    }
}
