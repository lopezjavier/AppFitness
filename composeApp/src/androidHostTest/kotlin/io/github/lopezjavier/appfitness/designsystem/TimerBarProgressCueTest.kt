package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.seconds

/** The timer bars show their progress once, as the ring; there is no second line along the bottom edge. */
@RunWith(RobolectricTestRunner::class)
class TimerBarProgressCueTest {
    @get:Rule
    val rule = createComposeRule()

    /** Nodes inside the bar that are as wide as the bar itself: the removed progress line was one of them. */
    private fun fullWidthNodesInBar(): Int {
        val barWidth = rule.onNodeWithTag("bar").fetchSemanticsNode().size.width
        return rule.onAllNodes(hasAnyAncestor(hasTestTag("bar")), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .count { it.size.width >= barWidth }
    }

    @Test
    fun restBar_keepsTheCountdownAndHasNoFullWidthProgressLine() {
        rule.setContent {
            AppTheme {
                RestTimerBar(
                    remaining = 72.seconds,
                    total = 120.seconds,
                    onAddTime = {},
                    onSkip = {},
                    modifier = Modifier.testTag("bar"),
                    testTagPrefix = "rest",
                )
            }
        }

        rule.onNodeWithTag("rest_countdown").assertTextEquals("01:12")
        assertEquals(0, fullWidthNodesInBar())
    }

    @Test
    fun cardioBar_keepsTheElapsedTimeAndHasNoFullWidthProgressLine() {
        rule.setContent {
            AppTheme {
                CardioTimerBar(
                    label = "Cinta",
                    elapsed = 90.seconds,
                    target = 600.seconds,
                    paused = false,
                    onToggle = {},
                    modifier = Modifier.testTag("bar"),
                    testTagPrefix = "cardio",
                )
            }
        }

        rule.onNodeWithTag("cardio_elapsed").assertTextEquals("01:30")
        assertEquals(0, fullWidthNodesInBar())
    }
}
