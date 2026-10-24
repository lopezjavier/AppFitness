package io.github.lopezjavier.appfitness.feature.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_close
import io.github.lopezjavier.appfitness.composeapp.resources.routine_archive
import io.github.lopezjavier.appfitness.composeapp.resources.routine_duplicate
import io.github.lopezjavier.appfitness.composeapp.resources.routine_edit
import io.github.lopezjavier.appfitness.composeapp.resources.routines_archive_cancel
import io.github.lopezjavier.appfitness.composeapp.resources.routines_archive_confirm
import io.github.lopezjavier.appfitness.composeapp.resources.routines_archive_message
import io.github.lopezjavier.appfitness.composeapp.resources.routines_archive_title
import io.github.lopezjavier.appfitness.composeapp.resources.routines_folder_delete_cancel
import io.github.lopezjavier.appfitness.composeapp.resources.routines_folder_delete_confirm
import io.github.lopezjavier.appfitness.composeapp.resources.routines_folder_delete_message
import io.github.lopezjavier.appfitness.composeapp.resources.routines_folder_delete_title
import io.github.lopezjavier.appfitness.composeapp.resources.routines_group_none
import io.github.lopezjavier.appfitness.composeapp.resources.routines_move_close_cd
import io.github.lopezjavier.appfitness.composeapp.resources.routines_move_down
import io.github.lopezjavier.appfitness.composeapp.resources.routines_move_sheet_title
import io.github.lopezjavier.appfitness.composeapp.resources.routines_move_to_folder
import io.github.lopezjavier.appfitness.composeapp.resources.routines_move_up
import io.github.lopezjavier.appfitness.composeapp.resources.routines_next_clear
import io.github.lopezjavier.appfitness.composeapp.resources.routines_next_set
import io.github.lopezjavier.appfitness.composeapp.resources.routines_offline_reason
import io.github.lopezjavier.appfitness.designsystem.AppBottomSheet
import io.github.lopezjavier.appfitness.designsystem.AppConfirmDialog
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.RoutineSummary
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Every sheet and dialog of "Mis rutinas": routine menu, folder picker, folder sheet and the confirmations. */
@Composable
internal fun RoutinesDialogs(state: RoutinesUiState.Content, onEvent: (RoutinesEvent) -> Unit) {
    state.menuFor?.let { RoutineMenuSheet(it, state, onEvent) }
    state.moveFor?.let { MoveSheet(it, state, onEvent) }
    state.folderSheet?.let { FolderSheet(it, onEvent) }
    state.archiveFor?.let { routine ->
        AppConfirmDialog(
            title = stringResource(Res.string.routines_archive_title, routine.name),
            message = stringResource(Res.string.routines_archive_message),
            confirmLabel = stringResource(Res.string.routines_archive_confirm),
            cancelLabel = stringResource(Res.string.routines_archive_cancel),
            onConfirm = { onEvent(RoutinesEvent.ConfirmArchive) },
            onDismiss = { onEvent(RoutinesEvent.DismissArchive) },
            testTagPrefix = "routines_archive",
        )
    }
    state.deleteFolderFor?.let { folder ->
        val count = state.deleteFolderRoutineCount
        AppConfirmDialog(
            title = stringResource(Res.string.routines_folder_delete_title, folder.name),
            message = pluralStringResource(Res.plurals.routines_folder_delete_message, count, count),
            confirmLabel = stringResource(Res.string.routines_folder_delete_confirm),
            cancelLabel = stringResource(Res.string.routines_folder_delete_cancel),
            onConfirm = { onEvent(RoutinesEvent.ConfirmDeleteFolder) },
            onDismiss = { onEvent(RoutinesEvent.DismissDeleteFolder) },
            testTagPrefix = "routines_folder_delete",
        )
    }
}

@Composable
private fun RoutineMenuSheet(
    routine: RoutineSummary,
    state: RoutinesUiState.Content,
    onEvent: (RoutinesEvent) -> Unit,
) {
    val siblings = state.groups.firstOrNull { group -> group.routines.any { it.id == routine.id } }?.routines.orEmpty()
    val position = siblings.indexOfFirst { it.id == routine.id }
    val id = routine.id
    ActionsSheet(
        title = routine.name,
        onDismiss = { onEvent(RoutinesEvent.DismissMenu) },
        testTagPrefix = "routines_menu",
    ) {
        SheetActionRow(stringResource(Res.string.routine_edit), {
            onEvent(RoutinesEvent.Edit(id))
        }, "routines_menu_edit_$id")
        SheetActionRow(
            label = stringResource(Res.string.routine_duplicate),
            onClick = { onEvent(RoutinesEvent.Duplicate(id)) },
            testTag = "routines_menu_duplicate_$id",
            enabled = state.isOnline,
        )
        SheetActionRow(
            label = stringResource(Res.string.routines_move_to_folder),
            onClick = { onEvent(RoutinesEvent.OpenMove(id)) },
            testTag = "routines_menu_move_$id",
            enabled = state.isOnline,
        )
        SheetActionRow(
            label = stringResource(
                if (routine.isNext) Res.string.routines_next_clear else Res.string.routines_next_set,
            ),
            onClick = { onEvent(if (routine.isNext) RoutinesEvent.ClearNext else RoutinesEvent.SetNext(id)) },
            testTag = "routines_menu_next_$id",
            enabled = state.isOnline,
        )
        SheetActionRow(
            label = stringResource(Res.string.routines_move_up),
            onClick = { onEvent(RoutinesEvent.MoveUp(id)) },
            testTag = "routines_menu_up_$id",
            enabled = state.isOnline && position > 0,
        )
        SheetActionRow(
            label = stringResource(Res.string.routines_move_down),
            onClick = { onEvent(RoutinesEvent.MoveDown(id)) },
            testTag = "routines_menu_down_$id",
            enabled = state.isOnline && position in 0 until siblings.lastIndex,
        )
        SheetActionRow(
            label = stringResource(Res.string.routine_archive),
            onClick = { onEvent(RoutinesEvent.RequestArchive(id)) },
            testTag = "routines_menu_archive_$id",
            enabled = state.isOnline,
        )
        if (!state.isOnline) OfflineReason()
    }
}

/** Folder picker: one tap on a target moves the routine there and closes; X, back or scrim close without moving. */
@Composable
@OptIn(ExperimentalMaterial3Api::class) // The shared sheet exposes a Material SheetState default.
private fun MoveSheet(routine: RoutineSummary, state: RoutinesUiState.Content, onEvent: (RoutinesEvent) -> Unit) {
    val dismiss = { onEvent(RoutinesEvent.DismissMove) }
    AppBottomSheet(onDismissRequest = dismiss, testTagPrefix = "routines_move_sheet") {
        Column(
            Modifier.padding(horizontal = Spacing.screenMargin).padding(bottom = Spacing.lg).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(Res.string.routines_move_sheet_title, routine.name),
                    style = AppTheme.typography.titleLarge,
                    color = AppTheme.colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                AppIconButton(
                    contentDescription = stringResource(Res.string.routines_move_close_cd),
                    icon = Res.drawable.ic_close,
                    testTag = "routines_move_sheet_dismiss",
                    onClick = dismiss,
                )
            }
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
}

@Composable
private fun OfflineReason() {
    Text(
        text = stringResource(Res.string.routines_offline_reason),
        style = AppTheme.typography.bodySmall,
        color = AppTheme.colors.textSecondary,
        modifier = Modifier.padding(top = Spacing.sm).testTag("routines_menu_offline_reason"),
    )
}
