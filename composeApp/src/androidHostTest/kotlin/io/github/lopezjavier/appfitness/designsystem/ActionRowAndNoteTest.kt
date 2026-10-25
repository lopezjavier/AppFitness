package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_photo_camera
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [ActionRow], [InlineNote] and the optional second action of [ErrorState]. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class ActionRowAndNoteTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun actionRow_isAtLeast48dp_andClicks() {
        var clicks = 0
        rule.setContent {
            AppTheme { ActionRow(Res.drawable.ic_photo_camera, "Hacer foto", "Usa la cámara", { clicks++ }, "row") }
        }

        rule.onNodeWithTag("row").assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun inlineNote_showsItsText() {
        rule.setContent { AppTheme { InlineNote("Estimado por IA · revisa los valores", testTag = "note") } }

        rule.onNodeWithTag("note").assertTextContains("Estimado por IA · revisa los valores")
    }

    @Test
    fun errorState_withASecondaryAction_showsBothButtons_andEachClicksOnItsOwn() {
        var primary = 0
        var secondary = 0
        rule.setContent {
            AppTheme {
                ErrorState(
                    message = "Fallo",
                    action = AppStateAction("Reintentar") { primary++ },
                    secondaryAction = AppStateAction("Crear manualmente") { secondary++ },
                    testTagPrefix = "err",
                )
            }
        }

        rule.onNodeWithTag("err_secondary_action").performClick()
        rule.onNodeWithTag("err_action").performClick()

        assertEquals(1, primary)
        assertEquals(1, secondary)
    }

    @Test
    fun errorState_withoutASecondaryAction_hasNoSecondaryButton() {
        rule.setContent { AppTheme { ErrorState("Fallo", AppStateAction("Reintentar") {}, testTagPrefix = "err") } }

        rule.onNodeWithTag("err_secondary_action").assertDoesNotExist()
    }
}
