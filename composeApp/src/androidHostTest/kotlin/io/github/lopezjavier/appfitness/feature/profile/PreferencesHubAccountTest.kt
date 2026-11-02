package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.width
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.domain.model.AccountProtection
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-56 / US-05 at the end of the hub: both buttons behind their dialogs, the protected note, failures. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class PreferencesHubAccountTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<AccountEvent>()
    private val messages = RecordingMessages()
    private var backs = 0

    private fun state(
        isOnline: Boolean = true,
        isDeleting: Boolean = false,
        protection: AccountProtection = AccountProtection.Unprotected,
    ) = AccountUiState(isOnline, isDeleting, protection)

    private fun show(state: AccountUiState = state(), effects: Channel<AccountEffect> = Channel(Channel.BUFFERED)) {
        events.clear()
        rule.setContent {
            AppTheme {
                CompositionLocalProvider(LocalAppMessages provides messages) {
                    PreferencesHubScreen(
                        onBack = { backs++ },
                        actions = PreferencesHubActions(),
                        accountState = state,
                        onAccountEvent = { events += it },
                        accountEffects = effects.receiveAsFlow(),
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun noCuentaRow_andBothButtonsEndTheScroll_signOutThenDelete() {
        show()
        rule.onNodeWithTag("preferences_row_account").assertDoesNotExist()
        rule.onNodeWithTag("preferences_footer").assertDoesNotExist()
        val avisos = rule.onNodeWithTag("preferences_row_avisos").getUnclippedBoundsInRoot()
        val signOut = rule.onNodeWithTag("preferences_sign_out").getUnclippedBoundsInRoot()
        val delete = rule.onNodeWithTag("preferences_delete_account").getUnclippedBoundsInRoot()
        assertTrue(signOut.top >= avisos.bottom)
        assertTrue(signOut.bottom <= delete.top)
    }

    @Test
    fun signOut_asksFirst_andConfirmSendsTheEventOnce() {
        show()
        rule.onNodeWithTag("preferences_sign_out").performClick()
        assertTrue(events.isEmpty())
        rule.onNodeWithTag("account_sign_out_dialog_confirm").performClick()
        rule.waitForIdle()
        assertEquals(listOf<AccountEvent>(AccountEvent.SignOutConfirmed), events)
        rule.onNodeWithTag("account_sign_out_dialog").assertDoesNotExist()
    }

    @Test
    fun signOut_cancelSendsNothing() {
        show()
        rule.onNodeWithTag("preferences_sign_out").performClick()
        rule.onNodeWithTag("account_sign_out_dialog_cancel").performClick()
        rule.waitForIdle()
        assertTrue(events.isEmpty())
        rule.onNodeWithTag("account_sign_out_dialog").assertDoesNotExist()
    }

    @Test
    fun delete_asksFirst_andConfirmSendsTheEventOnce() {
        show()
        rule.onNodeWithTag("preferences_delete_account").performClick()
        assertTrue(events.isEmpty())
        rule.onNodeWithTag("account_delete_dialog_confirm").performClick()
        rule.waitForIdle()
        assertEquals(listOf<AccountEvent>(AccountEvent.DeleteAccountConfirmed), events)
        rule.onNodeWithTag("account_delete_dialog").assertDoesNotExist()
    }

    @Test
    fun delete_cancelSendsNothing() {
        show()
        rule.onNodeWithTag("preferences_delete_account").performClick()
        rule.onNodeWithTag("account_delete_dialog_cancel").performClick()
        rule.waitForIdle()
        assertTrue(events.isEmpty())
    }

    @Test
    fun deleteButton_isDisabledOffline() {
        show(state(isOnline = false))
        rule.onNodeWithTag("preferences_delete_account").assertIsNotEnabled()
        rule.onNodeWithTag("preferences_sign_out").assertIsEnabled()
    }

    @Test
    fun deleting_disablesTheButton_readsBorrando_andShowsTheLoader() {
        show(state(isDeleting = true))
        rule.onNodeWithTag("preferences_delete_account").assertIsNotEnabled().assertTextEquals("Borrando…")
        rule.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo) and
                hasAnyAncestor(hasTestTag("preferences_delete_account")),
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun protectedAccount_disablesDelete_showsTheNoteUnderIt_andOpensNoDialog() {
        show(state(protection = AccountProtection.Protected))
        rule.onNodeWithTag("preferences_delete_account").assertIsNotEnabled().performClick()
        rule.onNodeWithTag("preferences_delete_protected_note").assertTextEquals("Esta cuenta está protegida")
        rule.onNodeWithTag("account_delete_dialog").assertDoesNotExist()
        val delete = rule.onNodeWithTag("preferences_delete_account").getUnclippedBoundsInRoot()
        val note = rule.onNodeWithTag("preferences_delete_protected_note").getUnclippedBoundsInRoot()
        assertTrue(note.top >= delete.bottom)
    }

    @Test
    fun protectedAccount_readsTheNoteAsTheButtonState() {
        show(state(protection = AccountProtection.Protected))
        rule.onNodeWithTag("preferences_delete_account").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Esta cuenta está protegida"),
        )
    }

    @Test
    fun unprotectedAccount_showsNoNote_andDeleteIsEnabled() {
        show(state(protection = AccountProtection.Unprotected))
        rule.onNodeWithTag("preferences_delete_protected_note").assertDoesNotExist()
        rule.onNodeWithTag("preferences_delete_account").assertIsEnabled()
    }

    @Test
    fun unknownProtection_disablesDelete_withoutNote() {
        show(state(protection = AccountProtection.Unknown))
        rule.onNodeWithTag("preferences_delete_account").assertIsNotEnabled()
        rule.onNodeWithTag("preferences_delete_protected_note").assertDoesNotExist()
    }

    @Test
    fun deleteFailure_isOneMessage() {
        val effects = Channel<AccountEffect>(Channel.BUFFERED)
        show(effects = effects)
        effects.trySend(AccountEffect.DeleteFailed(offline = false))
        rule.waitForIdle()
        assertEquals(listOf("No se pudo borrar la cuenta. Inténtalo de nuevo."), messages.shown)
    }

    @Test
    fun offlineDeleteFailure_saysThereIsNoConnection() {
        val effects = Channel<AccountEffect>(Channel.BUFFERED)
        show(effects = effects)
        effects.trySend(AccountEffect.DeleteFailed(offline = true))
        rule.waitForIdle()
        assertEquals(listOf("Sin conexión: no se puede borrar la cuenta"), messages.shown)
    }

    @Test
    @Config(qualifiers = "w840dp-h1000dp-mdpi")
    fun tablet_capsTheButtonWidth() {
        show()
        val delete = rule.onNodeWithTag("preferences_delete_account").getUnclippedBoundsInRoot()
        assertTrue("button width ${delete.width}", delete.width <= WindowWidthClassDefaults.FormContentMaxWidth)
    }

    @Test
    fun openDialog_survivesRecreation() {
        val tester = androidx.compose.ui.test.junit4.StateRestorationTester(rule)
        tester.setContent { AppTheme { PreferencesHubScreen({}, PreferencesHubActions(), accountState = state()) } }
        rule.onNodeWithTag("preferences_sign_out").performClick()
        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        rule.onNodeWithTag("account_sign_out_dialog").assertExists()
    }
}
