package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.live_discard_cancel
import io.github.lopezjavier.appfitness.composeapp.resources.live_discard_confirm
import io.github.lopezjavier.appfitness.composeapp.resources.live_discard_message
import io.github.lopezjavier.appfitness.composeapp.resources.live_discard_title
import io.github.lopezjavier.appfitness.composeapp.resources.live_finish
import io.github.lopezjavier.appfitness.composeapp.resources.live_finish_offline_reason
import io.github.lopezjavier.appfitness.composeapp.resources.live_menu_discard
import io.github.lopezjavier.appfitness.composeapp.resources.live_title
import io.github.lopezjavier.appfitness.designsystem.AppConfirmDialog
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.feature.routines.ActionsSheet
import io.github.lopezjavier.appfitness.feature.routines.SheetActionRow
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun LiveDialogs(
    state: LiveSessionUiState.Active,
    menuOpen: Boolean,
    onCloseMenu: () -> Unit,
    onEvent: (LiveSessionEvent) -> Unit,
) {
    if (menuOpen) {
        ActionsSheet(
            title = stringResource(Res.string.live_title),
            onDismiss = onCloseMenu,
            testTagPrefix = "live_menu",
        ) {
            SheetActionRow(
                label = stringResource(Res.string.live_menu_discard),
                onClick = {
                    onCloseMenu()
                    onEvent(LiveSessionEvent.RequestDiscard)
                },
                testTag = "live_menu_discard",
                enabled = !state.actionsBlocked,
                destructive = true,
            )
            if (state.actionsBlocked) OfflineReason("live_menu_offline_reason")
        }
    }
    state.finishSheet?.let {
        FinishSessionSheet(
            sheet = it,
            busy = state.busy,
            onConfirm = { onEvent(LiveSessionEvent.ConfirmFinish) },
            onDismiss = { onEvent(LiveSessionEvent.DismissFinish) },
        )
    }
    if (state.discardConfirm) {
        AppConfirmDialog(
            title = stringResource(Res.string.live_discard_title),
            message = stringResource(Res.string.live_discard_message),
            confirmLabel = stringResource(Res.string.live_discard_confirm),
            cancelLabel = stringResource(Res.string.live_discard_cancel),
            onConfirm = { onEvent(LiveSessionEvent.ConfirmDiscard) },
            onDismiss = { onEvent(LiveSessionEvent.DismissDiscard) },
            testTagPrefix = "live_discard",
        )
    }
}

/** Sticky bottom bar with the primary action; offline it explains why the button is disabled. */
@Composable
internal fun FinishBar(state: LiveSessionUiState.Active, onFinish: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().background(AppTheme.colors.surfaceRaised),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.widthIn(max = WindowWidthClassDefaults.CardColumnMaxWidth).fillMaxWidth().padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            PrimaryButton(
                text = stringResource(Res.string.live_finish),
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth().testTag("live_finish_button"),
                enabled = !state.actionsBlocked && !state.busy,
                loading = state.busy,
            )
            if (state.actionsBlocked) OfflineReason("live_finish_offline_reason")
        }
    }
}

@Composable
private fun OfflineReason(tag: String) {
    Text(
        text = stringResource(Res.string.live_finish_offline_reason),
        style = AppTheme.typography.bodySmall,
        color = AppTheme.colors.textSecondary,
        modifier = Modifier.padding(top = Spacing.sm).testTag(tag),
    )
}
