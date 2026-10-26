package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
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
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

private val Sept15 = LocalDate(2026, 9, 15)

private object RestorationClock : Clock {
    override fun today(): LocalDate = LocalDate(2026, 10, 21)

    override fun now(): Instant = Instant.parse("2026-10-21T10:30:00Z")
}

/** The visible month, the open day sheet and its half-filled draft survive recreation (ADR-40). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class WeightRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val repository = FakeWeightRepository(
        BodyMeasurement("a", Instant.parse("2026-09-10T07:30:00Z"), 75.2, MeasurementSource.Manual),
    )

    private fun viewModel(state: SavedStateHandle) = WeightViewModel(
        repository = repository,
        measurementsByDay = MeasurementsByDay(),
        saveBodyMeasurement = SaveBodyMeasurement(repository, RestorationClock, TimeZone.UTC),
        validate = ValidateBodyMeasurement(),
        connectivity = FakeConnectivityObserver(),
        clock = RestorationClock,
        timeZone = TimeZone.UTC,
        savedState = state,
    )

    @Composable
    private fun Host(model: WeightViewModel) {
        val state by model.uiState.collectAsState()
        AppTheme {
            WeightHistoryScreen(state, model::onEvent, {}, timeZone = TimeZone.UTC)
        }
    }

    @Composable
    private fun PesoHost(model: WeightViewModel) {
        val state by model.uiState.collectAsState()
        AppTheme {
            WeightScreen(state, model::onEvent, CircumferencesUiState.Loading, {}, timeZone = TimeZone.UTC)
        }
    }

    @Test
    fun todaysSheetOnPeso_surviveRecreationWithItsDraft() {
        val savedState = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { PesoHost(viewModel(savedState)) }
        rule.waitForIdle()
        rule.onNodeWithTag("weight_edit_today_button").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("weight_sheet_weight_input").performTextReplacement("74,6")
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("weight_day_sheet_title").assertTextEquals("Registrar peso")
        assertEquals("74,6", editableText("weight_sheet_weight_input"))
    }

    @Test
    fun monthOpenDayAndDraft_surviveRecreationFromTheSameSavedState() {
        val savedState = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { Host(viewModel(savedState)) }
        rule.waitForIdle()
        rule.onNodeWithTag("month_calendar_prev").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("month_calendar_day_$Sept15").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("weight_sheet_weight_input").performTextReplacement("74,6")
        rule.onNodeWithTag("weight_sheet_fat_input").performTextReplacement("16,2")
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("month_calendar_title").assertTextEquals("septiembre 2026")
        rule.onNodeWithTag("weight_day_sheet_title").assertTextEquals("Peso del 15 sep")
        assertEquals("74,6", editableText("weight_sheet_weight_input"))
        assertEquals("16,2", editableText("weight_sheet_fat_input"))
    }

    @Test
    fun recreatedViewModel_restoresMonthOpenDayAndDraftFromTheSavedState() {
        val savedState = SavedStateHandle()
        rule.setContent { Host(viewModel(savedState)) }
        rule.waitForIdle()
        rule.onNodeWithTag("month_calendar_prev").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("month_calendar_day_$Sept15").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("weight_sheet_weight_input").performTextReplacement("74,6")
        rule.waitForIdle()

        val recreated = viewModel(savedState)
        rule.waitForIdle()

        val content = (recreated.uiState.value as? WeightUiState.Content)?.content
            ?: error("Expected loaded content, was ${recreated.uiState.value}")
        assertEquals(Sept15, content.sheet?.day)
        assertEquals("74,6", content.sheet?.form?.weightText)
        assertEquals(YearMonth(2026, 9), content.month)
    }

    private fun editableText(tag: String): String =
        rule.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()
}
