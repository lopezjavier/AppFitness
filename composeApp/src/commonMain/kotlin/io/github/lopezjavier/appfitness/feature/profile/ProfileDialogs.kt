package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.runtime.Composable
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.delete_account_dialog_cancel
import io.github.lopezjavier.appfitness.composeapp.resources.delete_account_dialog_confirm
import io.github.lopezjavier.appfitness.composeapp.resources.delete_account_dialog_message
import io.github.lopezjavier.appfitness.composeapp.resources.delete_account_dialog_title
import io.github.lopezjavier.appfitness.composeapp.resources.sign_out_dialog_cancel
import io.github.lopezjavier.appfitness.composeapp.resources.sign_out_dialog_confirm
import io.github.lopezjavier.appfitness.composeapp.resources.sign_out_dialog_message
import io.github.lopezjavier.appfitness.composeapp.resources.sign_out_dialog_title
import io.github.lopezjavier.appfitness.designsystem.AppConfirmDialog
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SignOutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AppConfirmDialog(
        title = stringResource(Res.string.sign_out_dialog_title),
        message = stringResource(Res.string.sign_out_dialog_message),
        confirmLabel = stringResource(Res.string.sign_out_dialog_confirm),
        cancelLabel = stringResource(Res.string.sign_out_dialog_cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        testTagPrefix = "profile_root_sign_out_dialog",
    )
}

@Composable
internal fun DeleteAccountDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AppConfirmDialog(
        title = stringResource(Res.string.delete_account_dialog_title),
        message = stringResource(Res.string.delete_account_dialog_message),
        confirmLabel = stringResource(Res.string.delete_account_dialog_confirm),
        cancelLabel = stringResource(Res.string.delete_account_dialog_cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        testTagPrefix = "profile_root_delete_account_dialog",
    )
}
