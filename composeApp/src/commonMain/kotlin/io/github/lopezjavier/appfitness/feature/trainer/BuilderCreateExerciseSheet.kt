package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_create_failed
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_create_equipment_label
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_create_name_label
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_create_title
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_offline_add_action
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_per_hand_label
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_per_hand_off
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_per_hand_on
import io.github.lopezjavier.appfitness.composeapp.resources.movement_pattern_label
import io.github.lopezjavier.appfitness.designsystem.AppEditSheet
import io.github.lopezjavier.appfitness.designsystem.AppOption
import io.github.lopezjavier.appfitness.designsystem.AppSwitch
import io.github.lopezjavier.appfitness.designsystem.AppTextField
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.FilterChipRow
import io.github.lopezjavier.appfitness.designsystem.SheetCommit
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.offlineHint
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.MovementPattern
import io.github.lopezjavier.appfitness.feature.exercises.label
import io.github.lopezjavier.appfitness.feature.exercises.tagName
import io.github.lopezjavier.appfitness.feature.food.FieldErrorText
import org.jetbrains.compose.resources.stringResource

private const val TAG = "block_builder_create"

/**
 * "Nuevo ejercicio" from a builder slot: name, equipment and a movement pattern taken from [patterns] (the ones the
 * slot accepts) are required; the muscle group is the slot's. "Añadir" sends [BlockBuilderEvent.CreateCustomExercise];
 * the sheet stays open and shows an error when [createFailed]. Disabled offline with the hint.
 */
@Composable
internal fun BuilderCreateExerciseSheet(
    patterns: List<MovementPattern>,
    createFailed: Boolean,
    online: Boolean,
    onEvent: (BlockBuilderEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var equipmentIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var patternIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var perHand by rememberSaveable { mutableStateOf(false) }
    val equipment = equipmentIndex?.let { Equipment.entries[it] }
    val pattern = patternIndex?.let { patterns.getOrNull(it) }
    AppEditSheet(
        title = stringResource(Res.string.exercise_create_title),
        commit = SheetCommit.Add,
        onCommit = {
            onEvent(BlockBuilderEvent.CreateCustomExercise(name.trim(), checkNotNull(equipment), pattern, perHand))
        },
        onDismiss = onDismiss,
        commitEnabled = online && name.isNotBlank() && equipment != null && pattern != null,
        commitHint = if (online) null else offlineHint(stringResource(Res.string.exercise_offline_add_action)),
        testTagPrefix = "${TAG}_sheet",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
            AppTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(Res.string.exercise_create_name_label),
                testTagPrefix = "${TAG}_name",
            )
            ChipChoice(
                label = stringResource(Res.string.exercise_create_equipment_label),
                options = Equipment.entries.map {
                    AppOption(stringResource(it.label), "${TAG}_equipment_${it.tagName()}")
                },
                selected = equipmentIndex,
                onSelect = { equipmentIndex = it },
            )
            ChipChoice(
                label = stringResource(Res.string.movement_pattern_label),
                options = patterns.map { AppOption(stringResource(it.label), "${TAG}_pattern_${it.tagName()}") },
                selected = patternIndex,
                onSelect = { patternIndex = it },
            )
            PerHandRow(perHand) { perHand = it }
            if (createFailed) FieldErrorText(stringResource(Res.string.block_builder_create_failed), "${TAG}_error")
        }
    }
}

@Composable
private fun ChipChoice(label: String, options: List<AppOption>, selected: Int?, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(text = label, style = AppTheme.typography.titleMedium, color = AppTheme.colors.textPrimary)
        FilterChipRow(
            options = options,
            selectedIndex = selected,
            onSelect = onSelect,
            edgeToEdge = Spacing.screenMargin,
        )
    }
}

@Composable
private fun PerHandRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    val label = stringResource(Res.string.exercise_per_hand_label)
    Row(
        Modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = label,
            style = AppTheme.typography.titleMedium,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        AppSwitch(
            checked = checked,
            onCheckedChange = onChange,
            contentDescription = label,
            stateDescription = stringResource(
                if (checked) Res.string.exercise_per_hand_on else Res.string.exercise_per_hand_off,
            ),
            modifier = Modifier.testTag("${TAG}_per_hand_switch"),
        )
    }
}
