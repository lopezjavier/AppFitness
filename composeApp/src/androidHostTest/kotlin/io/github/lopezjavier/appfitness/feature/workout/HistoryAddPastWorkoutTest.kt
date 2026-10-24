package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
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

/** The "+" of the history top row opens the source chooser for a past workout (US-37). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class HistoryAddPastWorkoutTest {
    @get:Rule
    val rule = createComposeRule()

    private var taps = 0

    private fun show(state: HistoryUiState) {
        rule.setContent { AppTheme { HistoryScreen(state, {}, onAddPastWorkout = { taps++ }) } }
        rule.waitForIdle()
    }

    @Test
    fun theAddAction_isLabelled_atLeast48dp_andOpensTheChooser() {
        show(HistoryUiState.Empty)

        rule.onNodeWithTag("history_add_past_workout").assert(hasContentDescription("Registrar entreno pasado"))
        assertTrue(rule.onNodeWithTag("history_add_past_workout").getBoundsInRoot().height >= 48.dp)
        rule.onNodeWithTag("history_add_past_workout").performClick()

        assertEquals(1, taps)
    }

    @Test
    fun theAddAction_isAvailableInEveryState() {
        show(HistoryUiState.Loading)
        rule.onNodeWithTag("history_add_past_workout").assertExists()
    }
}
