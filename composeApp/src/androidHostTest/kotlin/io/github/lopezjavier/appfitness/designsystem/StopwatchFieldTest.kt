package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The set stopwatch: time as mm:ss, a start/stop button and inline manual entry. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per behaviour.
class StopwatchFieldTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(
        seconds: Int = 90,
        running: Boolean = false,
        readOnly: Boolean = false,
        onToggle: () -> Unit = {},
        onManual: (Int) -> Unit = {},
    ) {
        rule.setContent {
            AppTheme {
                StopwatchField(seconds, running, onToggle, onManual, readOnly = readOnly, testTagPrefix = "sw")
            }
        }
    }

    private fun hasStateDescription(text: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

    @Test
    fun showsTheTimeAsMinutesAndSeconds() {
        show(seconds = 90)

        rule.onNodeWithTag("sw_time").assertTextEquals("01:30")
    }

    @Test
    fun pastOneHour_showsHoursMinutesAndSeconds() {
        show(seconds = 3_723)

        rule.onNodeWithTag("sw_time").assertTextEquals("1:02:03")
    }

    @Test
    fun stopped_speaksTheStateAndOffersStart() {
        show(running = false)

        rule.onNodeWithTag("sw_time").assert(hasStateDescription("Detenido, 01:30"))
        rule.onNodeWithTag("sw_toggle").assertContentDescriptionEquals("Iniciar cronómetro")
    }

    @Test
    fun running_speaksTheStateAndOffersStop() {
        show(running = true)

        rule.onNodeWithTag("sw_time").assert(hasStateDescription("En marcha, 01:30"))
        rule.onNodeWithTag("sw_toggle").assertContentDescriptionEquals("Detener cronómetro")
    }

    @Test
    fun theToggleAndTheTimeAreEach48DpTouchTargets() {
        show()

        rule.onNodeWithTag("sw_toggle").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("sw_time").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun tappingTheToggle_reportsIt() {
        var toggles = 0
        show(onToggle = { toggles++ })

        rule.onNodeWithTag("sw_toggle").performClick()

        assertEquals(1, toggles)
    }

    @Test
    fun typingMmSs_commitsTheSeconds() {
        val committed = mutableListOf<Int>()
        show(seconds = 0, onManual = { committed += it })

        rule.onNodeWithTag("sw_time").performClick()
        rule.onNodeWithTag("sw_input").performTextInput("1:30")
        rule.onNodeWithTag("sw_input").performImeAction()
        rule.waitForIdle()

        assertEquals(listOf(90), committed)
        rule.onNodeWithTag("sw_input").assertDoesNotExist()
    }

    @Test
    fun anInvalidTime_showsAnInlineError_andDoesNotCommit() {
        val committed = mutableListOf<Int>()
        show(onManual = { committed += it })

        rule.onNodeWithTag("sw_time").performClick()
        rule.onNodeWithTag("sw_input").performTextInput("1:75")
        rule.onNodeWithTag("sw_input").performImeAction()
        rule.waitForIdle()

        assertEquals(emptyList<Int>(), committed)
        rule.onNodeWithTag("sw_error").assertTextEquals("Usa el formato mm:ss")
    }

    @Test
    fun theErrorClears_whenTheUserTypesAgain() {
        show()
        rule.onNodeWithTag("sw_time").performClick()
        rule.onNodeWithTag("sw_input").performTextInput("1:75")
        rule.onNodeWithTag("sw_input").performImeAction()

        rule.onNodeWithTag("sw_input").performTextInput("0")

        rule.onNodeWithTag("sw_error").assertDoesNotExist()
    }

    @Test
    fun whileRunning_theTimeCannotBeEdited() {
        show(running = true)

        rule.onNodeWithTag("sw_time").performClick()

        rule.onNodeWithTag("sw_input").assertDoesNotExist()
    }

    @Test
    fun readOnly_blocksEditingAndToggling() {
        var toggles = 0
        show(readOnly = true, onToggle = { toggles++ })

        rule.onNodeWithTag("sw_time").performClick()
        rule.onNodeWithTag("sw_toggle").performClick()

        rule.onNodeWithTag("sw_input").assertDoesNotExist()
        assertEquals(0, toggles)
    }

    @Test
    fun theDisplayedTimeFollowsTheCaller() {
        var seconds by mutableIntStateOf(5)
        rule.setContent { AppTheme { StopwatchField(seconds, true, {}, {}, testTagPrefix = "sw") } }
        rule.onNodeWithTag("sw_time").assertTextEquals("00:05")

        seconds = 65
        rule.waitForIdle()

        rule.onNodeWithTag("sw_time").assertTextEquals("01:05")
    }
}
