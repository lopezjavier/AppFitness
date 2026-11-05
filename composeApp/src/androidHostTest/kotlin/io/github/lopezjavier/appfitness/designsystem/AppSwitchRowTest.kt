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
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
                AppSwitchRow("Etiqueta", checked, { checked = it }, Modifier.testTag("row"), helper = "Ayuda")
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
                AppSwitchRow("Etiqueta", true, {
                    changes++
                }, Modifier.testTag("row"), enabled = false, helper = "Ayuda")
            }
        }

        rule.onNodeWithTag("row").assertIsNotEnabled().performClick()

        assertEquals(0, changes)
    }

    private fun showWithHelp() {
        rule.setContent {
            AppTheme {
                var checked by remember { mutableStateOf(false) }
                AppSwitchRow(
                    "Etiqueta",
                    checked,
                    { checked = it },
                    Modifier.testTag("row"),
                    help = HelpContent.fromBody("Texto de ayuda"),
                    helpContentDescription = "Ayuda: etiqueta",
                    helpTestTag = "row_help",
                )
            }
        }
    }

    @Test
    fun withHelp_showsOneQuestionMark_thatOpensTheSheet_withoutTogglingTheRow() {
        showWithHelp()

        rule.onNodeWithTag("row_help").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("row_help_sheet_body").assertExists()
        rule.onNodeWithTag("row").assertIsOff()
    }

    @Test
    fun withHelp_tapOnTheRowStillToggles_andTheHelpIsNotPartOfTheToggleSemantics() {
        showWithHelp()

        rule.onNodeWithTag("row").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("row").assertIsOn()
        rule.onAllNodesWithTag("row_help").assertCountEquals(1)
    }

    @Test
    fun withoutHelp_noQuestionMark_andNoHelperTextWhenNoneGiven() {
        rule.setContent { AppTheme { AppSwitchRow("Etiqueta", true, {}, Modifier.testTag("row")) } }

        rule.onNodeWithTag("row_help").assertDoesNotExist()
        rule.onNodeWithText("Ayuda").assertDoesNotExist()
    }

    @Test
    fun anOpenHelp_survivesRecreation() {
        val tester = StateRestorationTester(rule)
        tester.setContent {
            AppTheme {
                AppSwitchRow(
                    "Etiqueta",
                    false,
                    {},
                    Modifier.testTag("row"),
                    help = HelpContent.fromBody("Texto de ayuda"),
                    helpContentDescription = "Ayuda: etiqueta",
                    helpTestTag = "row_help",
                )
            }
        }
        rule.onNodeWithTag("row_help").performClick()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("row_help_sheet_body").assertExists()
    }
}
