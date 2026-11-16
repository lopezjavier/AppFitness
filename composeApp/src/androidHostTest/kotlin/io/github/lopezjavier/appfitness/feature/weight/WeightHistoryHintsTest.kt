package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.DayMeasurements
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

/** Historial de peso (V-92): a hint above the calendar, and a second one when the viewed month has no weigh-ins. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class WeightHistoryHintsTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate(2026, 10, 21)

    private fun content(days: Map<LocalDate, DayMeasurements>) = WeightUiState.Content(
        WeightContent(
            month = YearMonth(2026, 10),
            today = today,
            canGoNext = false,
            current = null,
            days = days,
            isOnline = true,
            sheet = null,
        ),
    )

    private fun show(state: WeightUiState) {
        rule.setContent {
            AppTheme { WeightHistoryScreen(state, onEvent = {}, onBack = {}, timeZone = TimeZone.UTC) }
        }
        rule.waitForIdle()
    }

    @Test
    fun hintAboveTheCalendar_isShown_andNoEmptyMonthHintWhenTheMonthHasWeighIns() {
        val weighIn = BodyMeasurement("m1", Instant.parse("2026-10-21T08:00:00Z"), 74.2, MeasurementSource.Manual)
        show(content(mapOf(today to DayMeasurements(weighIn, emptyList()))))

        rule.onNodeWithTag("weight_history_tap_day").assertIsDisplayed()
        rule.onNodeWithTag("weight_history_empty_month").assertDoesNotExist()
    }

    @Test
    fun aMonthWithoutWeighIns_showsTheEmptyMonthHintBesideTheCalendar() {
        show(content(emptyMap()))

        rule.onNodeWithTag("weight_history_tap_day").assertIsDisplayed()
        rule.onNodeWithTag("weight_history_calendar").assertIsDisplayed()
        rule.onNodeWithTag("weight_history_empty_month").assertIsDisplayed()
    }
}
