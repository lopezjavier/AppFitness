package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.centerY
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.right
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeWithVelocity
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class SwipeToDeleteRowTest {
    @get:Rule
    val rule = createComposeRule()

    private var deletes = 0

    private fun show(enabled: Boolean = true) {
        deletes = 0
        rule.setContent {
            AppTheme {
                SwipeToDeleteRow(
                    deleteLabel = "Eliminar Avena",
                    enabled = enabled,
                    onDelete = { deletes++ },
                    backgroundTag = "row_delete_background",
                    modifier = Modifier.testTag("row"),
                ) {
                    Box(Modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget).testTag("content")) {
                        Text("Avena")
                    }
                }
            }
        }
    }

    @Test
    fun fullSwipeToTheEndDeletesOnce() {
        show()

        rule.onNodeWithTag("row").performTouchInput { swipeLeft(startX = right - 1f, endX = 0f) }
        rule.waitForIdle()

        assertEquals(1, deletes)
    }

    @Test
    fun slowFullDragToTheEndDeletesOnce() {
        show()

        rule.onNodeWithTag("row").performTouchInput {
            down(Offset(right - 1f, centerY))
            moveTo(Offset(right * 0.5f, centerY))
            moveTo(Offset(1f, centerY))
            up()
        }
        rule.waitForIdle()

        assertEquals(1, deletes)
    }

    @Test
    fun shortSlowDragSnapsBackWithoutDeleting() {
        show()

        rule.onNodeWithTag("row").performTouchInput {
            down(Offset(right - 1f, centerY))
            moveTo(Offset(right * 0.55f, centerY))
            up()
        }
        rule.waitForIdle()

        assertEquals(0, deletes)
        assertEquals(0f, rule.onNodeWithTag("content").fetchSemanticsNode().boundsInRoot.left, 0.5f)
    }

    @Test
    fun shortFastFlingDoesNotDelete() {
        show()

        rule.onNodeWithTag("row").performTouchInput {
            swipeWithVelocity(
                start = Offset(right - 1f, centerY),
                end = Offset(right * 0.6f, centerY),
                endVelocity = 5000f,
            )
        }
        rule.waitForIdle()

        assertEquals(0, deletes)
    }

    @Test
    fun swipeInTheOtherDirectionDoesNotDelete() {
        show()

        rule.onNodeWithTag("row").performTouchInput { swipeRight() }
        rule.waitForIdle()

        assertEquals(0, deletes)
    }

    @Test
    fun disabledRowNeverDeletes() {
        show(enabled = false)

        rule.onNodeWithTag("row").performTouchInput { swipeLeft(startX = right - 1f, endX = 0f) }
        rule.waitForIdle()

        assertEquals(0, deletes)
        val config = rule.onNodeWithTag("row").fetchSemanticsNode().config
        assertTrue(config.getOrNull(SemanticsActions.CustomActions).isNullOrEmpty())
    }

    @Test
    fun accessibilityActionDeletesWithoutSwiping() {
        show()

        val actions = rule.onNodeWithTag("row").fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions)
        val action = actions?.firstOrNull { it.label == "Eliminar Avena" }
        assertNotNull(action)
        rule.runOnUiThread { action!!.action() }

        assertEquals(1, deletes)
    }

    @Test
    fun rowIsAtLeastTheMinimumTouchTarget() {
        show()

        rule.onNodeWithTag("row").assertHeightIsAtLeast(48.dp)
    }
}
