package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class AppSwitchTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(initial: Boolean, enabled: Boolean = true) {
        rule.setContent {
            AppTheme {
                var checked by remember { mutableStateOf(initial) }
                AppSwitch(
                    checked = checked,
                    onCheckedChange = { checked = it },
                    contentDescription = "Desayuno",
                    stateDescription = if (checked) "Activada" else "Desactivada",
                    enabled = enabled,
                    modifier = Modifier.testTag("switch"),
                )
            }
        }
    }

    private fun config() = rule.onNodeWithTag("switch").fetchSemanticsNode().config

    @Test
    fun exposesSwitchRoleToggleStateAndStateDescription() {
        show(initial = true)

        assertEquals(Role.Switch, config().getOrNull(SemanticsProperties.Role))
        assertEquals(ToggleableState.On, config().getOrNull(SemanticsProperties.ToggleableState))
        assertEquals("Activada", config().getOrNull(SemanticsProperties.StateDescription))
    }

    @Test
    fun tapToggles_andTheDescriptionFollows() {
        show(initial = true)

        rule.onNodeWithTag("switch").performClick()
        rule.waitForIdle()

        assertEquals(ToggleableState.Off, config().getOrNull(SemanticsProperties.ToggleableState))
        assertEquals("Desactivada", config().getOrNull(SemanticsProperties.StateDescription))
    }

    @Test
    fun disabledSwitch_isInertAndKeepsItsState() {
        show(initial = true, enabled = false)

        rule.onNodeWithTag("switch").assertIsNotEnabled().performClick()
        rule.waitForIdle()

        assertEquals(ToggleableState.On, config().getOrNull(SemanticsProperties.ToggleableState))
    }

    @Test
    fun enabledSwitch_hasA48DpTouchTarget() {
        show(initial = false)

        rule.onNodeWithTag("switch").assertIsEnabled().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
    }
}
