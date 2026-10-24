package io.github.lopezjavier.appfitness.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** History -> training calendar -> the workout of a day, with the calendar's own top bar (US-37). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class WorkoutCalendarNavigationTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var nav: NavHostController

    private val historySlot: HistorySlot = { modifier, _, onOpenCalendar ->
        Box(modifier.fillMaxSize().testTag("history_screen")) {
            Box(Modifier.size(48.dp).testTag("stub_open_calendar").clickable(onClick = onOpenCalendar))
        }
    }
    private val calendarSlot: CalendarSlot = { modifier, onOpenSession ->
        Box(modifier.fillMaxSize().testTag("calendar_screen")) {
            Box(Modifier.size(48.dp).testTag("stub_open_session").clickable { onOpenSession("s1") })
        }
    }
    private val sessionDetailSlot: SessionDetailSlot = { modifier, sessionId, _, _ ->
        Text(sessionId, modifier.testTag("opened_session_id"))
    }

    private fun showHistory() {
        rule.setContent {
            AppTheme {
                nav = rememberNavController()
                AppScaffold(
                    navController = nav,
                    history = historySlot,
                    calendar = calendarSlot,
                    sessionDetail = sessionDetailSlot,
                )
            }
        }
        rule.runOnUiThread { nav.navigateToFullScreen(HistoryRoute) }
        rule.waitForIdle()
    }

    @Test
    fun historyCalendarEntry_opensTheCalendar_withItsOwnTopBar_andBackReturnsToHistory() {
        showHistory()

        rule.onNodeWithTag("stub_open_calendar").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("calendar_screen").assertExists()
        rule.onNodeWithTag("calendar_top_bar_title").assertTextEquals("Calendario")
        rule.onNodeWithTag("nav_bar").assertDoesNotExist()
        rule.onNodeWithTag("calendar_top_bar_back").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("history_screen").assertExists()
    }

    @Test
    fun tappingATrainedDay_opensThatWorkout() {
        showHistory()
        rule.onNodeWithTag("stub_open_calendar").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("stub_open_session").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("opened_session_id").assertTextEquals("s1")
    }
}
