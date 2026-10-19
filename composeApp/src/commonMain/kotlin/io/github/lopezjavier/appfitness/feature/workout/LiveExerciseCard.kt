package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.builder_superset_label
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_unnamed
import io.github.lopezjavier.appfitness.composeapp.resources.live_add_set
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.GhostTone
import io.github.lopezjavier.appfitness.designsystem.SetTableHeader
import io.github.lopezjavier.appfitness.designsystem.Spacing
import org.jetbrains.compose.resources.stringResource

/**
 * One exercise of the live session: a heading (superset label and name), the set table and "Añadir serie" at the
 * foot. The heading is the node tagged `live_exercise_<position>`.
 */
@Composable
internal fun LiveExerciseCard(
    exercise: LiveExercise,
    onEvent: (LiveSessionEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val position = exercise.position
    AppCard(modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Column(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) { heading() }
                    .testTag("live_exercise_$position"),
            ) {
                exercise.supersetLabel?.let {
                    Text(
                        text = stringResource(Res.string.builder_superset_label, it.letter.toString(), it.number),
                        style = AppTheme.typography.labelTechnical,
                        color = AppTheme.colors.athleticCyan,
                    )
                }
                Text(
                    text = exercise.name ?: stringResource(Res.string.exercise_unnamed),
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.colors.textPrimary,
                )
            }
            SetTableHeader()
            exercise.sets.forEach { SessionSetRow(position, it, onEvent) }
            GhostButton(
                text = stringResource(Res.string.live_add_set),
                onClick = { onEvent(LiveSessionEvent.AddSet(position)) },
                modifier = Modifier.fillMaxWidth().testTag("live_exercise_${position}_add_set"),
                tone = GhostTone.Action,
            )
        }
    }
}
