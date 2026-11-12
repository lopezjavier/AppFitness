package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class ButtonsHostTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun primary_isAtLeast56dpTall() {
        rule.setContent { AppTheme { PrimaryButton("Guardar", onClick = {}, modifier = Modifier.testTag("p")) } }
        rule.onNodeWithTag("p").assertHeightIsAtLeast(56.dpValue())
    }

    @Test
    fun addItemButton_showsDecorativeIconAndKeepsLabelAndSingleClick() {
        var clicks = 0
        rule.setContent {
            AppTheme { AddItemButton("serie", onClick = { clicks++ }, modifier = Modifier.testTag("add")) }
        }
        rule.onNodeWithTag("app_button_leading_icon", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("Añadir serie").assertIsDisplayed()
        rule.onNodeWithTag("add").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun ghost_receivesFocusInteractionThatDrawsTheFocusBorder() {
        val source = MutableInteractionSource()
        val requester = FocusRequester()
        var focused = false
        rule.setContent {
            AppTheme {
                androidx.compose.runtime.LaunchedEffect(source) {
                    source.interactions.collect { if (it is FocusInteraction.Focus) focused = true }
                }
                GhostButton(
                    "Cancelar",
                    onClick = {},
                    interactionSource = source,
                    modifier = Modifier.focusRequester(requester).testTag("ghost"),
                )
            }
        }
        rule.runOnIdle { requester.requestFocus() }
        rule.waitForIdle()
        assertTrue(focused)
    }

    private fun Int.dpValue() = androidx.compose.ui.unit.Dp(toFloat())
}
