package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.espresso.Espresso
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [HelpButton] on the Info archetype: structured content, closing paths and restoration (R-32, ADR-40). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class HelpButtonTest {
    @get:Rule
    val rule = createComposeRule()

    private val content = HelpContent(
        paragraphs = listOf("Primer párrafo", "Segundo párrafo"),
        bullets = listOf("Primera viñeta", "Segunda viñeta"),
    )

    private fun open(tester: StateRestorationTester? = null) {
        val body: @Composable () -> Unit = {
            AppTheme { HelpButton("Ayuda: RPE", "RPE", content, "help") }
        }
        if (tester != null) tester.setContent(body) else rule.setContent(body)
        rule.onNodeWithTag("help").performClick()
        rule.waitForIdle()
    }

    @Test
    fun opening_showsTheHeadingParagraphsAndBullets() {
        open()
        rule.onNodeWithText("RPE").assertIsDisplayed()
        rule.onNodeWithText("Primer párrafo", substring = true, useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("Segundo párrafo", substring = true, useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("help_sheet_bullet_1", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("help_sheet_bullet_2", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("help_sheet_bullet_3", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun theBodyOverload_showsASingleParagraphAndNoBullets() {
        rule.setContent { AppTheme { HelpButton("Ayuda: RPE", "RPE", "Solo texto", "help") } }
        rule.onNodeWithTag("help").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Solo texto", substring = true, useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("help_sheet_bullet_1", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun closeButton_isDescribedCerrarAndCloses() {
        open()
        rule.onNodeWithTag("help_sheet_close").assertContentDescriptionEquals("Cerrar").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("help_sheet").assertDoesNotExist()
    }

    @Test
    fun back_closes() {
        open()
        Espresso.pressBack()
        rule.waitForIdle()
        rule.onNodeWithTag("help_sheet").assertDoesNotExist()
    }

    @Test
    fun scrimTap_closes() {
        open()
        rule.onNodeWithTag("help_sheet").assertIsDisplayed()
        rule.onNodeWithContentDescription("Close Sheet", ignoreCase = true).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("help_sheet").assertDoesNotExist()
    }

    @Test
    fun swipeDown_closes() {
        open()
        rule.onNodeWithTag("help_sheet").performTouchInput { swipeDown() }
        rule.waitForIdle()
        rule.onNodeWithTag("help_sheet").assertDoesNotExist()
    }

    @Test
    fun openState_survivesRecreation() {
        val tester = StateRestorationTester(rule)
        open(tester)

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("help_sheet").assertIsDisplayed()
    }
}
