package io.github.lopezjavier.appfitness.feature.routines

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_cancel
import io.github.lopezjavier.appfitness.composeapp.resources.action_delete
import io.github.lopezjavier.appfitness.composeapp.resources.action_edit
import io.github.lopezjavier.appfitness.composeapp.resources.ic_add
import io.github.lopezjavier.appfitness.composeapp.resources.ic_delete
import io.github.lopezjavier.appfitness.composeapp.resources.ic_edit
import io.github.lopezjavier.appfitness.composeapp.resources.ic_label
import io.github.lopezjavier.appfitness.composeapp.resources.ic_star
import io.github.lopezjavier.appfitness.composeapp.resources.routine_duplicate
import io.github.lopezjavier.appfitness.composeapp.resources.routines_folder_delete_message
import io.github.lopezjavier.appfitness.composeapp.resources.routines_folder_delete_title
import io.github.lopezjavier.appfitness.composeapp.resources.routines_group_none
import io.github.lopezjavier.appfitness.composeapp.resources.routines_move_sheet_title
import io.github.lopezjavier.appfitness.composeapp.resources.routines_move_to_folder
import io.github.lopezjavier.appfitness.composeapp.resources.routines_next_clear
import io.github.lopezjavier.appfitness.composeapp.resources.routines_next_set
import io.github.lopezjavier.appfitness.composeapp.resources.routines_offline_reason
import io.github.lopezjavier.appfitness.designsystem.AppActionSheet
import io.github.lopezjavier.appfitness.designsystem.AppConfirmDialog
import io.github.lopezjavier.appfitness.designsystem.AppPickerSheet
import io.github.lopezjavier.appfitness.designsystem.AppSheetAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.SheetActionRow
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.RoutineSummary
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Every sheet and dialog of the routines list: routine actions, folder picker, folder name and the folder delete. */
@Composable
internal fun RoutinesDialogs(state: RoutinesUiState.Content, onEvent: (RoutinesEvent) -> Unit) {
    state.menuFor?.let { RoutineMenuSheet(it, state.isOnline, onEvent) }
    state.moveFor?.let { MoveSheet(it, state, onEvent) }
    state.folderSheet?.let { FolderSheet(it, state.folders, state.folderSaving, state.isOnline, onEvent) }
    // The only confirmation here (R-08 #3): a folder that still holds routines; an empty one is deleted at once.
    state.deleteFolderFor?.let { folder ->
        val count = state.deleteFolderRoutineCount
        AppConfirmDialog(
            title = stringResource(Res.string.routines_folder_delete_title, folder.name),
            message = pluralStringResource(Res.plurals.routines_folder_delete_message, count, count),
            confirmLabel = stringResource(Res.string.action_delete),
            cancelLabel = stringResource(Res.string.action_cancel),
            onConfirm = { onEvent(RoutinesEvent.ConfirmDeleteFolder) },
            onDismiss = { onEvent(RoutinesEvent.DismissDeleteFolder) },
            testTagPrefix = "routines_folder_delete_dialog",
            confirmTestTag = "routines_folder_delete_confirm",
            cancelTestTag = "routines_folder_delete_cancel",
        )
    }
}

/**
 * Actions of one routine (R-07). Offline only "Editar" and "Eliminar" are listed ("Eliminar" explains the refusal),
 * with the reason under the title. Reordering is on the card itself (handle, TalkBack "Subir" / "Bajar").
 */
@Composable
private fun RoutineMenuSheet(routine: RoutineSummary, isOnline: Boolean, onEvent: (RoutinesEvent) -> Unit) {
    val id = routine.id
    val actions = buildList {
        add(
            AppSheetAction(Res.drawable.ic_edit, stringResource(Res.string.action_edit)) {
                onEvent(RoutinesEvent.Edit(id))
            },
        )
        if (isOnline) {
            add(
                AppSheetAction(Res.drawable.ic_add, stringResource(Res.string.routine_duplicate)) {
                    onEvent(RoutinesEvent.Duplicate(id))
                },
            )
            add(
                AppSheetAction(Res.drawable.ic_label, stringResource(Res.string.routines_move_to_folder)) {
                    onEvent(RoutinesEvent.OpenMove(id))
                },
            )
            val nextLabel = stringResource(
                if (routine.isNext) Res.string.routines_next_clear else Res.string.routines_next_set,
            )
            add(
                AppSheetAction(Res.drawable.ic_star, nextLabel) {
                    onEvent(if (routine.isNext) RoutinesEvent.ClearNext else RoutinesEvent.SetNext(id))
                },
            )
        }
        add(
            AppSheetAction(Res.drawable.ic_delete, stringResource(Res.string.action_delete)) {
                onEvent(RoutinesEvent.Delete(id))
            },
        )
    }
    AppActionSheet(
        actions = actions,
        onDismiss = { onEvent(RoutinesEvent.DismissMenu) },
        title = routine.name,
        testTagPrefix = "routines_menu",
        notice = if (isOnline) null else ({ OfflineReason() }),
    )
}

/** Folder picker: one tap on a target moves the routine there and closes; X, back or scrim close without moving. */
@Composable
private fun MoveSheet(routine: RoutineSummary, state: RoutinesUiState.Content, onEvent: (RoutinesEvent) -> Unit) {
    AppPickerSheet(
        title = stringResource(Res.string.routines_move_sheet_title, routine.name),
        onDismiss = { onEvent(RoutinesEvent.DismissMove) },
        testTagPrefix = "routines_move_sheet",
    ) {
        val currentFolder = state.folders.firstOrNull { it.id == routine.folderId }?.id
        SheetActionRow(
            label = stringResource(Res.string.routines_group_none),
            onClick = { onEvent(RoutinesEvent.MoveToFolder(routine.id, null)) },
            testTag = "routines_move_target_none",
            enabled = currentFolder != null,
        )
        state.folders.forEach { folder ->
            SheetActionRow(
                label = folder.name,
                onClick = { onEvent(RoutinesEvent.MoveToFolder(routine.id, folder.id)) },
                testTag = "routines_move_target_${folder.id}",
                enabled = folder.id != currentFolder,
            )
        }
    }
}

@Composable
private fun OfflineReason() {
    Text(
        text = stringResource(Res.string.routines_offline_reason),
        style = AppTheme.typography.bodySmall,
        color = AppTheme.colors.textSecondary,
        modifier = Modifier.padding(horizontal = Spacing.screenMargin).testTag("routines_menu_offline_reason"),
    )
}
