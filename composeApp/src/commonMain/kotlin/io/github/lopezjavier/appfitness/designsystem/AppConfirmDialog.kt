@file:Suppress("MatchingDeclarationName") // The file holds the dialog and its Defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/** Sizes, shape and tags of the [AppConfirmDialog]. */
object AppConfirmDialogDefaults {
    val MaxWidth: Dp = WindowWidthClassDefaults.NarrowContentMaxWidth
    val Shape = AppShapes.lg
    val InnerPadding: Dp = Spacing.xl
    val ButtonGap: Dp = Spacing.sm

    /** Inner width from which the two buttons sit side by side; narrower dialogs stack them. */
    val SideBySideMinWidth: Dp = 288.dp

    fun confirmTag(prefix: String): String = "${prefix}_confirm"

    fun cancelTag(prefix: String): String = "${prefix}_cancel"
}

/**
 * Dialog for a destructive confirmation (the only kind of dialog the app allows, planning/05 §1.5).
 * "Cancel" (neutral) comes first and "Confirm" (destructive, `error` colour) second; a scrim tap or back
 * is a cancel. The root is tagged [testTagPrefix] with `<prefix>_confirm` and `<prefix>_cancel` buttons unless
 * [confirmTestTag] or [cancelTestTag] override them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList")
@Composable
fun AppConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "confirm_dialog",
    confirmTestTag: String = AppConfirmDialogDefaults.confirmTag(testTagPrefix),
    cancelTestTag: String = AppConfirmDialogDefaults.cancelTag(testTagPrefix),
) {
    val colors = AppTheme.colors
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        // Own width (screen margins, capped on wide windows): independent of the platform dialog theme.
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = modifier.padding(horizontal = Spacing.screenMargin),
    ) {
        Surface(
            modifier =
            Modifier
                .fillMaxWidth()
                .widthIn(max = AppConfirmDialogDefaults.MaxWidth)
                // The dialog lives in its own window, so the main window's resource-id mapping does not reach it.
                .semantics { testTagsAsResourceId = true }
                .testTag(testTagPrefix),
            shape = AppConfirmDialogDefaults.Shape,
            color = colors.surfaceOverlay,
            border = BorderStroke(AppBorder.thin, colors.borderStrong),
        ) {
            Column(
                modifier = Modifier.padding(AppConfirmDialogDefaults.InnerPadding),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(
                    text = title,
                    style = AppTheme.typography.titleLarge,
                    color = colors.textPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                Text(text = message, style = AppTheme.typography.bodyMedium, color = colors.textSecondary)
                DialogButtons(confirmLabel, cancelLabel, onConfirm, onDismiss, confirmTestTag, cancelTestTag)
            }
        }
    }
}

/**
 * Cancel and confirm side by side (right-aligned) when they fit; stacked, confirm on top, when the dialog is too
 * narrow for each label to stay on one line.
 */
@Suppress("LongParameterList")
@Composable
private fun DialogButtons(
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmTag: String,
    cancelTag: String,
) {
    val cancel: @Composable (Modifier) -> Unit = { modifier ->
        GhostButton(
            text = cancelLabel,
            onClick = onDismiss,
            modifier = modifier.testTag(cancelTag),
            tone = GhostTone.Neutral,
        )
    }
    val confirm: @Composable (Modifier) -> Unit = { modifier ->
        GhostButton(
            text = confirmLabel,
            onClick = onConfirm,
            modifier = modifier.testTag(confirmTag),
            tone = GhostTone.Destructive,
        )
    }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
        if (maxWidth >= AppConfirmDialogDefaults.SideBySideMinWidth) {
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                horizontalArrangement = Arrangement.spacedBy(AppConfirmDialogDefaults.ButtonGap),
            ) {
                cancel(Modifier)
                confirm(Modifier)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(AppConfirmDialogDefaults.ButtonGap)) {
                confirm(Modifier.fillMaxWidth())
                cancel(Modifier.fillMaxWidth())
            }
        }
    }
}

@Preview
@Composable
private fun AppConfirmDialogPreview() {
    AppTheme {
        AppConfirmDialog(
            title = "¿Cerrar sesión?",
            message = "Tendrás que volver a entrar con tu cuenta de Google.",
            confirmLabel = "Cerrar sesión",
            cancelLabel = "Cancelar",
            onConfirm = {},
            onDismiss = {},
        )
    }
}
