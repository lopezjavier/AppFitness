package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.block_start_folder
import io.github.lopezjavier.appfitness.composeapp.resources.block_start_folder_hint
import io.github.lopezjavier.appfitness.composeapp.resources.block_start_folders_empty
import io.github.lopezjavier.appfitness.composeapp.resources.block_start_folders_empty_reason
import io.github.lopezjavier.appfitness.composeapp.resources.block_start_folders_error
import io.github.lopezjavier.appfitness.composeapp.resources.block_start_folders_offline
import io.github.lopezjavier.appfitness.composeapp.resources.block_start_folders_title
import io.github.lopezjavier.appfitness.composeapp.resources.block_start_scratch
import io.github.lopezjavier.appfitness.composeapp.resources.block_start_scratch_hint
import io.github.lopezjavier.appfitness.composeapp.resources.block_start_title
import io.github.lopezjavier.appfitness.composeapp.resources.ic_folder
import io.github.lopezjavier.appfitness.designsystem.AppListRow
import io.github.lopezjavier.appfitness.designsystem.AppPickerSheet
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.SheetActionRow
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import org.jetbrains.compose.resources.stringResource

/**
 * The sheets of the block's start choice (US-93, R-12): "Desde cero / Desde una carpeta", then the folders to pick.
 * A tap applies and the model swaps the sheet; closing (X, back, scrim) leaves without choosing anything.
 */
@Composable
internal fun BlockStartSheets(state: BeforeStartUiState.Content, onEvent: (BeforeStartEvent) -> Unit) {
    when (state.startChoice) {
        StartChoiceStep.None -> Unit

        StartChoiceStep.Choice -> AppPickerSheet(
            title = stringResource(Res.string.block_start_title),
            onDismiss = { onEvent(BeforeStartEvent.CloseChoice) },
            testTagPrefix = "block_start_sheet",
        ) {
            AppListRow(
                headline = stringResource(Res.string.block_start_scratch),
                supporting = stringResource(Res.string.block_start_scratch_hint),
                onClick = { onEvent(BeforeStartEvent.PickStart(StartOption.Scratch)) },
                modifier = Modifier.testTag("block_start_option_scratch"),
            )
            AppListRow(
                headline = stringResource(Res.string.block_start_folder),
                supporting = stringResource(Res.string.block_start_folder_hint),
                onClick = { onEvent(BeforeStartEvent.PickStart(StartOption.Folder)) },
                modifier = Modifier.testTag("block_start_option_folder"),
            )
        }

        StartChoiceStep.Folders -> AppPickerSheet(
            title = stringResource(Res.string.block_start_folders_title),
            onDismiss = { onEvent(BeforeStartEvent.CloseChoice) },
            testTagPrefix = "block_start_folders_sheet",
        ) {
            FolderPicker(state.folderPicker, onEvent)
        }
    }
}

@Composable
private fun FolderPicker(picker: FolderPickerState, onEvent: (BeforeStartEvent) -> Unit) {
    when (picker) {
        FolderPickerState.Loading -> SkeletonBox(
            Modifier
                .fillMaxWidth()
                .padding(Spacing.screenMargin)
                .height(Spacing.minTouchTarget)
                .testTag("block_start_folders_loading"),
        )

        is FolderPickerState.Failed -> ErrorState(
            message = stringResource(
                if (picker.offline) Res.string.block_start_folders_offline else Res.string.block_start_folders_error,
            ),
            action = AppStateAction(stringResource(Res.string.action_retry)) {
                onEvent(BeforeStartEvent.RetryFolders)
            },
            testTagPrefix = "block_start_folders_error",
            actionTestTag = "block_start_folders_retry",
        )

        is FolderPickerState.Loaded -> if (picker.folders.isEmpty()) {
            EmptyState(
                icon = Res.drawable.ic_folder,
                message = stringResource(Res.string.block_start_folders_empty_reason),
                title = stringResource(Res.string.block_start_folders_empty),
                testTagPrefix = "block_start_folders_empty",
            )
        } else {
            picker.folders.forEach { folder ->
                SheetActionRow(
                    label = folder.name,
                    onClick = { onEvent(BeforeStartEvent.PickFolder(folder.id)) },
                    testTag = "block_start_folder_${folder.id}",
                )
            }
        }
    }
}
