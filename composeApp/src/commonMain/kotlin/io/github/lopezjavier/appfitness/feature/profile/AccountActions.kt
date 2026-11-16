package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.delete_account_error
import io.github.lopezjavier.appfitness.composeapp.resources.delete_account_error_offline
import io.github.lopezjavier.appfitness.composeapp.resources.profile_account_protected
import io.github.lopezjavier.appfitness.composeapp.resources.profile_delete_account
import io.github.lopezjavier.appfitness.composeapp.resources.profile_deleting_account
import io.github.lopezjavier.appfitness.composeapp.resources.profile_sign_out
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppListRow
import io.github.lopezjavier.appfitness.designsystem.AppTextButton
import io.github.lopezjavier.appfitness.designsystem.AppTextButtonTone
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.AccountProtection
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource

/**
 * [AccountActions] with their confirmations (R-08) and the delete-failure message (R-37). The open dialogs survive
 * recreation.
 */
@Composable
internal fun AccountSection(state: AccountUiState, onEvent: (AccountEvent) -> Unit, effects: Flow<AccountEffect>) {
    DeleteFailureMessages(effects)
    var confirmingSignOut by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    AccountActions(state, { confirmingSignOut = true }, { confirmingDelete = true })
    if (confirmingSignOut) {
        SignOutDialog(
            onConfirm = {
                confirmingSignOut = false
                onEvent(AccountEvent.SignOutConfirmed)
            },
            onDismiss = { confirmingSignOut = false },
        )
    }
    if (confirmingDelete) {
        DeleteAccountDialog(
            onConfirm = {
                confirmingDelete = false
                onEvent(AccountEvent.DeleteAccountConfirmed)
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

/**
 * "Cerrar sesión" as a row and "Borrar cuenta" as a danger text action at least 16 dp under it (R-70, R-113); the last group of the Preferencias hub. A
 * protected account shows its note under "Borrar cuenta", which also announces it as its state (ADR-57).
 */
@Composable
private fun AccountActions(state: AccountUiState, onSignOut: () -> Unit, onDelete: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        AppCard(Modifier.fillMaxWidth()) {
            AppListRow(
                headline = stringResource(Res.string.profile_sign_out),
                onClick = onSignOut,
                modifier = Modifier.testTag("preferences_sign_out"),
            )
        }
        val protectedNote = stringResource(Res.string.profile_account_protected)
        val isProtected = state.protection == AccountProtection.Protected
        AppTextButton(
            text = stringResource(
                if (state.isDeleting) Res.string.profile_deleting_account else Res.string.profile_delete_account,
            ),
            onClick = onDelete,
            modifier = Modifier.padding(top = Spacing.xl)
                .semantics { if (isProtected) stateDescription = protectedNote }
                .testTag("preferences_delete_account"),
            enabled = state.canDelete,
            tone = AppTextButtonTone.Danger,
        )
        if (isProtected) {
            // The action also announces this text as its state, so TalkBack users get it with the action.
            Text(
                text = protectedNote,
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = Spacing.sm).testTag("preferences_delete_protected_note"),
            )
        }
    }
}

/** A failed account deletion is one message (R-28, R-37) through the shell's queue; the session is kept. */
@Composable
internal fun DeleteFailureMessages(effects: Flow<AccountEffect>) {
    val messages = LocalAppMessages.current
    val failed = stringResource(Res.string.delete_account_error)
    val offline = stringResource(Res.string.delete_account_error_offline)
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is AccountEffect.DeleteFailed -> messages.show(if (effect.offline) offline else failed)
            }
        }
    }
}
