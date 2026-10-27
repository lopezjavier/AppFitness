package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Entreno -> Historial opens the training calendar directly (US-37, E7 backlog). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class HistoryCalendarEntryTest {
    @get:Rule
    val rule = createComposeRule()

    private val empty = WorkoutCalendarUiState.Content(
        view = WorkoutCalendarView.Month,
        month = YearMonth(2026, 10),
        year = 2026,
        markedDays = emptySet(),
        dayCounts = emptyMap(),
        selectedDay = null,
        yearCounts = emptyMap(),
        monthCounts = emptyMap(),
        canGoNext = false,
        today = LocalDate(2026, 10, 19),
    )

    @Test
    fun opening_showsTheCalendarDirectly_evenWithoutWorkouts() {
        rule.setContent { AppTheme { WorkoutCalendarScreen(empty, {}) } }

        rule.onNodeWithTag("history_title").assertTextEquals("Historial de entrenos")
        rule.onNodeWithTag("month_calendar_title").assertExists()
        rule.onNodeWithTag("history_list").assertDoesNotExist()
        rule.onNodeWithTag("history_add_past_workout").assertDoesNotExist()
        rule.onNodeWithTag("history_calendar").assertDoesNotExist()
    }

    @Test
    fun loading_isShownInsideTheScreen() {
        rule.setContent { AppTheme { WorkoutCalendarScreen(WorkoutCalendarUiState.Loading, {}) } }

        rule.onNodeWithTag("history_title").assertExists()
        rule.onNodeWithTag("calendar_loading").assertExists()
    }
}
