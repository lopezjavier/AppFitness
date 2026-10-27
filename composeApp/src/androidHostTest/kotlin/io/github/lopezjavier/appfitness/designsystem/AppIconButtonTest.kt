package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_arrow_back
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class AppIconButtonTest {
    @get:Rule
    val rule = createComposeRule()

    private var clicks = 0

    private fun show(enabled: Boolean = true) {
        rule.setContent {
            AppTheme {
                AppIconButton(
                    contentDescription = "Volver",
                    icon = Res.drawable.ic_arrow_back,
                    testTag = "icon_button",
                    onClick = { clicks++ },
                    enabled = enabled,
                )
            }
        }
    }

    @Test
    fun longPress_showsTheDescriptionAsATooltip() {
        show()
        rule.onAllNodesWithTag("icon_button_tooltip").assertCountEquals(0)

        rule.onNodeWithTag("icon_button").performTouchInput { longClick() }
        rule.waitForIdle()

        rule.onAllNodesWithTag("icon_button_tooltip").assertCountEquals(1)
        rule.onNodeWithTag("icon_button").assertContentDescriptionEquals("Volver")
    }

    @Test
    fun click_stillInvokesOnClick() {
        show()

        rule.onNodeWithTag("icon_button").performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun disabled_staysInertAndShowsNoTooltip() {
        show(enabled = false)

        rule.onNodeWithTag("icon_button").assertIsNotEnabled().performTouchInput { longClick() }
        rule.waitForIdle()

        assertEquals(0, clicks)
        rule.onAllNodesWithTag("icon_button_tooltip").assertCountEquals(0)
    }
}
