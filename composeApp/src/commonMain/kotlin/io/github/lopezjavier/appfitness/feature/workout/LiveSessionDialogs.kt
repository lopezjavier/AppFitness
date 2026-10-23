package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.live_discard_cancel
import io.github.lopezjavier.appfitness.composeapp.resources.live_discard_confirm
import io.github.lopezjavier.appfitness.composeapp.resources.live_discard_message
import io.github.lopezjavier.appfitness.composeapp.resources.live_discard_title
import io.github.lopezjavier.appfitness.composeapp.resources.live_finish
import io.github.lopezjavier.appfitness.composeapp.resources.live_finish_empty_reason
import io.github.lopezjavier.appfitness.composeapp.resources.live_finish_offline_reason
import io.github.lopezjavier.appfitness.composeapp.resources.live_menu_discard
import io.github.lopezjavier.appfitness.composeapp.resources.live_title
import io.github.lopezjavier.appfitness.designsystem.AppConfirmDialog
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.RpePickerSheet
import io.github.lopezjavier.appfitness.designsystem.SetTypePickerSheet
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.usecase.RpeScale
import io.github.lopezjavier.appfitness.feature.routines.ActionsSheet
import io.github.lopezjavier.appfitness.feature.routines.SheetActionRow
import org.jetbrains.compose.resources.stringResource

/** The set type picker of the targeted set; a tap applies the choice, dismissing changes nothing. */
@Composable
internal fun LiveTypePicker(state: LiveSessionUiState.Active, onEvent: (LiveSessionEvent) -> Unit) {
    val target = state.typePicker ?: return
    val current = state.exercises.firstOrNull { it.position == target.position }
        ?.sets?.firstOrNull { it.setIndex == target.setIndex }?.type ?: return
    SetTypePickerSheet(
        current = current,
        onSelect = { onEvent(LiveSessionEvent.SetTypeChosen(target.position, target.setIndex, it)) },
        onDismiss = { onEvent(LiveSessionEvent.DismissTypePicker) },
    )
}

/** The RPE picker of the targeted set; a tap applies the choice (and completes the set when prompted). */
@Composable
internal fun LiveRpePicker(state: LiveSessionUiState.Active, onEvent: (LiveSessionEvent) -> Unit) {
    val target = state.rpePicker ?: return
    val row = state.exercises.firstOrNull { it.position == target.position }
        ?.sets?.firstOrNull { it.setIndex == target.setIndex } ?: return
    RpePickerSheet(
        current = row.rpe?.let(RpeScale::parse),
        setNumber = row.setIndex,
        onSelect = { onEvent(LiveSessionEvent.SetRpeChosen(target.position, target.setIndex, it)) },
        onDismiss = { onEvent(LiveSessionEvent.DismissRpePicker) },
        completesSet = target.completesSet,
    )
}

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
                label = stringResource(Res.string.live_finish),
                onClick = {
                    onCloseMenu()
                    onEvent(LiveSessionEvent.RequestFinish)
                },
                testTag = "live_menu_finish",
                enabled = !state.actionsBlocked && !state.busy && state.canFinish,
            )
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
            if (!state.canFinish) FinishEmptyReason()
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

@Composable
private fun FinishEmptyReason() {
    Text(
        text = stringResource(Res.string.live_finish_empty_reason),
        style = AppTheme.typography.bodySmall,
        color = AppTheme.colors.textSecondary,
        modifier = Modifier.padding(top = Spacing.sm).testTag("live_menu_finish_reason"),
    )
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
