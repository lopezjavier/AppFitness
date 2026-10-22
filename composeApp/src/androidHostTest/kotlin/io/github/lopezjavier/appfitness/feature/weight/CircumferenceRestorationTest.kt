package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

private object CircumferenceRestorationClock : Clock {
    override fun today(): LocalDate = LocalDate(2026, 10, 21)

    override fun now(): Instant = Instant.parse("2026-10-21T10:30:00Z")
}

/** The open circumference sheet and its half-filled draft survive recreation (ADR-40), for add and for edit. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class CircumferenceRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val repository = FakeCircumferenceRepository(
        BodyCircumference("w", CircumferenceSite.Waist, 84.5, Instant.parse("2026-10-18T08:30:00Z")),
    )

    private fun weightModel(): WeightViewModel {
        val weights = FakeWeightRepository()
        return WeightViewModel(
            repository = weights,
            measurementsByDay = MeasurementsByDay(),
            saveBodyMeasurement = SaveBodyMeasurement(weights, CircumferenceRestorationClock, TimeZone.UTC),
            validate = ValidateBodyMeasurement(),
            connectivity = FakeConnectivityObserver(),
            clock = CircumferenceRestorationClock,
            timeZone = TimeZone.UTC,
            savedState = SavedStateHandle(),
        )
    }

    private fun circumferenceModel(state: SavedStateHandle) = CircumferencesViewModel(
        repository = repository,
        validate = ValidateCircumference(),
        waistHipRatio = WaistHipRatio(),
        connectivity = FakeConnectivityObserver(),
        clock = CircumferenceRestorationClock,
        timeZone = TimeZone.UTC,
        savedState = state,
    )

    @Composable
    private fun Host(state: SavedStateHandle) {
        val weight = remember { weightModel() }
        val circumferences = remember { circumferenceModel(state) }
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

    private fun editableText(tag: String): String =
        rule.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun setWheel(tag: String, value: Float) {
        rule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
        rule.waitForIdle()
    }

    @Test
    fun addSheet_keepsSiteValueDateAndTime_afterRecreation() {
        val savedState = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { Host(savedState) }
        rule.waitForIdle()
        tap("circumference_add_button")
        tap("circumference_sheet_site_field")
        tap("circumference_sheet_site_option_hip")
        rule.onNodeWithTag("circumference_sheet_value_input").performTextReplacement("98,5")
        tap("circumference_sheet_date_prev")
        setWheel("circumference_sheet_hour", 8f)
        setWheel("circumference_sheet_minute", 15f)

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("circumference_sheet_title").assertTextEquals("Añadir medida")
        rule.onNodeWithTag("circumference_sheet_site_field").assertTextContains("Cadera", substring = true)
        assertEquals("98,5", editableText("circumference_sheet_value_input"))
        rule.onNodeWithTag("circumference_sheet_date_text").assertTextEquals("Ayer")
        rule.onNodeWithTag("circumference_sheet_time_value").assertTextEquals("08:15")
    }

    @Test
    fun editSheet_keepsRowValueDateAndTime_afterRecreation() {
        val savedState = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { Host(savedState) }
        rule.waitForIdle()
        tap("circumference_row_w")
        rule.onNodeWithTag("circumference_sheet_value_input").performTextReplacement("90,0")
        setWheel("circumference_sheet_hour", 9f)

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("circumference_sheet_title").assertTextEquals("Editar medida")
        rule.onNodeWithTag("circumference_sheet_site_readonly").assertTextEquals("Cintura")
        assertEquals("90,0", editableText("circumference_sheet_value_input"))
        rule.onNodeWithTag("circumference_sheet_date_text").assertTextEquals("18 oct")
        rule.onNodeWithTag("circumference_sheet_time_value").assertTextEquals("09:30")
        rule.onNodeWithTag("circumference_sheet_delete").assertExists()
    }
}
