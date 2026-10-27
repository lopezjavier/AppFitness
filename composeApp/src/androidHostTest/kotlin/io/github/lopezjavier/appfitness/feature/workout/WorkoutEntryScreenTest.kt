package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-56 "Same structure on every screen" for the Entreno tab root: the compact header (R-03, R-04, C3). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class WorkoutEntryScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private var openedHistory = 0

    private fun show() {
        rule.setContent {
            AppTheme {
                WorkoutEntryHost(
                    state = WorkoutEntryState.Routines,
                    onOpenLiveSession = {},
                    onOpenHistory = { openedHistory++ },
                ) { modifier, _ -> Box(modifier.fillMaxSize()) }
            }
        }
    }

    @Test
    fun header_showsRutinasOnce_andNotMisRutinas() {
        show()

        rule.onAllNodesWithText("Rutinas").assertCountEquals(1)
        rule.onNodeWithText("Rutinas").assertIsDisplayed()
        rule.onAllNodesWithText("Mis rutinas").assertCountEquals(0)
    }

    @Test
    fun calendarAction_isDescribedAsHistorialDeEntrenos_andOpensTheHistory() {
        show()

        rule.onNodeWithTag("workout_entry_history").assert(hasContentDescription("Historial de entrenos"))
        rule.onNodeWithTag("workout_entry_history").performClick()

        assertEquals(1, openedHistory)
    }

    @Test
    fun historialGhostButton_isGone() {
        show()

        rule.onAllNodesWithText("Historial").assertCountEquals(0)
        rule.onAllNodes(hasTestTag("workout_history")).assertCountEquals(0)
    }

    @Test
    fun headerText_isAHeading() {
        show()

        rule.onNode(hasText("Rutinas") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .assertIsDisplayed()
    }
}
