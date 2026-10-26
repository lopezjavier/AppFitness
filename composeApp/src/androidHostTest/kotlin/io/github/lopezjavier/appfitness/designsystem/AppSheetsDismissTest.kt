package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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

/** R-38 on every sheet archetype: no handle, no swipe or outside dismissal; X and back still close. */
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
        rule.onNodeWithContentDescription("Close Sheet", ignoreCase = true).assertDoesNotExist()
        rule.onNodeWithTag("sheet").performTouchInput { swipeDown() }
        rule.waitForIdle()
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
    fun editSheet_hasNoHandle_andIgnoresSwipeAndScrim() = assertStaysOpenOnSwipeAndHasNoHandle("edit")

    @Test
    fun pickerSheet_hasNoHandle_andIgnoresSwipeAndScrim() = assertStaysOpenOnSwipeAndHasNoHandle("picker")

    @Test
    fun infoSheet_hasNoHandle_andIgnoresSwipeAndScrim() = assertStaysOpenOnSwipeAndHasNoHandle("info")

    @Test
    fun actionSheet_hasNoHandle_andIgnoresSwipeAndScrim() = assertStaysOpenOnSwipeAndHasNoHandle("action")

    @Test
    fun editSheet_xAndBackStillClose() = assertCloseAndBackDismiss("edit")

    @Test
    fun pickerSheet_xAndBackStillClose() = assertCloseAndBackDismiss("picker")

    @Test
    fun infoSheet_xAndBackStillClose() = assertCloseAndBackDismiss("info")

    @Test
    fun actionSheet_xAndBackStillClose() = assertCloseAndBackDismiss("action")
}
