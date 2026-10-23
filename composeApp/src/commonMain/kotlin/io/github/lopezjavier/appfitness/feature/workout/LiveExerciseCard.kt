package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.builder_exercise_open_cd
import io.github.lopezjavier.appfitness.composeapp.resources.builder_superset_label
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_unnamed
import io.github.lopezjavier.appfitness.composeapp.resources.live_add_set
import io.github.lopezjavier.appfitness.composeapp.resources.live_target_rpe
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.GhostTone
import io.github.lopezjavier.appfitness.designsystem.SetTableHeader
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.feature.exercises.setFieldSpecs
import org.jetbrains.compose.resources.stringResource

/**
 * One exercise of the live session: a heading (superset label and name), the set table and "Añadir serie" at the
 * foot. The heading is the node tagged `live_exercise_<position>`.
 */
@Composable
internal fun LiveExerciseCard(
    exercise: LiveExercise,
    onEvent: (LiveSessionEvent) -> Unit,
    onOpenExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val position = exercise.position
    val name = exercise.name ?: stringResource(Res.string.exercise_unnamed)
    var sheet by rememberSaveable { mutableStateOf(ExerciseSheet.None) }
    AppCard(modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExerciseHeading(exercise, name, onOpenExercise, Modifier.weight(1f))
                ExerciseMenuButton(name, position, onClick = { sheet = ExerciseSheet.Menu })
            }
            exercise.targetRpeText?.let {
                Text(
                    text = stringResource(Res.string.live_target_rpe, it),
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.testTag("live_exercise_${position}_target_rpe"),
                )
            }
            val fields = setFieldSpecs(exercise.metricType, exercise.equipment)
            SetTableHeader(showRpe = true, fields = fields)
            exercise.sets.forEach {
                SessionSetRow(position, it, onEvent, metricType = exercise.metricType, equipment = exercise.equipment)
            }
            GhostButton(
                text = stringResource(Res.string.live_add_set),
                onClick = { onEvent(LiveSessionEvent.AddSet(position)) },
                modifier = Modifier.fillMaxWidth().testTag("live_exercise_${position}_add_set"),
                tone = GhostTone.Action,
            )
        }
    }
    when (sheet) {
        ExerciseSheet.None -> Unit

        ExerciseSheet.Menu -> ExerciseMenuSheet(
            position = position,
            onRest = { sheet = ExerciseSheet.Rest },
            onDismiss = { sheet = ExerciseSheet.None },
        )

        ExerciseSheet.Rest -> ExerciseRestSheet(
            position = position,
            currentSeconds = exercise.restSeconds,
            onSave = {
                onEvent(LiveSessionEvent.SetExerciseRest(position, it))
                sheet = ExerciseSheet.None
            },
            onDismiss = { sheet = ExerciseSheet.None },
        )
    }
}

private enum class ExerciseSheet { None, Menu, Rest }

/** The heading is one tap target (min 48 dp) that opens the exercise; the menu button stays a sibling. */
@Composable
private fun ExerciseHeading(
    exercise: LiveExercise,
    name: String,
    onOpenExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val position = exercise.position
    Box(
        modifier.heightIn(min = Spacing.minTouchTarget)
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(Res.string.builder_exercise_open_cd, name),
                onClick = { onOpenExercise(exercise.exerciseId) },
            )
            .testTag("live_exercise_${position}_open"),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(Modifier.semantics(mergeDescendants = true) { heading() }.testTag("live_exercise_$position")) {
            exercise.supersetLabel?.let {
                Text(
                    text = stringResource(Res.string.builder_superset_label, it.letter.toString(), it.number),
                    style = AppTheme.typography.labelTechnical,
                    color = AppTheme.colors.athleticCyan,
                )
            }
            Text(text = name, style = AppTheme.typography.titleMedium, color = AppTheme.colors.textPrimary)
        }
    }
}
