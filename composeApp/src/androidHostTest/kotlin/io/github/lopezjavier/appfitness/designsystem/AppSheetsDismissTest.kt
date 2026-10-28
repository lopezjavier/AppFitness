package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.espresso.Espresso
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_edit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * R-38 / ADR-80 on every sheet archetype: no handle and no swipe dismissal; pickers, info sheets and action menus
 * close on an outside tap, the edit sheet does not; X and back close all.
 */
@Suppress("TooManyFunctions") // One method per archetype and rule.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class AppSheetsDismissTest {
    @get:Rule
    val rule = createComposeRule()

    private var dismissed = 0
    private var open by mutableStateOf(true)

    private val archetypes: Map<String, @Composable () -> Unit> = mapOf(
        "edit" to {
            AppEditSheet("T", SheetCommit.Save, {}, ::close, testTagPrefix = "sheet") { Text("c") }
        },
        "picker" to { AppPickerSheet("T", ::close, testTagPrefix = "sheet") { Text("c") } },
        "info" to { AppInfoSheet("T", ::close, testTagPrefix = "sheet") { Text("c") } },
        "action" to {
            AppActionSheet(
                actions = listOf(AppSheetAction(Res.drawable.ic_edit, "Editar", onClick = {})),
                onDismiss = ::close,
                title = "T",
                testTagPrefix = "sheet",
            )
        },
    )

    private fun close() {
        dismissed++
        open = false
    }

    private fun show(name: String) {
        rule.setContent { AppTheme { if (open) archetypes.getValue(name)() } }
        rule.waitForIdle()
    }

    private fun assertStaysOpenOnSwipeAndHasNoHandle(name: String) {
        show(name)
        rule.onNodeWithTag("sheet_handle", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("sheet_close").performTouchInput { swipeDown(startY = centerY, endY = centerY + 300f) }
        rule.waitForIdle()
        rule.onNodeWithTag("sheet").assertIsDisplayed()
        assertEquals(0, dismissed)
    }

    /**
     * Taps the dimmed area outside the sheet (ADR-80). When outside taps are enabled the scrim is a clickable node
     * ("Close sheet"); otherwise the tap lands on the inert scrim above the sheet.
     */
    private fun tapOutside() {
        val scrim = rule.onAllNodes(hasContentDescription("Close sheet", ignoreCase = true))
        if (scrim.fetchSemanticsNodes().isNotEmpty()) {
            scrim[0].performClick()
        } else {
            rule.onNodeWithTag("sheet").performTouchInput { click(Offset(width / 2f, -(height / 2f) - 40f)) }
        }
        rule.waitForIdle()
    }

    private fun assertOutsideTapDismisses(name: String) {
        show(name)
        tapOutside()
        assertEquals(1, dismissed)
    }

    private fun assertOutsideTapIgnored(name: String) {
        show(name)
        tapOutside()
        rule.onNodeWithTag("sheet").assertIsDisplayed()
        assertEquals(0, dismissed)
    }

    private fun assertCloseAndBackDismiss(name: String) {
        show(name)
        rule.onNodeWithTag("sheet_close").performClick()
        rule.waitForIdle()
        assertEquals(1, dismissed)
        open = true
        rule.waitForIdle()
        Espresso.pressBack()
        rule.waitForIdle()
        assertEquals(2, dismissed)
    }

    @Test
    fun editSheet_hasNoHandle_andIgnoresSwipe() = assertStaysOpenOnSwipeAndHasNoHandle("edit")

    @Test
    fun pickerSheet_hasNoHandle_andIgnoresSwipe() = assertStaysOpenOnSwipeAndHasNoHandle("picker")

    @Test
    fun infoSheet_hasNoHandle_andIgnoresSwipe() = assertStaysOpenOnSwipeAndHasNoHandle("info")

    @Test
    fun actionSheet_hasNoHandle_andIgnoresSwipe() = assertStaysOpenOnSwipeAndHasNoHandle("action")

    @Test
    fun editSheet_ignoresOutsideTap() = assertOutsideTapIgnored("edit")

    @Test
    fun editSheet_outsideTapKeepsTheTypedValue() {
        rule.setContent {
            AppTheme {
                AppEditSheet("T", SheetCommit.Save, {}, ::close, testTagPrefix = "sheet") {
                    var text by remember { mutableStateOf("") }
                    BasicTextField(text, { text = it }, Modifier.testTag("sheet_field"))
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("sheet_field").performTextInput("72,5")

        tapOutside()

        rule.onNodeWithTag("sheet_field").assertTextEquals("72,5")
        assertEquals(0, dismissed)
    }

    @Test
    fun pickerSheet_closesOnOutsideTap() = assertOutsideTapDismisses("picker")

    @Test
    fun infoSheet_closesOnOutsideTap() = assertOutsideTapDismisses("info")

    @Test
    fun actionSheet_closesOnOutsideTap() = assertOutsideTapDismisses("action")

    @Test
    fun editSheet_xAndBackStillClose() = assertCloseAndBackDismiss("edit")

    @Test
    fun pickerSheet_xAndBackStillClose() = assertCloseAndBackDismiss("picker")

    @Test
    fun infoSheet_xAndBackStillClose() = assertCloseAndBackDismiss("info")

    @Test
    fun actionSheet_xAndBackStillClose() = assertCloseAndBackDismiss("action")
}
