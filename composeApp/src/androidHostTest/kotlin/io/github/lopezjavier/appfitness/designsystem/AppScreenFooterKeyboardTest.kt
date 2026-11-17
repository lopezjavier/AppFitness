package io.github.lopezjavier.appfitness.designsystem

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** With a pinned footer open above the keyboard, a body that pads for the keyboard must not be squeezed to nothing. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class AppScreenFooterKeyboardTest {
    private companion object {
        const val KEYBOARD_PX = 600f
    }

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun showKeyboard(heightPx: Int) {
        rule.runOnUiThread {
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, heightPx))
                .setVisible(WindowInsetsCompat.Type.ime(), true)
                .build()
            ViewCompat.dispatchApplyWindowInsets(rule.activity.window.decorView, insets)
        }
        rule.waitForIdle()
    }

    @Test
    fun bodyWithImePadding_endsWhereTheFooterStarts_notAKeyboardHeightEarlier() {
        rule.setContent {
            AppTheme {
                AppScreen(
                    title = "Entreno",
                    footer = { Box(Modifier.height(56.dp)) },
                    testTagPrefix = "t",
                ) { Box(Modifier.fillMaxSize().imePadding().testTag("body")) }
            }
        }
        showKeyboard(KEYBOARD_PX.toInt())

        val bodyBottom = rule.onNodeWithTag("body").fetchSemanticsNode().boundsInRoot.bottom
        val footerTop = rule.onNodeWithTag("t_footer").fetchSemanticsNode().boundsInRoot.top
        val rootBottom = rule.onRoot().fetchSemanticsNode().boundsInRoot.bottom
        assertTrue("the footer is lifted above the keyboard", footerTop <= rootBottom - KEYBOARD_PX)
        assertEquals(footerTop, bodyBottom, 1f)
    }
}
