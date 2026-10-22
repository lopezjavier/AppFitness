package io.github.lopezjavier.appfitness.feature.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.builder_add_set
import io.github.lopezjavier.appfitness.composeapp.resources.builder_set_delete_cd
import io.github.lopezjavier.appfitness.composeapp.resources.builder_set_hint_kg
import io.github.lopezjavier.appfitness.composeapp.resources.builder_set_hint_reps
import io.github.lopezjavier.appfitness.composeapp.resources.builder_set_reps_cd
import io.github.lopezjavier.appfitness.composeapp.resources.builder_set_type_cd
import io.github.lopezjavier.appfitness.composeapp.resources.builder_set_weight_cd
import io.github.lopezjavier.appfitness.composeapp.resources.builder_superset_sets_hint
import io.github.lopezjavier.appfitness.composeapp.resources.ic_delete
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.NumberInput
import io.github.lopezjavier.appfitness.designsystem.SetRowDefaults
import io.github.lopezjavier.appfitness.designsystem.SetTypeBadge
import io.github.lopezjavier.appfitness.designsystem.SetTypeBadgeDefaults
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.SwipeToDeleteRow
import org.jetbrains.compose.resources.stringResource

/** The planned sets of a row (badge, weight, reps, delete), their column header and "Añadir serie". */
@Composable
internal fun PlannedSets(row: BuilderRowUi, index: Int, onEvent: (RoutineBuilderEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SetColumnsHeader()
        row.plannedSets.forEachIndexed { setIndex, set ->
            key(set.setId) { PlannedSetRow(row, index, setIndex, set, onEvent) }
        }
        GhostButton(
            text = stringResource(Res.string.builder_add_set),
            onClick = { onEvent(RoutineBuilderEvent.AddSet(index)) },
            modifier = Modifier.fillMaxWidth().testTag("routine_builder_add_set_$index"),
            enabled = row.canAddSet,
        )
        if (row.supersetLabel != null) {
            Text(
                text = stringResource(Res.string.builder_superset_sets_hint),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.testTag("routine_builder_superset_sets_hint_$index"),
            )
        }
    }
}

@Composable
private fun SetColumnsHeader() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Spacer(Modifier.width(Spacing.minTouchTarget))
        listOf(Res.string.builder_set_hint_kg, Res.string.builder_set_hint_reps).forEach {
            Text(
                text = stringResource(it).uppercase(),
                style = AppTheme.typography.labelTechnical,
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.width(Spacing.minTouchTarget))
    }
}

@Composable
@Suppress("LongParameterList") // Row, its position, the set and the event hook.
private fun PlannedSetRow(
    row: BuilderRowUi,
    index: Int,
    setIndex: Int,
    set: PlannedSetUi,
    onEvent: (RoutineBuilderEvent) -> Unit,
) {
    val number = setIndex + 1
    val tag = "${index}_$setIndex"
    val typeLabel = stringResource(SetTypeBadgeDefaults.labelResource(set.type))
    val delete = stringResource(Res.string.builder_set_delete_cd, number)
    SwipeToDeleteRow(
        deleteLabel = delete,
        enabled = row.canRemoveSet,
        onDelete = { onEvent(RoutineBuilderEvent.RemoveSet(index, setIndex)) },
        backgroundTag = "routine_builder_set_row_delete_background_$tag",
        modifier = Modifier.testTag("routine_builder_set_row_$tag"),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SetTypeBadge(
                type = set.type,
                number = number.toString(),
                modifier = Modifier.testTag("routine_builder_set_type_$tag"),
                onClick = { onEvent(RoutineBuilderEvent.OpenTypePicker(index, setIndex)) },
                clickLabel = stringResource(Res.string.builder_set_type_cd, number, typeLabel),
            )
            NumberInput(
                value = set.weight,
                onValueChange = { onEvent(RoutineBuilderEvent.SetWeight(index, setIndex, it)) },
                label = stringResource(Res.string.builder_set_weight_cd, number),
                modifier = Modifier.weight(1f).testTag("routine_builder_set_weight_$tag"),
                maxDecimals = SetRowDefaults.WEIGHT_MAX_DECIMALS,
                maxIntegerDigits = SetRowDefaults.WEIGHT_MAX_INTEGER_DIGITS,
                showLabel = false,
                imeAction = ImeAction.Next,
            )
            NumberInput(
                value = set.reps,
                onValueChange = { onEvent(RoutineBuilderEvent.SetReps(index, setIndex, it)) },
                label = stringResource(Res.string.builder_set_reps_cd, number),
                modifier = Modifier.weight(1f).testTag("routine_builder_set_reps_$tag"),
                maxDecimals = SetRowDefaults.REPS_MAX_DECIMALS,
                maxIntegerDigits = SetRowDefaults.REPS_MAX_INTEGER_DIGITS,
                showLabel = false,
                imeAction = ImeAction.Done,
            )
            AppIconButton(
                contentDescription = delete,
                icon = Res.drawable.ic_delete,
                testTag = "routine_builder_set_delete_$tag",
                onClick = { onEvent(RoutineBuilderEvent.RemoveSet(index, setIndex)) },
                enabled = row.canRemoveSet,
            )
        }
    }
}
