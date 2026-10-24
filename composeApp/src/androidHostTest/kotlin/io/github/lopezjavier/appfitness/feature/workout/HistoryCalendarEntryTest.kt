package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The "Calendario" entry of the history (US-37). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class HistoryCalendarEntryTest {
    @get:Rule
    val rule = createComposeRule()

    private var opened = 0

    private fun show(state: HistoryUiState) {
        rule.setContent { AppTheme { HistoryScreen(state, {}, onOpenCalendar = { opened++ }) } }
        rule.waitForIdle()
    }

    @Test
    fun calendarIcon_isLabelledBigEnough_andOpensTheCalendar() {
        show(HistoryUiState.Empty)

        rule.onNodeWithTag("history_calendar").assert(hasContentDescription("Calendario"))
        assertTrue(rule.onNodeWithTag("history_calendar").getBoundsInRoot().height >= 48.dp)
        rule.onNodeWithTag("history_calendar").performClick()

        assertEquals(1, opened)
    }

    @Test
    fun calendarIcon_isAvailableWhileLoading() {
        show(HistoryUiState.Loading)

        rule.onNodeWithTag("history_calendar").assertExists()
    }
}
