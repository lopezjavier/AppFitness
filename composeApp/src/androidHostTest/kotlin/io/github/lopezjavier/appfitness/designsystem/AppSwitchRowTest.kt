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
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
class AppSwitchRowTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(initial: Boolean) {
        rule.setContent {
            AppTheme {
                var checked by remember { mutableStateOf(initial) }
                AppSwitchRow("Etiqueta", "Ayuda", checked, { checked = it }, Modifier.testTag("row"))
            }
        }
    }

    private fun config() = rule.onNodeWithTag("row").fetchSemanticsNode().config

    @Test
    fun showsLabelAndHelper_withSwitchRoleAndAtLeast48dp() {
        show(initial = false)

        rule.onNodeWithText("Etiqueta").assertExists()
        rule.onNodeWithText("Ayuda").assertExists()
        rule.onNodeWithTag("row").assertIsOff().assertHeightIsAtLeast(48.dp)
        assertEquals(Role.Switch, config().getOrNull(SemanticsProperties.Role))
    }

    @Test
    fun tapOnTheRow_toggles_andTheStateDescriptionFollows() {
        show(initial = false)
        assertEquals("Desactivado", config().getOrNull(SemanticsProperties.StateDescription))

        rule.onNodeWithTag("row").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("row").assertIsOn()
        assertEquals("Activado", config().getOrNull(SemanticsProperties.StateDescription))
    }

    @Test
    fun disabledRow_ignoresTaps() {
        var changes = 0
        rule.setContent {
            AppTheme {
                AppSwitchRow("Etiqueta", "Ayuda", true, { changes++ }, Modifier.testTag("row"), enabled = false)
            }
        }

        rule.onNodeWithTag("row").assertIsNotEnabled().performClick()

        assertEquals(0, changes)
    }
}
