package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [ChatBubble]: side alignment, width cap, text and surface tokens. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class ChatBubbleTest {
    @get:Rule
    val rule = createComposeRule()

    private val longText = "palabra ".repeat(60)

    private fun show() {
        rule.setContent {
            AppTheme {
                Column(Modifier.fillMaxWidth()) {
                    ChatBubble("Hola", isQuestion = true, testTag = "q")
                    ChatBubble("Respuesta", isQuestion = false, testTag = "a")
                    ChatBubble(longText, isQuestion = false, testTag = "long")
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun bubblesShowTheirText() {
        show()

        rule.onNodeWithTag("q").assertTextEquals("Hola")
        rule.onNodeWithTag("a").assertTextEquals("Respuesta")
    }

    @Test
    fun aQuestionSitsAtTheEnd_andAnAnswerAtTheStart() {
        show()

        val question = rule.onNodeWithTag("q").getUnclippedBoundsInRoot()
        val answer = rule.onNodeWithTag("a").getUnclippedBoundsInRoot()
        assertTrue(question.left > answer.left)
        assertEquals(360.dp.value, question.right.value, 1f)
        assertEquals(0f, answer.left.value, 1f)
    }

    @Test
    fun aLongMessage_wrapsWithinTheMaximumWidth() {
        show()

        val width = rule.onNodeWithTag("long").getUnclippedBoundsInRoot().width
        assertTrue(width <= 360.dp * ChatBubbleDefaults.MAX_WIDTH_FRACTION + 1.dp)
    }

    @Test
    fun questionAndAnswerUseDifferentSurfaceTokens() {
        val colors = KineticDarkColors

        assertTrue(
            ChatBubbleDefaults.container(colors, true) != ChatBubbleDefaults.container(colors, false),
        )
        assertEquals(colors.surfaceOverlay, ChatBubbleDefaults.container(colors, true))
        assertEquals(colors.surfaceRaised, ChatBubbleDefaults.container(colors, false))
    }
}
