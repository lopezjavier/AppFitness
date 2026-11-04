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
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_offline_add_action
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_own_label
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_row_subtitle
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_search_label
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_search_placeholder
import io.github.lopezjavier.appfitness.designsystem.AddItemButton
import io.github.lopezjavier.appfitness.designsystem.AppPickerSheet
import io.github.lopezjavier.appfitness.designsystem.AppSearchField
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ExerciseRow
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.coach.builder.PickerEntry
import io.github.lopezjavier.appfitness.feature.exercises.label
import org.jetbrains.compose.resources.stringResource

/**
 * The exercises of one builder slot (R-12/R-17/R-48): only the ones the ViewModel lists for the slot's muscle and the
 * user's equipment. Tapping a row picks it and closes; the thumbnail opens its details. A row that cannot be picked
 * stays listed and says why (never hidden). Searching filters locally by name.
 */
@Composable
internal fun BuilderSlotPickerSheet(
    state: BlockBuilderUiState.Builder,
    slot: SlotRef,
    onEvent: (BlockBuilderEvent) -> Unit,
    onOpenExercise: (String) -> Unit,
    online: Boolean = true,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var creating by rememberSaveable { mutableStateOf(false) }
    val filled = !slot.extra && state.state.days[slot.day].slots.getOrNull(slot.index)?.pick != null
    val title = if (filled) {
        stringResource(Res.string.block_builder_pick_title_change)
    } else {
        stringResource(Res.string.block_builder_pick_title)
    }
    val entries = state.pickerEntries.filter { it.exercise.name.contains(query.trim(), ignoreCase = true) }
    AppPickerSheet(
        title = title,
        onDismiss = { onEvent(BlockBuilderEvent.OpenSlot(null)) },
        testTagPrefix = "block_builder_picker",
        footer = { AddCustomFooter(online) { creating = true } },
    ) {
        AppSearchField(
            value = query,
            onValueChange = { query = it },
            label = stringResource(Res.string.exercise_search_label),
            placeholder = stringResource(Res.string.exercise_search_placeholder),
            onSearch = {},
            testTagPrefix = "block_builder_picker_search",
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screenMargin),
        )
        if (entries.isEmpty()) {
            Text(
                text = stringResource(Res.string.block_builder_pick_empty),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.padding(Spacing.screenMargin).testTag("block_builder_pick_empty"),
            )
        }
        Column {
            entries.forEach { PickerRow(it, it.exercise.id in state.favouriteIds, onEvent, onOpenExercise) }
        }
    }
    if (creating) {
        BuilderCreateExerciseSheet(
            patterns = state.validPatterns,
            createFailed = state.createFailed,
            online = online,
            onEvent = onEvent,
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun PickerRow(
    entry: PickerEntry,
    favourite: Boolean,
    onEvent: (BlockBuilderEvent) -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    val item = entry.exercise
    val supporting = entry.disabledReason?.let { pickerReasonText(it) } ?: stringResource(
        Res.string.exercise_row_subtitle,
        stringResource(item.exercise.muscleGroup.label),
        stringResource(item.exercise.equipment.label),
    )
    ExerciseRow(
        name = item.name,
        supporting = supporting,
        onOpen = { onOpenExercise(item.id) },
        tagPrefix = "block_builder_pick_${item.id}",
        imageUri = item.rowImageUri(),
        onSelect = { onEvent(BlockBuilderEvent.PickExercise(item.id)) },
        enabled = !entry.disabled,
        favorite = favourite,
        favoriteTestTag = "block_builder_pick_favorite_${item.id}",
    )
}

@Composable
private fun AddCustomFooter(online: Boolean, onAdd: () -> Unit) {
    Column {
        AddItemButton(
            objectLabel = stringResource(Res.string.exercise_own_label),
            onClick = onAdd,
            modifier = Modifier.testTag("block_builder_pick_add_custom"),
            enabled = online,
        )
        if (!online) {
            OfflineHint(
                action = stringResource(Res.string.exercise_offline_add_action),
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}
