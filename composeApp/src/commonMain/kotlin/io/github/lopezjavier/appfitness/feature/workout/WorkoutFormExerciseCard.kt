@file:Suppress("MatchingDeclarationName") // The file holds the card and its state holder.

package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.builder_add_set
import io.github.lopezjavier.appfitness.composeapp.resources.builder_rest_decrease_cd
import io.github.lopezjavier.appfitness.composeapp.resources.builder_rest_increase_cd
import io.github.lopezjavier.appfitness.composeapp.resources.builder_rest_unit
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_unnamed
import io.github.lopezjavier.appfitness.composeapp.resources.ic_arrow_downward
import io.github.lopezjavier.appfitness.composeapp.resources.ic_arrow_upward
import io.github.lopezjavier.appfitness.composeapp.resources.ic_delete
import io.github.lopezjavier.appfitness.composeapp.resources.note_edit
import io.github.lopezjavier.appfitness.composeapp.resources.workout_form_exercise_down_cd
import io.github.lopezjavier.appfitness.composeapp.resources.workout_form_exercise_remove_cd
import io.github.lopezjavier.appfitness.composeapp.resources.workout_form_exercise_up_cd
import io.github.lopezjavier.appfitness.composeapp.resources.workout_form_set_error
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.InlineNote
import io.github.lopezjavier.appfitness.designsystem.NumberStepper
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.setNoteSummary
import io.github.lopezjavier.appfitness.domain.model.RoutineBounds
import io.github.lopezjavier.appfitness.domain.model.WorkoutDraftBounds
import org.jetbrains.compose.resources.stringResource

/** What a card needs to know about its place in the list and what the form flags on it. */
internal data class FormCardState(
    val index: Int,
    val isFirst: Boolean,
    val isLast: Boolean,
    /** 0-based indices of the sets with an invalid value that should be flagged. */
    val flaggedSets: Set<Int>,
)

/**
 * One exercise of the form: name with up / down / remove icon buttons, the sets, "Añadir serie" and the rest stepper.
 * Tags are `<prefix>_exercise_<index>_up|down|delete`, `<prefix>_add_set_<index>` and `<prefix>_rest_<index>`.
 */
@Composable
@Suppress("LongParameterList", "LongMethod") // Row data, its card state, tags and the picker hooks.
internal fun FormExerciseCard(
    exercise: FormExercise,
    name: String?,
    card: FormCardState,
    tagPrefix: String,
    onEvent: (WorkoutFormEvent) -> Unit,
    onOpenTypePicker: (setIndex: Int) -> Unit,
    onOpenRpePicker: (setIndex: Int) -> Unit,
    onOpenNote: (setIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = name ?: stringResource(Res.string.exercise_unnamed)
    val index = card.index
    AppCard(modifier.fillMaxWidth().testTag("${tagPrefix}_exercise_$index")) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                AppIconButton(
                    contentDescription = stringResource(Res.string.workout_form_exercise_up_cd, title),
                    icon = Res.drawable.ic_arrow_upward,
                    testTag = "${tagPrefix}_exercise_${index}_up",
                    onClick = { onEvent(WorkoutFormEvent.MoveExercise(index, index - 1)) },
                    enabled = !card.isFirst,
                )
                AppIconButton(
                    contentDescription = stringResource(Res.string.workout_form_exercise_down_cd, title),
                    icon = Res.drawable.ic_arrow_downward,
                    testTag = "${tagPrefix}_exercise_${index}_down",
                    onClick = { onEvent(WorkoutFormEvent.MoveExercise(index, index + 1)) },
                    enabled = !card.isLast,
                )
                AppIconButton(
                    contentDescription = stringResource(Res.string.workout_form_exercise_remove_cd, title),
                    icon = Res.drawable.ic_delete,
                    testTag = "${tagPrefix}_exercise_${index}_delete",
                    onClick = { onEvent(WorkoutFormEvent.RemoveExercise(index)) },
                )
            }
            FormSetHeader(exercise)
            exercise.sets.forEachIndexed { setIndex, set ->
                key(set.setId) {
                    FormSetRow(
                        exercise = exercise,
                        index = index,
                        setIndex = setIndex,
                        canRemove = exercise.sets.size > 1,
                        tagPrefix = tagPrefix,
                        onEvent = onEvent,
                        onOpenTypePicker = { onOpenTypePicker(setIndex) },
                        onOpenRpePicker = { onOpenRpePicker(setIndex) },
                        onOpenNote = { onOpenNote(setIndex) },
                    )
                    setNoteSummary(set.noteKind, set.notes)?.let {
                        InlineNote(
                            text = it,
                            testTag = "${tagPrefix}_set_note_${index}_$setIndex",
                            onClick = { onOpenNote(setIndex) },
                            onClickLabel = stringResource(Res.string.note_edit),
                        )
                    }
                    if (setIndex in card.flaggedSets) {
                        Text(
                            text = stringResource(Res.string.workout_form_set_error, setIndex + 1),
                            style = AppTheme.typography.bodySmall,
                            color = AppTheme.colors.error,
                            modifier = Modifier.testTag("${tagPrefix}_set_${index}_${setIndex}_error"),
                        )
                    }
                }
            }
            GhostButton(
                text = stringResource(Res.string.builder_add_set),
                onClick = { onEvent(WorkoutFormEvent.AddSet(index)) },
                modifier = Modifier.fillMaxWidth().testTag("${tagPrefix}_add_set_$index"),
                enabled = exercise.sets.size < WorkoutDraftBounds.SETS_MAX,
            )
            RestLine(exercise.restSeconds, index, tagPrefix, onEvent)
        }
    }
}

@Composable
private fun RestLine(restSeconds: Int, index: Int, tagPrefix: String, onEvent: (WorkoutFormEvent) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.builder_rest_unit),
            style = AppTheme.typography.labelTechnical,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        NumberStepper(
            valueText = restDurationText(restSeconds),
            onDecrement = {
                onEvent(WorkoutFormEvent.RestChanged(index, restSeconds - RoutineBounds.REST_STEP_SECONDS))
            },
            onIncrement = {
                onEvent(WorkoutFormEvent.RestChanged(index, restSeconds + RoutineBounds.REST_STEP_SECONDS))
            },
            decrementEnabled = restSeconds > RoutineBounds.REST_MIN_SECONDS,
            incrementEnabled = restSeconds < RoutineBounds.REST_MAX_SECONDS,
            decrementContentDescription = stringResource(Res.string.builder_rest_decrease_cd),
            incrementContentDescription = stringResource(Res.string.builder_rest_increase_cd),
            stateDescription = restStateDescription(restSeconds),
            testTagPrefix = "${tagPrefix}_rest_$index",
        )
    }
}
