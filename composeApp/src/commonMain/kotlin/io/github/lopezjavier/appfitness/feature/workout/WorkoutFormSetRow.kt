package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_remove
import io.github.lopezjavier.appfitness.composeapp.resources.builder_set_delete_cd
import io.github.lopezjavier.appfitness.composeapp.resources.builder_set_type_cd
import io.github.lopezjavier.appfitness.composeapp.resources.set_header_rpe
import io.github.lopezjavier.appfitness.composeapp.resources.workout_form_rpe_cd
import io.github.lopezjavier.appfitness.composeapp.resources.workout_form_rpe_value_cd
import io.github.lopezjavier.appfitness.composeapp.resources.workout_form_seconds_cd
import io.github.lopezjavier.appfitness.composeapp.resources.workout_form_seconds_header
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.NumberInput
import io.github.lopezjavier.appfitness.designsystem.SetFieldKind
import io.github.lopezjavier.appfitness.designsystem.SetNoteButton
import io.github.lopezjavier.appfitness.designsystem.SetOrdinal
import io.github.lopezjavier.appfitness.designsystem.SetRowDefaults
import io.github.lopezjavier.appfitness.designsystem.SetTypeBadge
import io.github.lopezjavier.appfitness.designsystem.SetTypeBadgeDefaults
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.SwipeToDeleteRow
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.usecase.RpeScale
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The kind of input a [SetField] is for an exercise of [metric] (assisted and weighted loads are labelled apart). */
internal fun SetField.kind(metric: ExerciseMetricType): SetFieldKind = when (this) {
    SetField.Weight -> when (metric) {
        ExerciseMetricType.Weighted -> SetFieldKind.AddedWeight
        ExerciseMetricType.Assisted -> SetFieldKind.AssistWeight
        else -> SetFieldKind.Weight
    }

    SetField.Reps -> SetFieldKind.Reps

    SetField.Duration -> SetFieldKind.Duration

    SetField.Distance -> SetFieldKind.Distance

    SetField.Steps -> SetFieldKind.Steps

    SetField.Floors -> SetFieldKind.Floors
}

/** Suffix of the test tag of the input of this field. */
internal fun SetField.tagPart(): String = when (this) {
    SetField.Weight -> "weight"
    SetField.Reps -> "reps"
    SetField.Duration -> "duration"
    SetField.Distance -> "distance"
    SetField.Steps -> "steps"
    SetField.Floors -> "floors"
}

private val RpeCellWidth = Spacing.minTouchTarget + Spacing.sm

/** Column header of the set rows of one exercise: the kind of each field, then RPE. */
@Composable
internal fun FormSetHeader(exercise: FormExercise) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Spacer(Modifier.width(Spacing.minTouchTarget))
        exercise.fields.forEach { field ->
            val header: StringResource = field.kind(exercise.metricType).header
            Text(
                text = if (field == SetField.Duration) {
                    stringResource(Res.string.workout_form_seconds_header)
                } else {
                    stringResource(header)
                }.uppercase(),
                style = AppTheme.typography.labelTechnical,
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = stringResource(Res.string.set_header_rpe),
            style = AppTheme.typography.labelTechnical,
            color = AppTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(RpeCellWidth),
        )
        Spacer(Modifier.width(Spacing.minTouchTarget))
    }
}

/**
 * One set of the form: type badge (opens the type picker), one input per field of the exercise's metric type, the RPE
 * cell (opens the RPE picker) and a delete button; a swipe deletes too. Nothing is written until the screen's Guardar.
 * Tags are `<prefix>_set_<exercise>_<set>_<part>`.
 */
@Composable
@Suppress("LongParameterList") // Row data, its position, tags and the event hooks.
internal fun FormSetRow(
    exercise: FormExercise,
    index: Int,
    setIndex: Int,
    canRemove: Boolean,
    tagPrefix: String,
    onEvent: (WorkoutFormEvent) -> Unit,
    onOpenTypePicker: () -> Unit,
    onOpenRpePicker: () -> Unit,
    onOpenNote: () -> Unit,
) {
    val set = exercise.sets[setIndex]
    val number = setIndex + 1
    val tag = "${tagPrefix}_set_${index}_$setIndex"
    val delete = stringResource(Res.string.builder_set_delete_cd, number)
    val typeLabel = stringResource(SetTypeBadgeDefaults.labelResource(set.type))
    SwipeToDeleteRow(
        deleteLabel = delete,
        enabled = canRemove,
        onDelete = { onEvent(WorkoutFormEvent.RemoveSet(index, setIndex)) },
        backgroundTag = "${tag}_delete_background",
        label = stringResource(Res.string.action_remove),
        modifier = Modifier.testTag("${tag}_row"),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SetOrdinal(number.toString())
            SetTypeBadge(
                type = set.type,
                modifier = Modifier.testTag("${tag}_type"),
                onClick = onOpenTypePicker,
                clickLabel = stringResource(Res.string.builder_set_type_cd, number, typeLabel),
            )
            exercise.fields.forEachIndexed { position, field ->
                val kind = field.kind(exercise.metricType)
                val label = kind.rowDescription?.let { stringResource(it, number) }
                    ?: stringResource(Res.string.workout_form_seconds_cd, number)
                NumberInput(
                    value = set.text(field),
                    onValueChange = { onEvent(WorkoutFormEvent.ValueChanged(index, setIndex, field, it)) },
                    label = label,
                    modifier = Modifier.weight(1f).testTag("${tag}_${field.tagPart()}"),
                    maxDecimals = kind.maxDecimals,
                    maxIntegerDigits = kind.maxIntegerDigits,
                    showLabel = false,
                    imeAction = if (position == exercise.fields.lastIndex) ImeAction.Done else ImeAction.Next,
                )
            }
            RpeCell(set.rpe, number, onOpenRpePicker, Modifier.testTag("${tag}_rpe"))
            SetNoteButton(
                setNumber = number,
                hasNote = set.noteKind != null,
                testTag = "${tagPrefix}_set_note_action_${index}_$setIndex",
                onClick = onOpenNote,
            )
        }
    }
}

@Composable
private fun RpeCell(rpe: Double?, number: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = if (rpe == null) {
        stringResource(Res.string.workout_form_rpe_cd, number)
    } else {
        stringResource(Res.string.workout_form_rpe_value_cd, number, RpeScale.text(rpe))
    }
    Box(
        modifier
            .width(RpeCellWidth)
            .heightIn(min = SetRowDefaults.MinHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = rpe?.let(RpeScale::text) ?: stringResource(Res.string.set_header_rpe),
            style = AppTheme.typography.labelNumeric,
            color = if (rpe == null) AppTheme.colors.ghostData else AppTheme.colors.textPrimary,
        )
    }
}
