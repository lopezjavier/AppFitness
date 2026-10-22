package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.usecase.MeasurementsByDay
import io.github.lopezjavier.appfitness.domain.usecase.SaveBodyMeasurement
import io.github.lopezjavier.appfitness.domain.usecase.ValidateBodyMeasurement
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

private object CompositionClock : Clock {
    override fun today(): LocalDate = LocalDate(2026, 10, 21)

    override fun now(): Instant = Instant.parse("2026-10-21T10:30:00Z")
}

/** US-27 "Composition values" and US-25 "Health Connect weight takes precedence" (ADR-47) on the weight screen. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class WeightScreenCompositionTest {
    @get:Rule
    val rule = createComposeRule()

    private val healthConnectDay = BodyMeasurement(
        id = "h1",
        measuredAt = Instant.parse("2026-10-19T06:00:00Z"),
        weightKg = 73.5,
        source = MeasurementSource.HealthConnect,
        bodyFatPct = 18.5,
        leanMassKg = 58.1,
        boneKg = 3.2,
    )
    private val olderManual = BodyMeasurement(
        id = "m1",
        measuredAt = Instant.parse("2026-10-02T08:00:00Z"),
        weightKg = 75.0,
        source = MeasurementSource.Manual,
        waterKg = 41.0,
    )

    private fun viewModel(repository: FakeWeightRepository, state: SavedStateHandle) = WeightViewModel(
        repository = repository,
        measurementsByDay = MeasurementsByDay(),
        saveBodyMeasurement = SaveBodyMeasurement(repository, CompositionClock, TimeZone.UTC),
        validate = ValidateBodyMeasurement(),
        connectivity = FakeConnectivityObserver(),
        clock = CompositionClock,
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

    private lateinit var shownModel: WeightViewModel

    private fun show(vararg rows: BodyMeasurement, state: SavedStateHandle = SavedStateHandle()) {
        val model = viewModel(FakeWeightRepository(*rows), state).also { shownModel = it }
        rule.setContent { Host(model) }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun description(tag: String): String? =
        rule.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)
            ?.firstOrNull()

    // -- "+" and the Composición corporal sheet ---------------------------------------------------------------------

    @Test
    fun plusButton_isATouchTargetWithASpokenLabelAndOpensTheSheet() {
        show(healthConnectDay, olderManual)

        assertEquals("Ver composición corporal", description("weight_composition_button"))
        rule.onNodeWithTag("weight_composition_button").assertHeightIsAtLeast(48.dp)
        tap("weight_composition_button")

        rule.onNodeWithTag("weight_composition_title").assertTextEquals("Composición corporal")
    }

    @Test
    fun compositionSheet_showsTheNewestValuePerMetricWithItsDate() {
        show(healthConnectDay, olderManual)

        tap("weight_composition_button")

        assertEquals("Grasa corporal, 18,5 %, 19 oct", description("weight_composition_fat"))
        assertEquals("Masa magra, 58,1 kg, 19 oct", description("weight_composition_lean"))
        assertEquals("Agua, 41,0 kg, 2 oct", description("weight_composition_water"))
        assertEquals("Masa ósea, 3,2 kg, 19 oct", description("weight_composition_bone"))
    }

    @Test
    fun compositionSheet_marksAMetricThatWasNeverMeasuredAsWithoutData() {
        show(healthConnectDay, olderManual)

        tap("weight_composition_button")

        assertEquals("TMB, sin datos", description("weight_composition_bmr"))
    }

    @Test
    fun compositionSheet_isReadOnlyAndGoneOnceTheViewModelClosesIt() {
        show(healthConnectDay)

        tap("weight_composition_button")
        rule.onNodeWithTag("weight_composition_sheet").assertIsDisplayed()
        rule.onNodeWithTag("weight_sheet_save").assertDoesNotExist()
        rule.runOnUiThread { shownModel.onEvent(WeightEvent.CloseComposition) }
        rule.waitForIdle()

        rule.onNodeWithTag("weight_composition_title").assertDoesNotExist()
    }

    @Test
    fun compositionSheet_openStateSurvivesRecreation() {
        val savedState = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { Host(viewModel(FakeWeightRepository(healthConnectDay), savedState)) }
        rule.waitForIdle()
        tap("weight_composition_button")

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("weight_composition_title").assertIsDisplayed()
    }

    // -- Day detail ---------------------------------------------------------------------------------------------------

    @Test
    fun daySheet_showsThatDaysCompositionIncludingBodyFat() {
        show(healthConnectDay, olderManual)

        tap("month_calendar_day_2026-10-19")

        rule.onNodeWithTag("weight_day_composition_body_fat").assertTextContains("18,5 %", substring = true)
        rule.onNodeWithTag("weight_day_composition_lean_mass").assertTextContains("58,1 kg", substring = true)
        rule.onNodeWithTag("weight_day_composition_water").assertDoesNotExist()
    }

    // -- Health Connect precedence ---------------------------------------------------------------------------------

    @Test
    fun healthConnectDay_showsTheNoteAndNoManualEntry() {
        show(healthConnectDay)

        tap("month_calendar_day_2026-10-19")

        rule.onNodeWithTag("weight_sheet_hc_note").assertTextEquals("Sincronizado desde Health Connect")
        rule.onNodeWithTag("weight_set_manual_button").assertDoesNotExist()
        rule.onNodeWithTag("weight_sheet_weight_input").assertDoesNotExist()
        rule.onNodeWithTag("weight_sheet_save").assertDoesNotExist()
    }

    @Test
    fun logToday_onADayWithHealthConnectWeight_opensTheNoteInsteadOfAForm() {
        val todayHc = healthConnectDay.copy(id = "h2", measuredAt = Instant.parse("2026-10-21T06:00:00Z"))
        show(todayHc)

        tap("weight_log_today_button")

        rule.onNodeWithTag("weight_sheet_hc_note").assertIsDisplayed()
        rule.onNodeWithTag("weight_sheet_weight_input").assertDoesNotExist()
        rule.onNodeWithTag("weight_sheet_save").assertDoesNotExist()
    }

    @Test
    fun dayWithoutHealthConnect_keepsTheManualForm() {
        show(olderManual)

        tap("month_calendar_day_2026-10-02")

        rule.onNodeWithTag("weight_sheet_hc_note").assertDoesNotExist()
        rule.onNodeWithTag("weight_sheet_weight_input").assertIsDisplayed()
        rule.onNodeWithTag("weight_sheet_save").assertIsDisplayed()
    }
}
