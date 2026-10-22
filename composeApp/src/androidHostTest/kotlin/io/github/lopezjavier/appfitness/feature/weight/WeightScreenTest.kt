package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
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
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.usecase.MeasurementsByDay
import io.github.lopezjavier.appfitness.domain.usecase.SaveBodyMeasurement
import io.github.lopezjavier.appfitness.domain.usecase.ValidateBodyMeasurement
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

private val Today = LocalDate(2026, 10, 21)
private val Yesterday = LocalDate(2026, 10, 20)

private object TestClock : Clock {
    override fun today(): LocalDate = Today

    override fun now(): Instant = Instant.parse("2026-10-21T10:30:00Z")
}

/** US-25 on the weight screen: panel, calendar, day sheet, validation, Health Connect rows and deletion. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class WeightScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val connectivity = FakeConnectivityObserver()
    private lateinit var repository: FakeWeightRepository

    private fun manual(id: String, at: String, kg: Double, fat: Double? = null) =
        BodyMeasurement(id, Instant.parse(at), kg, MeasurementSource.Manual, fat)

    private fun healthConnect(id: String, at: String, kg: Double) =
        BodyMeasurement(id, Instant.parse(at), kg, MeasurementSource.HealthConnect)

    private fun viewModel(repository: FakeWeightRepository, state: SavedStateHandle = SavedStateHandle()) =
        WeightViewModel(
            repository = repository,
            measurementsByDay = MeasurementsByDay(),
            saveBodyMeasurement = SaveBodyMeasurement(repository, TestClock, TimeZone.UTC),
            validate = ValidateBodyMeasurement(),
            connectivity = connectivity,
            clock = TestClock,
            timeZone = TimeZone.UTC,
            savedState = state,
        )

    @Composable
    private fun Host(model: WeightViewModel) {
        val state by model.uiState.collectAsState()
        AppTheme {
            WeightScreen(state, model::onEvent, CircumferencesUiState.Loading, {}, timeZone = TimeZone.UTC)
        }
    }

    private fun show(vararg rows: BodyMeasurement) {
        repository = FakeWeightRepository(*rows)
        val model = viewModel(repository)
        rule.setContent { Host(model) }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun type(tag: String, text: String) {
        rule.onNodeWithTag(tag).performTextReplacement(text)
        rule.waitForIdle()
    }

    private fun description(tag: String): String? =
        rule.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)
            ?.firstOrNull()

    private fun SemanticsNodeInteraction.text(): String =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    // -- Current weight panel ---------------------------------------------------------------------------------------

    @Test
    fun panel_showsTheLatestManualWeightWithItsDateOnlyAndBodyFat() {
        show(
            manual("a", "2026-10-02T08:00:00Z", 75.0),
            manual("b", "2026-10-18T07:30:00Z", 73.9, fat = 15.1),
        )

        rule.onNodeWithTag("weight_current_value").assertTextContains("73,9 kg", substring = true)
        rule.onNodeWithTag("weight_current_value", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("weight_current_when", useUnmergedTree = true).assertTextEquals("18 oct")
        rule.onNodeWithTag("weight_current_fat", useUnmergedTree = true).assertTextEquals("Grasa corporal: 15,1 %")
    }

    @Test
    fun panel_ofAHealthConnectReading_keepsItsTime() {
        show(manual("a", "2026-10-02T08:00:00Z", 75.0), healthConnect("h", "2026-10-19T06:00:00Z", 73.5))

        rule.onNodeWithTag("weight_current_when", useUnmergedTree = true).assertTextEquals("19 oct, 06:00")
    }

    @Test
    fun panel_withoutBodyFat_showsNoFatLine() {
        show(manual("a", "2026-10-18T07:30:00Z", 73.9))

        rule.onNodeWithTag("weight_current_fat", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun panel_showsTheLatestRowOfAnySource() {
        show(
            manual("a", "2026-10-18T07:30:00Z", 73.9),
            healthConnect("h", "2026-10-19T06:00:00Z", 73.5),
        )

        rule.onNodeWithTag("weight_current_value").assertTextContains("73,5 kg", substring = true)
    }

    // -- Calendar ---------------------------------------------------------------------------------------------------

    @Test
    fun calendar_marksTheDaysWithMeasurements() {
        show(manual("a", "2026-10-18T07:30:00Z", 73.9))

        assertEquals("18 de octubre, con registros", description("month_calendar_day_2026-10-18"))
        assertEquals("19 de octubre, sin registros", description("month_calendar_day_2026-10-19"))
    }

    @Test
    fun calendar_nextArrowIsDisabledAtTheCurrentMonth_andPreviousLoadsTheOtherMonth() {
        show(manual("a", "2026-09-15T07:30:00Z", 75.2))

        rule.onNodeWithTag("month_calendar_next").assertIsNotEnabled()
        tap("month_calendar_prev")

        rule.onNodeWithTag("month_calendar_title").assertTextEquals("septiembre 2026")
        rule.onNodeWithTag("month_calendar_next").assertIsEnabled()
        assertEquals("15 de septiembre, con registros", description("month_calendar_day_2026-09-15"))
    }

    @Test
    fun calendar_doesNotLetYouPickAFutureDay() {
        show(manual("a", "2026-10-18T07:30:00Z", 73.9))

        rule.onNodeWithTag("month_calendar_day_2026-10-22").assertIsNotEnabled()
    }

    // -- Logging today's weight -------------------------------------------------------------------------------------

    @Test
    fun logToday_opensAnEmptySheetPrefilledWithTheLatestWeight_andSavingUpdatesThePanel() {
        show(manual("a", "2026-10-18T07:30:00Z", 74.2))

        tap("weight_log_today_button")
        rule.onNodeWithTag("weight_sheet_title").assertTextEquals("Registrar peso")
        assertEquals("74,2", rule.onNodeWithTag("weight_sheet_weight_input").text())
        rule.onNodeWithTag("weight_sheet_delete").assertDoesNotExist()
        type("weight_sheet_weight_input", "73,9")
        tap("weight_sheet_save")

        assertEquals(73.9, repository.saves.single().weightKg, 0.0)
        assertEquals(null, repository.saves.single().bodyFatPct)
        rule.onNodeWithTag("weight_day_sheet").assertDoesNotExist()
        rule.onNodeWithTag("weight_current_value").assertTextContains("73,9 kg", substring = true)
    }

    @Test
    fun logToday_withBodyFat_savesBothValues() {
        show(manual("a", "2026-10-18T07:30:00Z", 74.2))

        tap("weight_log_today_button")
        type("weight_sheet_weight_input", "73,9")
        type("weight_sheet_fat_input", "15,1")
        tap("weight_sheet_save")

        assertEquals(15.1, repository.saves.single().bodyFatPct!!, 0.0)
        rule.onNodeWithTag("weight_current_fat", useUnmergedTree = true).assertTextEquals("Grasa corporal: 15,1 %")
    }

    @Test
    fun logToday_afterTodayWasSaved_reopensItInEditMode() {
        show(manual("a", "2026-10-18T07:30:00Z", 74.2))
        tap("weight_log_today_button")
        type("weight_sheet_weight_input", "73,9")
        tap("weight_sheet_save")

        tap("weight_log_today_button")

        assertEquals("73,9", rule.onNodeWithTag("weight_sheet_weight_input").text())
        rule.onNodeWithTag("weight_sheet_delete").assertExists()
        rule.onNodeWithTag("weight_sheet_title").assertTextEquals("Peso del 21 oct")
        type("weight_sheet_weight_input", "73,5")
        tap("weight_sheet_save")
        assertEquals(
            1,
            repository.rows.count {
                it.source == MeasurementSource.Manual &&
                    it.measuredAt >= Instant.parse("2026-10-21T00:00:00Z")
            },
        )
    }

    // -- Past days --------------------------------------------------------------------------------------------------

    @Test
    fun emptyPastDay_opensTheSheetWithoutTime_andSavesAtNoon() {
        show(manual("a", "2026-10-18T07:30:00Z", 74.2))

        tap("month_calendar_day_$Yesterday")
        rule.onNodeWithTag("weight_sheet_title").assertTextEquals("Peso del 20 oct")
        assertNoTimeFields()
        type("weight_sheet_weight_input", "74,0")
        tap("weight_sheet_save")

        assertEquals(Instant.parse("2026-10-20T12:00:00Z"), repository.saves.single().measuredAt)
        assertEquals("20 de octubre, con registros", description("month_calendar_day_$Yesterday"))
    }

    @Test
    fun editingADay_changesWeight_keepsTheStoredInstant_andOffersNoDateOrTime() {
        show(manual("a", "2026-10-18T07:30:00Z", 74.2))

        tap("month_calendar_day_2026-10-18")
        rule.onNodeWithTag("weight_sheet_title").assertTextEquals("Peso del 18 oct")
        assertNoTimeFields()
        type("weight_sheet_weight_input", "73,8")
        tap("weight_sheet_save")

        val save = repository.saves.single()
        assertEquals("a", save.id)
        assertEquals(73.8, save.weightKg, 0.0)
        assertEquals(Instant.parse("2026-10-18T07:30:00Z"), save.measuredAt)
        rule.onNodeWithTag("weight_sheet_date").assertDoesNotExist()
    }

    @Test
    fun loggingToday_hasNoTimeFields_andSavesTheCurrentInstant() {
        show(manual("a", "2026-10-18T07:30:00Z", 74.2))

        tap("weight_log_today_button")
        assertNoTimeFields()
        type("weight_sheet_weight_input", "74,0")
        tap("weight_sheet_save")

        assertEquals(Instant.parse("2026-10-21T10:30:00Z"), repository.saves.single().measuredAt)
    }

    private fun assertNoTimeFields() {
        listOf("weight_sheet_time_value", "weight_sheet_hour", "weight_sheet_minute").forEach {
            rule.onNodeWithTag(it, useUnmergedTree = true).assertDoesNotExist()
        }
    }

    // -- Field alignment --------------------------------------------------------------------------------------------

    private fun assertFieldsAligned() {
        openTodaySheet()
        val weight = rule.onNodeWithTag("weight_sheet_weight_input").getUnclippedBoundsInRoot()
        val fat = rule.onNodeWithTag("weight_sheet_fat_input").getUnclippedBoundsInRoot()
        assertEquals(weight.top, fat.top)
        assertEquals(weight.bottom, fat.bottom)
        val weightLabel = rule.onNodeWithTag("weight_sheet_weight_label").getUnclippedBoundsInRoot()
        val fatLabel = rule.onNodeWithTag("weight_sheet_fat_label").getUnclippedBoundsInRoot()
        assertEquals(weightLabel.top, fatLabel.top)
    }

    @Test
    fun weightAndFatInputs_shareTheSameTopAndBottom() {
        assertFieldsAligned()
    }

    @Test
    @Config(fontScale = 1.3f)
    fun weightAndFatInputs_stayAlignedAtLargeFontScale() {
        assertFieldsAligned()
    }

    // -- Validation -------------------------------------------------------------------------------------------------

    private fun openTodaySheet() {
        show(manual("a", "2026-10-18T07:30:00Z", 74.2))
        tap("weight_log_today_button")
    }

    @Test
    fun weightBelowTheRange_disablesSave_andShowsTheRange() {
        openTodaySheet()

        type("weight_sheet_weight_input", "19,9")

        rule.onNodeWithTag("weight_sheet_save").assertIsNotEnabled()
        rule.onNodeWithTag("weight_sheet_range_hint").assertTextEquals("El peso debe estar entre 20 y 400 kg")
    }

    @Test
    fun weightAboveTheRange_disablesSave_andShowsTheRange() {
        openTodaySheet()

        type("weight_sheet_weight_input", "401")

        rule.onNodeWithTag("weight_sheet_save").assertIsNotEnabled()
        rule.onNodeWithTag("weight_sheet_range_hint").assertExists()
    }

    @Test
    fun bodyFatAboveTheRange_disablesSave_andShowsItsRange() {
        openTodaySheet()

        type("weight_sheet_fat_input", "80")

        rule.onNodeWithTag("weight_sheet_save").assertIsNotEnabled()
        rule.onNodeWithTag("weight_sheet_range_hint")
            .assertTextEquals("La grasa corporal debe estar entre 2 y 75 %")
    }

    @Test
    fun valuesAtTheLimits_areAccepted() {
        openTodaySheet()

        type("weight_sheet_weight_input", "20")
        type("weight_sheet_fat_input", "75")

        rule.onNodeWithTag("weight_sheet_save").assertIsEnabled()
        rule.onNodeWithTag("weight_sheet_range_hint").assertDoesNotExist()
    }

    @Test
    fun aBlankWeight_disablesSaveWithoutAnErrorMessage() {
        openTodaySheet()

        type("weight_sheet_weight_input", "")

        rule.onNodeWithTag("weight_sheet_save").assertIsNotEnabled()
        rule.onNodeWithTag("weight_sheet_range_hint").assertDoesNotExist()
    }

    // -- Deleting and Health Connect --------------------------------------------------------------------------------

    @Test
    fun eliminar_deletesTheManualWeightAtOnce_withNoUndo() {
        show(
            manual("a", "2026-10-18T07:30:00Z", 74.2),
            manual("b", "2026-10-19T07:30:00Z", 73.0),
        )

        tap("month_calendar_day_2026-10-19")
        tap("weight_sheet_delete")

        assertEquals(listOf("b"), repository.deletes)
        rule.onNodeWithTag("weight_day_sheet").assertDoesNotExist()
        rule.onNodeWithText("Deshacer").assertDoesNotExist()
        assertEquals("19 de octubre, sin registros", description("month_calendar_day_2026-10-19"))
        rule.onNodeWithTag("weight_current_value").assertTextContains("74,2 kg", substring = true)
    }

    @Test
    fun healthConnectRow_showsItsIconAndOnlyADeleteButton() {
        show(healthConnect("h1", "2026-10-19T06:00:00Z", 73.5))

        tap("month_calendar_day_2026-10-19")

        rule.onNodeWithTag("weight_hc_row_h1").assertExists()
        rule.onNodeWithTag("weight_hc_icon_h1", useUnmergedTree = true).assertExists()
        assertEquals("Eliminar medición de 06:00", description("weight_hc_delete_h1"))
        rule.onNodeWithTag("weight_sheet_save").assertDoesNotExist()
        rule.onNodeWithTag("weight_sheet_weight_input").assertDoesNotExist()
        rule.onNodeWithTag("weight_set_manual_button").assertExists()
    }

    @Test
    fun healthConnectDelete_removesTheRowAtOnce() {
        show(healthConnect("h1", "2026-10-19T06:00:00Z", 73.5))

        tap("month_calendar_day_2026-10-19")
        tap("weight_hc_delete_h1")

        assertEquals(listOf("h1"), repository.deletes)
        rule.onNodeWithTag("weight_day_sheet").assertDoesNotExist()
    }

    @Test
    fun dayWithOnlyHealthConnectRows_canSetItsManualWeight() {
        show(healthConnect("h1", "2026-10-19T06:00:00Z", 73.5))

        tap("month_calendar_day_2026-10-19")
        tap("weight_set_manual_button")

        rule.onNodeWithTag("weight_hc_row_h1").assertExists()
        rule.onNodeWithTag("weight_sheet_weight_input").assertExists()
        type("weight_sheet_weight_input", "73,0")
        tap("weight_sheet_save")
        assertEquals(73.0, repository.saves.single().weightKg, 0.0)
    }

    // -- States -----------------------------------------------------------------------------------------------------

    @Test
    fun loading_showsTheSkeleton() {
        rule.setContent {
            AppTheme {
                WeightScreen(WeightUiState.Loading, {}, CircumferencesUiState.Loading, {}, timeZone = TimeZone.UTC)
            }
        }

        rule.onNodeWithTag("weight_loading").assertExists()
    }

    @Test
    fun noMeasurements_showsTheEmptyState_andKeepsTheLogButton() {
        show()

        rule.onNodeWithTag("weight_empty").assertExists()
        rule.onNodeWithTag("weight_log_today_button").assertExists()
        rule.onNodeWithTag("weight_panel").assertDoesNotExist()
    }

    @Test
    fun failedLoad_showsTheError_andRetryReloads() {
        repository = FakeWeightRepository(manual("a", "2026-10-18T07:30:00Z", 73.9))
        repository.failReads = true
        val model = viewModel(repository)
        rule.setContent { Host(model) }
        rule.waitForIdle()

        rule.onNodeWithTag("weight_error").assertExists()
        repository.failReads = false
        tap("weight_error_action")

        rule.onNodeWithTag("weight_current_value").assertTextContains("73,9 kg", substring = true)
    }

    @Test
    fun offline_showsTheBanner_andDisablesGuardarAndEliminar() {
        connectivity.state.value = false
        show(manual("a", "2026-10-19T07:30:00Z", 74.2))

        rule.onNodeWithTag("weight_offline_banner").assertExists()
        tap("month_calendar_day_2026-10-19")

        rule.onNodeWithTag("weight_sheet_save").assertIsNotEnabled()
        rule.onNodeWithTag("weight_sheet_delete").assertIsNotEnabled()
        rule.onNodeWithTag("weight_sheet_message").assertExists()
    }

    @Test
    fun failedSave_keepsTheSheetOpenWithAnError() {
        show(manual("a", "2026-10-18T07:30:00Z", 74.2))
        repository.saveFailure = AppException(AppError.Unknown)

        tap("weight_log_today_button")
        tap("weight_sheet_save")

        rule.onNodeWithTag("weight_day_sheet").assertExists()
        rule.onNodeWithTag("weight_sheet_message").assertTextEquals("No se pudo guardar. Inténtalo de nuevo.")
    }

    @Test
    fun circumferenceSection_isPresentBelowTheCalendar() {
        show(manual("a", "2026-10-18T07:30:00Z", 73.9))

        rule.onNodeWithTag("circumference_section").assertExists()
        rule.onNodeWithTag("circumference_title").assertTextEquals("Circunferencias")
    }

    // -- Layout -----------------------------------------------------------------------------------------------------

    @Test
    fun phone_stacksThePanelAboveTheCalendar() {
        show(manual("a", "2026-10-18T07:30:00Z", 73.9))

        val panel = rule.onNodeWithTag("weight_panel").getUnclippedBoundsInRoot()
        val calendar = rule.onNodeWithTag("weight_calendar").getUnclippedBoundsInRoot()
        assertTrue(calendar.top >= panel.bottom)
    }

    @Test
    @Config(qualifiers = "w900dp-h1200dp-mdpi")
    fun wideWindow_putsThePanelLeftOfTheCalendar() {
        show(manual("a", "2026-10-18T07:30:00Z", 73.9))

        val panel = rule.onNodeWithTag("weight_panel").getUnclippedBoundsInRoot()
        val calendar = rule.onNodeWithTag("weight_calendar").getUnclippedBoundsInRoot()
        assertTrue(calendar.left >= panel.right)
    }
}
