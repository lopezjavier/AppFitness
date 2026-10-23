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
class AppToggleChipTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(initial: Boolean) {
        rule.setContent {
            AppTheme {
                var checked by remember { mutableStateOf(initial) }
                AppToggleChip(
                    label = "2,5",
                    checked = checked,
                    onCheckedChange = { checked = it },
                    contentDescription = "Disco de 2,5 kg",
                    modifier = Modifier.testTag("chip"),
                )
            }
        }
    }

    private fun config() = rule.onNodeWithTag("chip").fetchSemanticsNode().config

    @Test
    fun exposesCheckboxRoleToggleStateAndStateDescription() {
        show(initial = true)

        assertEquals(Role.Checkbox, config().getOrNull(SemanticsProperties.Role))
        assertEquals(ToggleableState.On, config().getOrNull(SemanticsProperties.ToggleableState))
        assertEquals("Activado", config().getOrNull(SemanticsProperties.StateDescription))
    }

    @Test
    fun tapping_togglesAndUpdatesTheStateDescription() {
        show(initial = false)
        assertEquals("Desactivado", config().getOrNull(SemanticsProperties.StateDescription))

        rule.onNodeWithTag("chip").performClick()

        assertEquals(ToggleableState.On, config().getOrNull(SemanticsProperties.ToggleableState))
        assertEquals("Activado", config().getOrNull(SemanticsProperties.StateDescription))
    }

    @Test
    fun speaksTheContentDescriptionInsteadOfTheLabel() {
        show(initial = false)

        assertEquals(listOf("Disco de 2,5 kg"), config().getOrNull(SemanticsProperties.ContentDescription))
    }

    @Test
    fun touchTargetIs48dp() {
        show(initial = false)

        rule.onNodeWithTag("chip").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
    }
}
