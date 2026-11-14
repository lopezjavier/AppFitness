package io.github.lopezjavier.appfitness.feature.routines

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_delete
import io.github.lopezjavier.appfitness.composeapp.resources.action_edit
import io.github.lopezjavier.appfitness.composeapp.resources.ic_content_copy
import io.github.lopezjavier.appfitness.composeapp.resources.ic_delete
import io.github.lopezjavier.appfitness.composeapp.resources.ic_drive_file_move
import io.github.lopezjavier.appfitness.composeapp.resources.ic_edit
import io.github.lopezjavier.appfitness.composeapp.resources.ic_fact_check
import io.github.lopezjavier.appfitness.composeapp.resources.ic_flag
import io.github.lopezjavier.appfitness.composeapp.resources.routine_duplicate
import io.github.lopezjavier.appfitness.composeapp.resources.routines_folder_menu_delete
import io.github.lopezjavier.appfitness.composeapp.resources.routines_folder_menu_delete_blocked_cd
import io.github.lopezjavier.appfitness.composeapp.resources.routines_folder_menu_offline_reason
import io.github.lopezjavier.appfitness.composeapp.resources.routines_folder_menu_rename
import io.github.lopezjavier.appfitness.composeapp.resources.routines_folder_menu_review
import io.github.lopezjavier.appfitness.composeapp.resources.routines_group_none
import io.github.lopezjavier.appfitness.composeapp.resources.routines_move_sheet_title
import io.github.lopezjavier.appfitness.composeapp.resources.routines_move_to_folder
import io.github.lopezjavier.appfitness.composeapp.resources.routines_next_clear
import io.github.lopezjavier.appfitness.composeapp.resources.routines_next_set
import io.github.lopezjavier.appfitness.composeapp.resources.routines_offline_reason
import io.github.lopezjavier.appfitness.designsystem.AppActionSheet
import io.github.lopezjavier.appfitness.designsystem.AppSheetAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.OptionPickerSheet
import io.github.lopezjavier.appfitness.designsystem.PickerOption
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.RoutineFolder
import io.github.lopezjavier.appfitness.domain.model.RoutineSummary
import org.jetbrains.compose.resources.stringResource

/** Every sheet and dialog of the routines list: routine actions, folder picker, folder name and the folder delete. */
@Composable
internal fun RoutinesDialogs(state: RoutinesUiState.Content, onEvent: (RoutinesEvent) -> Unit) {
    state.menuFor?.let { RoutineMenuSheet(it, state.isOnline, onEvent) }
    state.folderMenuFor?.let { folder ->
        FolderMenuSheet(folder, state.isOnline, state.routines.any { it.folderId == folder.id }, onEvent)
    }
    state.moveFor?.let { MoveSheet(it, state, onEvent) }
    state.folderSheet?.let { FolderSheet(it, state.folders, state.folderSaving, state.isOnline, onEvent) }
}

/**
 * Actions of one routine (R-07). Offline "Duplicar", "Mover a carpeta" and "Siguiente entreno" stay listed but
 * dimmed (V-40, like the folder menu) and "Eliminar" explains the refusal, with the reason under the title.
 * Reordering is on the card itself (handle, TalkBack "Subir" / "Bajar").
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
        add(
            AppSheetAction(
                icon = Res.drawable.ic_content_copy,
                title = stringResource(Res.string.routine_duplicate),
                enabled = isOnline,
            ) { onEvent(RoutinesEvent.Duplicate(id)) },
        )
        add(
            AppSheetAction(
                icon = Res.drawable.ic_drive_file_move,
                title = stringResource(Res.string.routines_move_to_folder),
                enabled = isOnline,
            ) { onEvent(RoutinesEvent.OpenMove(id)) },
        )
        val nextLabel = stringResource(
            if (routine.isNext) Res.string.routines_next_clear else Res.string.routines_next_set,
        )
        add(
            AppSheetAction(icon = Res.drawable.ic_flag, title = nextLabel, enabled = isOnline) {
                onEvent(if (routine.isNext) RoutinesEvent.ClearNext else RoutinesEvent.SetNext(id))
            },
        )
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

/**
 * Actions of one folder (R-07): "Revisar tus rutinas" (computed on the device, so it works offline), "Renombrar" and
 * "Borrar carpeta" (last, no dialog). Rename and delete need the network, so offline they stay listed but disabled,
 * with the reason under the title. With routines inside, delete is [AppSheetAction.blocked]: dimmed, but the tap
 * explains the refusal (R-20, R-28).
 */
@Composable
private fun FolderMenuSheet(
    folder: RoutineFolder,
    isOnline: Boolean,
    hasRoutines: Boolean,
    onEvent: (RoutinesEvent) -> Unit,
) {
    AppActionSheet(
        actions = listOf(
            AppSheetAction(
                icon = Res.drawable.ic_fact_check,
                title = stringResource(Res.string.routines_folder_menu_review),
                testTag = "routines_folder_menu_review",
            ) { onEvent(RoutinesEvent.ReviewFolder(folder.id)) },
            AppSheetAction(
                icon = Res.drawable.ic_edit,
                title = stringResource(Res.string.routines_folder_menu_rename),
                testTag = "routines_folder_menu_rename",
                enabled = isOnline,
            ) { onEvent(RoutinesEvent.RenameFolder(folder.id)) },
            AppSheetAction(
                icon = Res.drawable.ic_delete,
                title = stringResource(Res.string.routines_folder_menu_delete),
                testTag = "routines_folder_menu_delete",
                enabled = isOnline,
                blocked = hasRoutines,
                blockedDescription = stringResource(Res.string.routines_folder_menu_delete_blocked_cd),
            ) { onEvent(RoutinesEvent.RequestDeleteFolder(folder.id)) },
        ),
        onDismiss = { onEvent(RoutinesEvent.DismissFolderMenu) },
        title = folder.name,
        testTagPrefix = "routines_folder_menu_sheet",
        notice = if (isOnline) null else ({ FolderMenuOfflineReason() }),
    )
}

@Composable
private fun FolderMenuOfflineReason() {
    Text(
        text = stringResource(Res.string.routines_folder_menu_offline_reason),
        style = AppTheme.typography.bodySmall,
        color = AppTheme.colors.textSecondary,
        modifier = Modifier.padding(horizontal = Spacing.screenMargin).testTag("routines_folder_menu_offline_reason"),
    )
}

/**
 * Folder picker (R-64 b, V-37): one tap on a target moves the routine there and closes; the current folder carries the
 * check and a tap on it only closes; X, back or scrim close without moving.
 */
@Composable
private fun MoveSheet(routine: RoutineSummary, state: RoutinesUiState.Content, onEvent: (RoutinesEvent) -> Unit) {
    val currentFolder = state.folders.firstOrNull { it.id == routine.folderId }?.id
    val noneOption = PickerOption(
        tag = "none",
        label = stringResource(Res.string.routines_group_none),
        isSelected = currentFolder == null,
        onPick = { if (currentFolder != null) onEvent(RoutinesEvent.MoveToFolder(routine.id, null)) },
    )
    val folderOptions = state.folders.map { folder ->
        PickerOption(
            tag = folder.id,
            label = folder.name,
            isSelected = folder.id == currentFolder,
            onPick = { if (folder.id != currentFolder) onEvent(RoutinesEvent.MoveToFolder(routine.id, folder.id)) },
        )
    }
    OptionPickerSheet(
        title = stringResource(Res.string.routines_move_sheet_title, routine.name),
        tagPrefix = "routines_move",
        options = listOf(noneOption) + folderOptions,
        onClose = { onEvent(RoutinesEvent.DismissMove) },
    )
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
