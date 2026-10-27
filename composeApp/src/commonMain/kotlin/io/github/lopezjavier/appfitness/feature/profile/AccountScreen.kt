package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.delete_account_error
import io.github.lopezjavier.appfitness.composeapp.resources.delete_account_error_offline
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_account_title
import io.github.lopezjavier.appfitness.composeapp.resources.profile_account_protected
import io.github.lopezjavier.appfitness.composeapp.resources.profile_delete_account
import io.github.lopezjavier.appfitness.composeapp.resources.profile_deleting_account
import io.github.lopezjavier.appfitness.composeapp.resources.profile_sign_out
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.GhostTone
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.AccountProtection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Stateful Cuenta: connects [AccountViewModel] to the stateless [AccountScreen]. */
@Composable
fun AccountRoot(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: AccountViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AccountScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
        effects = viewModel.effects,
    )
}

/**
 * Cuenta (Preferencias > "Cuenta", US-56) on [AppScreen]: "Cerrar sesión" and "Borrar cuenta" pinned at the bottom,
 * each behind its own confirmation (R-08). A protected account shows its note under "Borrar cuenta", which also
 * announces it as its state (ADR-57). A failed delete is a message (R-37). The open dialogs survive recreation.
 */
@Composable
fun AccountScreen(
    state: AccountUiState,
    onEvent: (AccountEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    effects: Flow<AccountEffect> = emptyFlow(),
) {
    DeleteFailureMessages(effects)
    var confirmingSignOut by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    AppScreen(
        title = stringResource(Res.string.preferences_account_title),
        modifier = modifier,
        onBack = onBack,
        testTagPrefix = "account",
        footer = {
            AccountActions(state, { confirmingSignOut = true }, { confirmingDelete = true })
        },
    ) {
        Box(Modifier.fillMaxSize().testTag("account_screen"))
    }
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

@Composable
private fun AccountActions(state: AccountUiState, onSignOut: () -> Unit, onDelete: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        GhostButton(
            text = stringResource(Res.string.profile_sign_out),
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth().testTag("account_sign_out"),
            tone = GhostTone.Neutral,
        )
        val protectedNote = stringResource(Res.string.profile_account_protected)
        val isProtected = state.protection == AccountProtection.Protected
        GhostButton(
            text = stringResource(
                if (state.isDeleting) Res.string.profile_deleting_account else Res.string.profile_delete_account,
            ),
            onClick = onDelete,
            modifier = Modifier.fillMaxWidth()
                .semantics { if (isProtected) stateDescription = protectedNote }
                .testTag("account_delete"),
            enabled = state.canDelete,
            tone = GhostTone.Destructive,
            loading = state.isDeleting,
        )
        if (isProtected) {
            // The button also announces this text as its state, so TalkBack users get it with the button.
            Text(
                text = protectedNote,
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().testTag("account_delete_protected_note"),
            )
        }
    }
}

/** A failed account deletion is one message (R-28, R-37) through the shell's queue; the session is kept. */
@Composable
private fun DeleteFailureMessages(effects: Flow<AccountEffect>) {
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
