package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/** US-56 feedback: the message host (R-28), above a pinned footer, polite live region, no action. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class AppMessageHostTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var messages: AppMessages

    /** Same wiring as the shell: a footer state, the message queue and the host over an `AppScreen`. */
    private fun show(withFooter: Boolean) {
        rule.setContent {
            AppTheme {
                val scope = rememberCoroutineScope()
                messages = remember { AppMessages(scope) }
                val footerState = remember { PinnedFooterState() }
                CompositionLocalProvider(LocalAppMessages provides messages, LocalPinnedFooter provides footerState) {
                    Box(Modifier.fillMaxSize()) {
                        AppScreen(
                            title = "Editor",
                            footer = if (withFooter) {
                                (
                                    {
                                        Box(Modifier.fillMaxWidth().height(72.dp).testTag("footer"))
                                    }
                                    )
                            } else {
                                null
                            },
                        ) {}
                        AppMessageHost(Modifier.align(Alignment.BottomCenter))
                    }
                }
            }
        }
    }

    @Test
    fun noMessage_rendersNothing() {
        show(withFooter = false)
        rule.onNodeWithTag("app_message").assertDoesNotExist()
    }

    @Test
    fun message_isShownAsPoliteLiveRegionWithoutAnyButton() {
        show(withFooter = false)
        rule.runOnUiThread { messages.show("Guardado") }
        rule.waitForIdle()
        rule.onNodeWithTag("app_message").assertIsDisplayed().assertTextEquals("Guardado")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assert(SemanticsMatcher.keyNotDefined(androidx.compose.ui.semantics.SemanticsActions.OnClick))
    }

    @Test
    fun message_sitsAbovePinnedFooter() {
        show(withFooter = true)
        rule.runOnUiThread { messages.show("Guardado") }
        rule.waitForIdle()
        val message = rule.onNodeWithTag("app_message").getUnclippedBoundsInRoot()
        val footer = rule.onNodeWithTag("footer").getUnclippedBoundsInRoot()
        assertTrue(message.bottom <= footer.top + 0.5.dp, "message bottom ${message.bottom} footer top ${footer.top}")
    }

    @Test
    fun message_autoDismissesAfterTheDuration() {
        show(withFooter = false)
        rule.runOnUiThread { messages.show("Guardado") }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(AppMessagesDefaults.DurationMillis + 100)
        rule.waitForIdle()
        rule.onNodeWithTag("app_message").assertDoesNotExist()
    }

    @Test
    fun secondMessage_replacesTheFirstAfterIt() {
        show(withFooter = false)
        rule.runOnUiThread {
            messages.show("Uno")
            messages.show("Dos")
        }
        rule.waitForIdle()
        rule.onNodeWithTag("app_message").assertTextEquals("Uno")
        rule.mainClock.advanceTimeBy(AppMessagesDefaults.DurationMillis + 100)
        rule.waitForIdle()
        rule.onNodeWithTag("app_message").assertTextEquals("Dos")
    }
}
