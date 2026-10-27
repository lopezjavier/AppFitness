package io.github.lopezjavier.appfitness.feature.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_offline_add_action
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_own_label
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_row_subtitle
import io.github.lopezjavier.appfitness.designsystem.AddItemButton
import io.github.lopezjavier.appfitness.designsystem.ExerciseRow
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.CatalogExercise
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PickerRow(
    item: CatalogExercise,
    selectMode: Boolean,
    mediaUris: ExerciseMediaUris,
    onEvent: (ExercisePickerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val subtitle = stringResource(
        Res.string.exercise_row_subtitle,
        stringResource(item.exercise.muscleGroup.label),
        stringResource(item.exercise.equipment.label),
    )
    val key = item.exercise.key
    ExerciseRow(
        name = item.name,
        supporting = subtitle,
        onOpen = { onEvent(ExercisePickerEvent.OpenDetail(item.id)) },
        tagPrefix = "exercise_picker_row_${item.id}",
        modifier = modifier,
        imageUri = if (item.hasMedia && key != null) mediaUris.start(key) else null,
        onSelect = if (selectMode) {
            { onEvent(ExercisePickerEvent.OpenExercise(item.id)) }
        } else {
            null
        },
    )
}

/** "Añadir ejercicio propio" (disabled offline with the hint). */
@Composable
internal fun CreateOwnExercise(isOnline: Boolean, onEvent: (ExercisePickerEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        AddItemButton(
            objectLabel = stringResource(Res.string.exercise_own_label),
            onClick = { onEvent(ExercisePickerEvent.OpenCreate) },
            modifier = Modifier.testTag("exercise_picker_create_button"),
            enabled = isOnline,
        )
        if (!isOnline) {
            OfflineHint(
                action = stringResource(Res.string.exercise_offline_add_action),
                modifier = Modifier.testTag("exercise_picker_offline_hint"),
            )
        }
    }
}
