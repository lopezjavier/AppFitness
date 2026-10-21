package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import io.github.lopezjavier.appfitness.designsystem.AppAvatarDefaults
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-05 on the Perfil root: photo and name, "Preferencias", sign out and delete account. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class ProfileRootScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<ProfileRootEvent>()
    private var catalogOpens = 0
    private var preferencesOpens = 0
    private var strengthOpens = 0
    private var diaryHistoryOpens = 0

    private fun loaded(
        name: String? = "Ana María",
        avatarUrl: String? = null,
        isOnline: Boolean = true,
        isDeleting: Boolean = false,
    ) = ProfileRootUiState.Loaded(name, avatarUrl, isOnline, isDeleting)

    private fun show(
        state: ProfileRootUiState = loaded(),
        effects: Channel<ProfileRootEffect> = Channel(Channel.BUFFERED),
        withCatalog: Boolean = false,
    ) {
        events.clear()
        catalogOpens = 0
        preferencesOpens = 0
        strengthOpens = 0
        diaryHistoryOpens = 0
        rule.setContent {
            AppTheme {
                ProfileRootScreen(
                    state = state,
                    onEvent = { events += it },
                    onOpenPreferences = { preferencesOpens++ },
                    onOpenStrength = { strengthOpens++ },
                    onOpenDiaryHistory = { diaryHistoryOpens++ },
                    onOpenCatalog = if (withCatalog) ({ catalogOpens++ }) else null,
                    effects = effects.receiveAsFlow(),
                )
            }
        }
        rule.waitForIdle()
    }

    // -- header ---------------------------------------------------------------------------------------------------

    @Test
    fun header_showsTheNameAndThePlaceholderWhenThereIsNoPhoto() {
        show(loaded(avatarUrl = null))
        rule.onNodeWithTag("profile_root_name").assertTextEquals("Ana María")
        rule.onNodeWithTag("profile_root_avatar_placeholder", useUnmergedTree = true).assertExists()
    }

    @Test
    fun avatar_isTheRootSize() {
        show()
        val bounds = rule.onNodeWithTag("profile_root_avatar").getUnclippedBoundsInRoot()
        assertEquals(AppAvatarDefaults.RootSize, bounds.width)
        assertEquals(AppAvatarDefaults.RootSize, bounds.height)
    }

    @Test
    fun aMissingName_showsNoNameLine() {
        show(loaded(name = null))
        rule.onNodeWithTag("profile_root_name").assertDoesNotExist()
        rule.onNodeWithTag("profile_root_avatar").assertExists()
    }

    @Test
    fun photoThatDoesNotLoad_keepsThePlaceholder() {
        show(loaded(avatarUrl = "https://invalid.example/none.png"))
        rule.onNodeWithTag("profile_root_avatar_placeholder", useUnmergedTree = true).assertExists()
    }

    // -- Preferencias ---------------------------------------------------------------------------------------------

    @Test
    fun preferencesButton_opensPreferences_andMeetsTheTouchTarget() {
        show()
        rule.onNodeWithTag("profile_root_preferences_button")
            .assertTextEquals("Preferencias").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, preferencesOpens)
    }

    @Test
    fun strengthRow_isInTheStatisticsSection_andOpensFuerza() {
        show()
        rule.onNodeWithText("ESTADÍSTICAS").assertExists()
        rule.onNodeWithTag("profile_strength_row").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithText("Fuerza").assertExists()

        rule.onNodeWithTag("profile_strength_row").performClick()

        assertEquals(1, strengthOpens)
    }

    @Test
    fun diaryHistoryRow_isBelowFuerza_andOpensTheHistory() {
        show()
        rule.onNodeWithText("Calorías").assertExists()
        rule.onNodeWithText("Historial de comidas").assertDoesNotExist()
        rule.onNodeWithTag("profile_diary_history_row").assertHeightIsAtLeast(48.dp)
        val strength = rule.onNodeWithTag("profile_strength_row").getUnclippedBoundsInRoot()
        val history = rule.onNodeWithTag("profile_diary_history_row").getUnclippedBoundsInRoot()
        assertTrue(history.top >= strength.bottom)

        rule.onNodeWithTag("profile_diary_history_row").performClick()

        assertEquals(1, diaryHistoryOpens)
        assertEquals(0, strengthOpens)
    }

    @Test
    fun diaryHistoryRow_isHiddenWhileTheProfileLoads() {
        show(ProfileRootUiState.Loading)
        rule.onNodeWithTag("profile_diary_history_row").assertDoesNotExist()
    }

    @Test
    fun strengthRow_isAboveTheAccountActions() {
        show()
        val strength = rule.onNodeWithTag("profile_strength_row").getUnclippedBoundsInRoot()
        val signOut = rule.onNodeWithTag("profile_root_sign_out").getUnclippedBoundsInRoot()
        assertTrue(signOut.top >= strength.bottom)
    }

    @Test
    fun strengthRow_isHiddenWhileTheProfileLoads() {
        show(ProfileRootUiState.Loading)
        rule.onNodeWithTag("profile_strength_row").assertDoesNotExist()
        rule.onNodeWithTag("profile_root_sign_out").assertExists()
    }

    @Test
    fun actions_areBelowTheHeader() {
        show()
        val header = rule.onNodeWithTag("profile_root_name").getUnclippedBoundsInRoot()
        val preferences = rule.onNodeWithTag("profile_root_preferences_button").getUnclippedBoundsInRoot()
        val signOut = rule.onNodeWithTag("profile_root_sign_out").getUnclippedBoundsInRoot()
        val delete = rule.onNodeWithTag("profile_root_delete_account").getUnclippedBoundsInRoot()
        assertTrue(preferences.top >= header.bottom)
        assertTrue(signOut.top >= preferences.bottom)
        assertTrue(delete.top >= signOut.bottom)
    }

    @Test
    fun catalogButton_isShownOnlyWhenProvided_andOpensTheCatalog() {
        show(withCatalog = false)
        rule.onNodeWithTag("profile_root_catalog_button").assertDoesNotExist()
    }

    @Test
    fun catalogButton_opensTheCatalog() {
        show(withCatalog = true)
        rule.onNodeWithTag("profile_root_catalog_button").performClick()
        assertEquals(1, catalogOpens)
    }

    // -- states ---------------------------------------------------------------------------------------------------

    @Test
    fun loading_showsTheSkeleton() {
        rule.mainClock.autoAdvance = false
        show(ProfileRootUiState.Loading)
        rule.onNodeWithTag("profile_root_loading").assertExists()
        rule.onNodeWithTag("profile_root_name").assertDoesNotExist()
    }

    @Test
    fun loadFailed_showsRetry_andKeepsSignOut() {
        show(ProfileRootUiState.LoadFailed)
        rule.onNodeWithTag("profile_root_error_action").assertTextEquals("Reintentar").performClick()
        assertEquals(listOf<ProfileRootEvent>(ProfileRootEvent.Retry), events)
        rule.onNodeWithTag("profile_root_sign_out").assertExists()
        rule.onNodeWithTag("profile_root_delete_account").assertDoesNotExist()
        rule.onNodeWithTag("profile_root_preferences_button").assertDoesNotExist()
    }

    // -- US-05 sign out -------------------------------------------------------------------------------------------

    @Test
    fun signOutButton_isShown_andMeetsTheTouchTarget() {
        show()
        rule.onNodeWithTag("profile_root_sign_out").assertTextEquals("Cerrar sesión").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("profile_root_sign_out_dialog").assertDoesNotExist()
    }

    @Test
    fun tappingSignOut_opensTheDialogWithoutSigningOut() {
        show()
        rule.onNodeWithTag("profile_root_sign_out").performClick()
        rule.onNodeWithTag("profile_root_sign_out_dialog").assertExists()
        assertTrue(events.isEmpty())
    }

    @Test
    fun confirming_signsOutOnce_andClosesTheDialog() {
        show()
        rule.onNodeWithTag("profile_root_sign_out").performClick()
        rule.onNodeWithTag("profile_root_sign_out_dialog_confirm").assertTextEquals("Cerrar sesión").performClick()
        rule.waitForIdle()
        assertEquals(listOf<ProfileRootEvent>(ProfileRootEvent.SignOutConfirmed), events)
        rule.onNodeWithTag("profile_root_sign_out_dialog").assertDoesNotExist()
    }

    @Test
    fun cancelling_closesTheDialog_andNothingElseHappens() {
        show()
        rule.onNodeWithTag("profile_root_sign_out").performClick()
        rule.onNodeWithTag("profile_root_sign_out_dialog_cancel").assertTextEquals("Cancelar").performClick()
        rule.waitForIdle()
        assertTrue(events.isEmpty())
        rule.onNodeWithTag("profile_root_sign_out_dialog").assertDoesNotExist()
        rule.onNodeWithTag("profile_screen").assertExists()
    }

    // -- US-05 delete account -------------------------------------------------------------------------------------

    @Test
    fun deleteButton_isLastInTheBlock_andMeetsTheTouchTarget() {
        show()
        rule.onNodeWithTag("profile_root_delete_account")
            .assertTextEquals("Borrar cuenta").assertIsEnabled().assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("profile_root_delete_account_dialog").assertDoesNotExist()
        val signOutBottom = rule.onNodeWithTag("profile_root_sign_out").getUnclippedBoundsInRoot().bottom
        val deleteTop = rule.onNodeWithTag("profile_root_delete_account").getUnclippedBoundsInRoot().top
        assertTrue(deleteTop >= signOutBottom)
    }

    @Test
    fun tappingDelete_opensTheDialogWithTheApprovedTexts_andDeletesNothing() {
        show()
        rule.onNodeWithTag("profile_root_delete_account").performClick()
        rule.onNodeWithTag("profile_root_delete_account_dialog").assertExists()
        rule.onNodeWithText("¿Borrar tu cuenta?").assertExists()
        rule.onNodeWithText("Se borrarán tu perfil, tu peso y tus objetivos. No se puede deshacer.").assertExists()
        rule.onNodeWithTag("profile_root_delete_account_dialog_cancel").assertTextEquals("Cancelar")
        rule.onNodeWithTag("profile_root_delete_account_dialog_confirm").assertTextEquals("Borrar")
        assertTrue(events.isEmpty())
    }

    @Test
    fun cancellingDelete_closesTheDialog_andDeletesNothing() {
        show()
        rule.onNodeWithTag("profile_root_delete_account").performClick()
        rule.onNodeWithTag("profile_root_delete_account_dialog_cancel").performClick()
        rule.waitForIdle()
        assertTrue(events.isEmpty())
        rule.onNodeWithTag("profile_root_delete_account_dialog").assertDoesNotExist()
    }

    @Test
    fun confirmingDelete_sendsTheEventOnce_andClosesTheDialog() {
        show()
        rule.onNodeWithTag("profile_root_delete_account").performClick()
        rule.onNodeWithTag("profile_root_delete_account_dialog_confirm").performClick()
        rule.waitForIdle()
        assertEquals(listOf<ProfileRootEvent>(ProfileRootEvent.DeleteAccountConfirmed), events)
        rule.onNodeWithTag("profile_root_delete_account_dialog").assertDoesNotExist()
    }

    @Test
    fun deleteButton_isDisabledOffline() {
        show(loaded(isOnline = false))
        rule.onNodeWithTag("profile_root_delete_account").assertIsNotEnabled()
    }

    @Test
    fun deleting_disablesTheButton_andReadsBorrando() {
        show(loaded(isDeleting = true))
        rule.onNodeWithTag("profile_root_delete_account").assertIsNotEnabled().assertTextEquals("Borrando…")
        rule.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo) and
                hasAnyAncestor(hasTestTag("profile_root_delete_account")),
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun deleteFailedEffect_showsTheErrorSnackbar() {
        val effects = Channel<ProfileRootEffect>(Channel.BUFFERED)
        show(effects = effects)
        effects.trySend(ProfileRootEffect.DeleteFailed(offline = false))
        rule.waitForIdle()
        rule.onNodeWithText("No se pudo borrar la cuenta. Inténtalo de nuevo").assertIsDisplayed()
    }

    @Test
    fun deleteFailedOfflineEffect_showsTheOfflineSnackbar() {
        val effects = Channel<ProfileRootEffect>(Channel.BUFFERED)
        show(effects = effects)
        effects.trySend(ProfileRootEffect.DeleteFailed(offline = true))
        rule.waitForIdle()
        rule.onNodeWithText("Sin conexión. Inténtalo de nuevo").assertIsDisplayed()
    }
}
