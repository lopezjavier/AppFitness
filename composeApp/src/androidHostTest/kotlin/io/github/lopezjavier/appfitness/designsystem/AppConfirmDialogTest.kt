package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class AppConfirmDialogTest {
    @get:Rule
    val rule = createComposeRule()

    private var confirms = 0
    private var dismisses = 0

    private fun show() {
        confirms = 0
        dismisses = 0
        rule.setContent {
            AppTheme {
                AppConfirmDialog(
                    title = "¿Cerrar sesión?",
                    message = "Tendrás que volver a entrar con tu cuenta de Google.",
                    confirmLabel = "Cerrar sesión",
                    cancelLabel = "Cancelar",
                    onConfirm = { confirms++ },
                    onDismiss = { dismisses++ },
                    testTagPrefix = "test_dialog",
                )
            }
        }
    }

    @Test
    fun showsTitleMessageAndBothButtons() {
        show()
        rule.onNodeWithTag("test_dialog").assertExists()
        rule.onNodeWithText("¿Cerrar sesión?").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        rule.onNodeWithText("Tendrás que volver a entrar con tu cuenta de Google.").assertExists()
        rule.onNodeWithTag("test_dialog_cancel").assertTextEquals("Cancelar")
        rule.onNodeWithTag("test_dialog_confirm").assertTextEquals("Cerrar sesión")
    }

    @Test
    fun buttons_meetTheTouchTarget() {
        show()
        rule.onNodeWithTag("test_dialog_cancel").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("test_dialog_confirm").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun confirm_callsOnConfirmOnly() {
        show()
        rule.onNodeWithTag("test_dialog_confirm").performClick()
        assertEquals(1, confirms)
        assertEquals(0, dismisses)
    }

    @Test
    fun cancel_callsOnDismissOnly() {
        show()
        rule.onNodeWithTag("test_dialog_cancel").performClick()
        assertEquals(0, confirms)
        assertEquals(1, dismisses)
    }

    @Test
    fun narrowPhone_stacksTheButtons_eachLabelOnOneLine() {
        show()
        val confirm = rule.onNodeWithTag("test_dialog_confirm").getUnclippedBoundsInRoot()
        val cancel = rule.onNodeWithTag("test_dialog_cancel").getUnclippedBoundsInRoot()
        // Confirm on top, both below each other, and one text line (a wrapped label is far taller than 56 dp).
        assertTrue("confirm above cancel", confirm.bottom <= cancel.top + 0.5.dp)
        assertTrue("confirm label on one line: ${confirm.height}", confirm.height < 56.dp)
        assertTrue("cancel label on one line: ${cancel.height}", cancel.height < 56.dp)
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-mdpi")
    fun wideWindow_keepsTheButtonsSideBySide_cancelFirst() {
        show()
        val confirm = rule.onNodeWithTag("test_dialog_confirm").getUnclippedBoundsInRoot()
        val cancel = rule.onNodeWithTag("test_dialog_cancel").getUnclippedBoundsInRoot()
        assertTrue("cancel left of confirm", cancel.right <= confirm.left + 0.5.dp)
        assertTrue("confirm label on one line: ${confirm.height}", confirm.height < 56.dp)
    }
}
