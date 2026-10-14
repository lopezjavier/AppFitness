package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-05 "Sign out": a confirmation dialog guards the action. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class ProfileScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private var signOuts = 0

    private fun show() {
        signOuts = 0
        rule.mainClock.autoAdvance = false
        rule.setContent { AppTheme { ProfileScreen(onSignOut = { signOuts++ }) } }
        rule.mainClock.advanceTimeByFrame()
    }

    @Test
    fun signOutButton_isShown_andMeetsTheTouchTarget() {
        show()
        rule.onNodeWithTag("profile_sign_out_button").assertTextEquals("Cerrar sesión").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("sign_out_dialog").assertDoesNotExist()
    }

    @Test
    fun tappingSignOut_opensTheDialogWithoutSigningOut() {
        show()
        rule.onNodeWithTag("profile_sign_out_button").performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("sign_out_dialog").assertExists()
        assertEquals(0, signOuts)
    }

    @Test
    fun confirming_signsOutOnce_andClosesTheDialog() {
        show()
        rule.onNodeWithTag("profile_sign_out_button").performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("sign_out_dialog_confirm").assertTextEquals("Cerrar sesión").performClick()
        rule.mainClock.advanceTimeByFrame()
        assertEquals(1, signOuts)
        rule.onNodeWithTag("sign_out_dialog").assertDoesNotExist()
    }

    @Test
    fun cancelling_closesTheDialog_andNothingElseHappens() {
        show()
        rule.onNodeWithTag("profile_sign_out_button").performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("sign_out_dialog_cancel").assertTextEquals("Cancelar").performClick()
        rule.mainClock.advanceTimeByFrame()
        assertEquals(0, signOuts)
        rule.onNodeWithTag("sign_out_dialog").assertDoesNotExist()
        rule.onNodeWithTag("profile_screen").assertExists()
    }
}
