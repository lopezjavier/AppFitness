package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_title
import io.github.lopezjavier.appfitness.composeapp.resources.profile_sign_out
import io.github.lopezjavier.appfitness.composeapp.resources.sign_out_dialog_cancel
import io.github.lopezjavier.appfitness.composeapp.resources.sign_out_dialog_confirm
import io.github.lopezjavier.appfitness.composeapp.resources.sign_out_dialog_message
import io.github.lopezjavier.appfitness.composeapp.resources.sign_out_dialog_title
import io.github.lopezjavier.appfitness.composeapp.resources.wip_footer
import io.github.lopezjavier.appfitness.composeapp.resources.wip_message
import io.github.lopezjavier.appfitness.designsystem.AppConfirmDialog
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.GhostTone
import io.github.lopezjavier.appfitness.designsystem.SecondaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WipPlaceholder
import org.jetbrains.compose.resources.stringResource

/**
 * Work-in-progress full-screen profile destination (ADR-22) until its epic replaces it, plus the sign-out action.
 * [onOpenCatalog] is non-null only where the debug component catalog is available (local flavor).
 * [onSignOut] runs after the user confirms the dialog; cancelling closes it and nothing else happens.
 */
@Composable
fun ProfileScreen(modifier: Modifier = Modifier, onOpenCatalog: (() -> Unit)? = null, onSignOut: () -> Unit = {}) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier.testTag("profile_screen")) {
        if (onOpenCatalog != null) {
            SecondaryButton(
                text = stringResource(Res.string.catalog_title),
                onClick = onOpenCatalog,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.screenMargin, end = Spacing.screenMargin, top = Spacing.lg)
                    .testTag("profile_catalog_button"),
            )
        }
        WipPlaceholder(
            message = stringResource(Res.string.wip_message),
            footer = stringResource(Res.string.wip_footer),
            testTagPrefix = "profile",
            modifier = Modifier.weight(1f),
        )
        GhostButton(
            text = stringResource(Res.string.profile_sign_out),
            onClick = { confirming = true },
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenMargin, vertical = Spacing.blockGap)
                .testTag("profile_sign_out_button"),
            tone = GhostTone.Destructive,
        )
    }
    if (confirming) {
        AppConfirmDialog(
            title = stringResource(Res.string.sign_out_dialog_title),
            message = stringResource(Res.string.sign_out_dialog_message),
            confirmLabel = stringResource(Res.string.sign_out_dialog_confirm),
            cancelLabel = stringResource(Res.string.sign_out_dialog_cancel),
            onConfirm = {
                confirming = false
                onSignOut()
            },
            onDismiss = { confirming = false },
            testTagPrefix = "sign_out_dialog",
        )
    }
}
