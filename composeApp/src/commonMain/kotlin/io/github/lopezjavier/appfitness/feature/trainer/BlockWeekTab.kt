package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.block_exercise_detail
import io.github.lopezjavier.appfitness.composeapp.resources.block_exercise_fallback
import io.github.lopezjavier.appfitness.composeapp.resources.block_reason_cd
import io.github.lopezjavier.appfitness.composeapp.resources.block_reps_range
import io.github.lopezjavier.appfitness.composeapp.resources.block_reps_single
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppListRow
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ExerciseRow
import io.github.lopezjavier.appfitness.designsystem.HelpTriggerButton
import io.github.lopezjavier.appfitness.domain.model.BlockExercise
import io.github.lopezjavier.appfitness.domain.model.BlockSession
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.feature.exercises.label
import io.github.lopezjavier.appfitness.feature.workout.restDurationText
import org.jetbrains.compose.resources.stringResource

/** The muscle group stored under [key] (`full_body`, `chest`...), or null for a key this app does not know. */
internal fun muscleGroupOfKey(key: String): MuscleGroup? =
    MuscleGroup.entries.firstOrNull { it.name.lowercase() == key.replace("_", "").lowercase() }

/** The Spanish name of the muscle group stored under [key]; an unknown key reads as it was stored. */
@Composable
internal fun blockMuscleName(key: String): String = muscleGroupOfKey(key)?.let { stringResource(it.label) } ?: key

/** One week of the block: a card per workout with its exercises and the "?" that opens the coach's reason. */
@Composable
internal fun BlockWeekTab(
    sessions: List<BlockSession>,
    exerciseNames: Map<String, String>,
    onOpenExercise: (String) -> Unit,
    onOpenReason: (String) -> Unit,
) {
    sessions.forEach { session ->
        BlockRoutineCard(session, exerciseNames, onOpenExercise, onOpenReason)
    }
}

@Composable
private fun BlockRoutineCard(
    session: BlockSession,
    exerciseNames: Map<String, String>,
    onOpenExercise: (String) -> Unit,
    onOpenReason: (String) -> Unit,
) {
    AppCard(Modifier.fillMaxWidth().testTag("block_preview_routine_${session.week}_${session.sessionIndex}")) {
        Text(
            text = session.name,
            style = AppTheme.typography.titleMedium,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        if (session.focus.isNotEmpty()) {
            Text(
                text = session.focus.map { blockMuscleName(it) }.joinToString(", "),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
            )
        }
        session.exercises.forEachIndexed { position, exercise ->
            BlockExerciseRow(
                session = session,
                position = position,
                exercise = exercise,
                name = exerciseNames[exercise.exerciseId],
                onOpenExercise = onOpenExercise,
                onOpenReason = onOpenReason,
            )
        }
    }
}

@Suppress("LongParameterList") // The row needs its place, its data and both openers.
@Composable
private fun BlockExerciseRow(
    session: BlockSession,
    position: Int,
    exercise: BlockExercise,
    name: String?,
    onOpenExercise: (String) -> Unit,
    onOpenReason: (String) -> Unit,
) {
    val tag = "${session.week}_${session.sessionIndex}_$position"
    val reps = if (exercise.repMin == exercise.repMax) {
        stringResource(Res.string.block_reps_single, exercise.sets, exercise.repMin)
    } else {
        stringResource(Res.string.block_reps_range, exercise.sets, exercise.repMin, exercise.repMax)
    }
    val supporting = stringResource(Res.string.block_exercise_detail, reps, restDurationText(exercise.restSeconds))
    val reasonButton: @Composable () -> Unit = {
        HelpTriggerButton(
            contentDescription = stringResource(Res.string.block_reason_cd),
            testTag = "block_preview_reason_$tag",
            onClick = { onOpenReason(blockExerciseKey(session.week, session.sessionIndex, position)) },
        )
    }
    if (name != null) {
        ExerciseRow(
            name = name,
            supporting = supporting,
            onOpen = { onOpenExercise(exercise.exerciseId) },
            tagPrefix = "block_preview_exercise_$tag",
            trailing = reasonButton,
        )
    } else {
        // Not in the catalog: nothing to open, so the row is not tappable.
        AppListRow(
            headline = stringResource(Res.string.block_exercise_fallback),
            modifier = Modifier.testTag("block_preview_exercise_$tag"),
            supporting = supporting,
            trailing = reasonButton,
        )
    }
}
