package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_pick_empty
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_pick_title
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_pick_title_change
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_search_label
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_search_placeholder
import io.github.lopezjavier.appfitness.composeapp.resources.folder_block_group_option
import io.github.lopezjavier.appfitness.composeapp.resources.folder_block_group_title
import io.github.lopezjavier.appfitness.composeapp.resources.folder_block_routine_title
import io.github.lopezjavier.appfitness.designsystem.AppPickerSheet
import io.github.lopezjavier.appfitness.designsystem.AppSearchField
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.SheetActionRow
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.feature.exercises.label
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FolderBlockPickers(
    state: FolderBlockUiState.Content,
    onEvent: (FolderBlockEvent) -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    val dismiss = { onEvent(FolderBlockEvent.ClosePicker) }
    when (val target = state.pickerTarget) {
        null -> Unit

        FolderPickerTarget.ChooseGroup -> AppPickerSheet(
            title = stringResource(Res.string.folder_block_group_title),
            onDismiss = dismiss,
            testTagPrefix = "folder_block_group_chooser",
        ) {
            state.addableGroups.forEach { row ->
                SheetActionRow(
                    label = stringResource(
                        Res.string.folder_block_group_option,
                        stringResource(row.group.label),
                        row.missingSets,
                    ),
                    onClick = { onEvent(FolderBlockEvent.ChooseGroup(row.group)) },
                    testTag = "folder_block_group_option_${row.group.tagKey()}",
                )
            }
        }

        is FolderPickerTarget.ChooseRoutine -> AppPickerSheet(
            title = stringResource(Res.string.folder_block_routine_title),
            onDismiss = dismiss,
            testTagPrefix = "folder_block_routine_chooser",
        ) {
            state.liftingRoutines.forEach { (ri, name) ->
                SheetActionRow(
                    label = name,
                    onClick = { onEvent(FolderBlockEvent.ChooseRoutine(ri)) },
                    testTag = "folder_block_routine_option_$ri",
                )
            }
        }

        is FolderPickerTarget.Add, is FolderPickerTarget.Swap -> ExercisePicker(
            state = state,
            change = target is FolderPickerTarget.Swap,
            onEvent = onEvent,
            onOpenExercise = onOpenExercise,
        )
    }
}

private fun MuscleGroup.tagKey(): String = name.lowercase()

@Composable
private fun ExercisePicker(
    state: FolderBlockUiState.Content,
    change: Boolean,
    onEvent: (FolderBlockEvent) -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val entries = state.pickerEntries.filter { it.exercise.name.contains(query.trim(), ignoreCase = true) }
    AppPickerSheet(
        title = stringResource(
            if (change) Res.string.block_builder_pick_title_change else Res.string.block_builder_pick_title,
        ),
        onDismiss = { onEvent(FolderBlockEvent.ClosePicker) },
        testTagPrefix = "folder_block_picker",
    ) {
        AppSearchField(
            value = query,
            onValueChange = { query = it },
            label = stringResource(Res.string.exercise_search_label),
            placeholder = stringResource(Res.string.exercise_search_placeholder),
            onSearch = {},
            testTagPrefix = "folder_block_picker_search",
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screenMargin),
        )
        if (entries.isEmpty()) {
            Text(
                text = stringResource(Res.string.block_builder_pick_empty),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.padding(Spacing.screenMargin).testTag("folder_block_pick_empty"),
            )
        }
        Column {
            entries.forEach {
                PickerRow(
                    entry = it,
                    favourite = it.exercise.id in state.favouriteIds,
                    tagPrefix = "folder_block_pick",
                    onSelect = { onEvent(FolderBlockEvent.PickExercise(it.exercise.id)) },
                    onOpenExercise = onOpenExercise,
                )
            }
        }
    }
}
