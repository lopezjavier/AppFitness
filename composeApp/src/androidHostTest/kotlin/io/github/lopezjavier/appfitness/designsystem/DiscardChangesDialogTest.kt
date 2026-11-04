package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** R-14: the one shared discard dialog, with the default and the overridden tags. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class DiscardChangesDialogTest {
    @get:Rule
    val rule = createComposeRule()

    private var confirms = 0
    private var dismisses = 0

    private fun show(confirmTag: String? = null) {
        rule.setContent {
            AppTheme {
                if (confirmTag == null) {
                    DiscardChangesDialog({ confirms++ }, { dismisses++ }, testTagPrefix = "d")
                } else {
                    DiscardChangesDialog({ confirms++ }, { dismisses++ }, "d", confirmTestTag = confirmTag)
                }
            }
        }
    }

    @Test
    fun showsTheSharedCopy() {
        show()

        rule.onNodeWithText("¿Descartar los cambios?").assertExists()
        rule.onNodeWithText("Los cambios que no hayas guardado se perderán.").assertExists()
        rule.onNodeWithText("Descartar").assertExists()
        rule.onNodeWithText("Seguir editando").assertExists()
    }

    @Test
    fun defaultTags_confirmAndCancel() {
        show()

        rule.onNodeWithTag("d_confirm").performClick()
        rule.onNodeWithTag("d_cancel").performClick()

        assertEquals(1, confirms)
        assertEquals(1, dismisses)
    }

    @Test
    fun anOverriddenConfirmTag_replacesTheDefault() {
        show(confirmTag = "custom_confirm")

        rule.onNodeWithTag("custom_confirm").performClick()

        assertEquals(1, confirms)
        rule.onNodeWithTag("d_confirm").assertDoesNotExist()
    }
}
