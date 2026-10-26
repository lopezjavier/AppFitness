package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** US-56 Picker and Info archetypes, the window-free layout (pinned footer, tablet cap) and the header at 1.3x. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class AppReadOnlySheetsTest {
    @get:Rule
    val rule = createComposeRule()

    private var dismissed = 0
    private var picks = 0
    private var open by mutableStateOf(true)

    private fun showPicker() {
        rule.setContent {
            AppTheme {
                if (open) {
                    AppPickerSheet(
                        title = "Elige un tipo",
                        onDismiss = {
                            dismissed++
                            open = false
                        },
                        testTagPrefix = "sheet",
                    ) {
                        Box(Modifier.fillMaxWidth().height(60.dp).testTag("sheet_option").clickable { picks++ })
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun showInfo() {
        rule.setContent {
            AppTheme {
                if (open) {
                    AppInfoSheet(
                        title = "Ayuda",
                        onDismiss = {
                            dismissed++
                            open = false
                        },
                        testTagPrefix = "sheet",
                    ) { Text("Explicación") }
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun picker_hasNoCommitButtonAndAppliesOnTap() {
        showPicker()
        rule.onNodeWithTag("sheet_commit").assertDoesNotExist()
        rule.onNodeWithTag("sheet_option").performClick()
        assertEquals(1, picks)
        rule.onNodeWithText("Elige un tipo").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        rule.onNodeWithTag("sheet_close").assertContentDescriptionEquals("Cerrar")
    }

    @Test
    fun picker_closeDismissesOnly() {
        showPicker()
        rule.onNodeWithTag("sheet_close").performClick()
        rule.waitForIdle()
        assertEquals(1, dismissed)
        assertEquals(0, picks)
    }

    @Test
    fun info_hasNoCommitAndCloses() {
        showInfo()
        rule.onNodeWithTag("sheet_commit").assertDoesNotExist()
        rule.onNodeWithText("Explicación").assertIsDisplayed()
        rule.onNodeWithTag("sheet_close").performClick()
        rule.waitForIdle()
        assertEquals(1, dismissed)
    }

    @Composable
    private fun TallBodyWithFooter(modifier: Modifier = Modifier) {
        AppTheme {
            Box(modifier) {
                AppSheetLayout(
                    header = { AppSheetHeader("Título", {}, "layout", help = null) },
                    footer = { PrimaryButton("Guardar", {}, Modifier.fillMaxWidth().testTag("layout_commit")) },
                ) { Box(Modifier.fillMaxWidth().height(1200.dp).testTag("layout_tall")) }
            }
        }
    }

    @Test
    fun footer_staysVisibleAndBodyScrolls_whenTheKeyboardLeavesLittleRoom() {
        // Fallback named in the plan: a fixed-height parent stands for the 640 dp window minus a ~300 dp IME.
        rule.setContent { TallBodyWithFooter(Modifier.height(340.dp)) }
        rule.onNodeWithTag("layout_commit").assertIsDisplayed()
        val bounds = rule.onNodeWithTag("layout_commit").getUnclippedBoundsInRoot()
        assertTrue(bounds.bottom <= 340.dp, "footer bottom ${bounds.bottom}")
        rule.onNode(hasScrollAction()).assertExists()
    }

    @Test
    @Config(qualifiers = "w800dp-h640dp-xxhdpi")
    fun content_isCappedToTheFormWidthOnTablets() {
        rule.setContent { TallBodyWithFooter(Modifier.fillMaxWidth()) }
        val width = rule.onNodeWithTag("layout_tall").getUnclippedBoundsInRoot().width
        assertTrue(width <= WindowWidthClassDefaults.FormContentMaxWidth, "body width $width")
    }

    @Test
    fun closeButton_keepsItsTargetAtFontScale13WithTheHelpButton() {
        rule.setContent {
            AppTheme {
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, 1.3f)) {
                    var shown by remember { mutableStateOf(true) }
                    if (shown) {
                        AppPickerSheet(
                            title = "Tipo de serie con un título bastante largo para forzar dos líneas",
                            onDismiss = { shown = false },
                            help = { HelpButton("Ayuda: tipo", "Tipo", "Texto", "help") },
                            testTagPrefix = "sheet",
                        ) { Text("Contenido") }
                    }
                }
            }
        }
        rule.waitForIdle()
        val close = rule.onNodeWithTag("sheet_close").getUnclippedBoundsInRoot()
        assertTrue(close.width >= 47.5.dp && close.height >= 47.5.dp, "X is $close")
        assertTrue(close.right <= 360.dp, "X right edge ${close.right}")
        rule.onNodeWithTag("sheet_close").assertIsDisplayed()
        val help = rule.onNodeWithTag("help").getUnclippedBoundsInRoot()
        assertTrue(help.right <= close.left, "help must stay left of the X")
    }
}
