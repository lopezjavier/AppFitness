package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_delete
import io.github.lopezjavier.appfitness.composeapp.resources.ic_edit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** US-56 sheets: the Actions archetype (rows, tap closes, X "Cerrar"). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class AppActionSheetTest {
    @get:Rule
    val rule = createComposeRule()

    private val log = mutableListOf<String>()
    private var dismissed = 0

    private fun show(title: String? = "Opciones") {
        val rows =
            listOf(
                AppSheetAction(Res.drawable.ic_edit, "Editar", onClick = { log += "edit" }),
                AppSheetAction(Res.drawable.ic_delete, "Eliminar", onClick = { log += "delete" }),
            )
        rule.setContent {
            AppTheme {
                var open by androidx.compose.runtime.remember { mutableStateOf(true) }
                if (open) {
                    AppActionSheet(title = title, actions = rows, onDismiss = {
                        dismissed++
                        open = false
                    })
                }
            }
        }
    }

    @Test
    fun showsTitleRowsAndCloseButton() {
        show()
        rule.onNodeWithTag("action_sheet").assertIsDisplayed()
        rule.onNodeWithText("Opciones").assertIsDisplayed()
        rule.onNodeWithText("Editar").assertIsDisplayed()
        rule.onNodeWithText("Eliminar").assertIsDisplayed()
        rule.onNodeWithTag("action_sheet_close").assertContentDescriptionEquals("Cerrar")
    }

    @Test
    fun withoutTitle_stillOffersTheCloseButton() {
        show(title = null)
        rule.onNodeWithTag("action_sheet_close").assertIsDisplayed()
    }

    @Test
    fun rowTap_runsTheActionOnceThenDismisses() {
        show()
        rule.onNodeWithTag("action_sheet_row_2").performClick()
        rule.waitForIdle()
        assertEquals(listOf("delete"), log)
        assertEquals(1, dismissed)
        rule.onNodeWithTag("action_sheet").assertDoesNotExist()
    }

    @Test
    fun closeButton_dismissesWithoutRunningAnyAction() {
        show()
        rule.onNodeWithTag("action_sheet_close").performClick()
        rule.waitForIdle()
        assertTrue(log.isEmpty())
        assertEquals(1, dismissed)
    }

    @Test
    fun closeButtonAndRows_areAtLeast48dp() {
        show()
        listOf("action_sheet_close", "action_sheet_row_1", "action_sheet_row_2").forEach { tag ->
            val bounds = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue(bounds.height >= 47.5.dp, "$tag height ${bounds.height}")
        }
    }
}
