package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.block_drop_set
import io.github.lopezjavier.appfitness.composeapp.resources.block_exercise_detail
import io.github.lopezjavier.appfitness.composeapp.resources.block_planned_weight
import io.github.lopezjavier.appfitness.composeapp.resources.block_preview_cardio_detail
import io.github.lopezjavier.appfitness.composeapp.resources.block_preview_cardio_total
import io.github.lopezjavier.appfitness.composeapp.resources.block_reason_cd
import io.github.lopezjavier.appfitness.composeapp.resources.block_replace_action_cd
import io.github.lopezjavier.appfitness.composeapp.resources.block_replace_changed
import io.github.lopezjavier.appfitness.composeapp.resources.block_reps_range
import io.github.lopezjavier.appfitness.composeapp.resources.block_reps_single
import io.github.lopezjavier.appfitness.composeapp.resources.block_rpe_detail
import io.github.lopezjavier.appfitness.composeapp.resources.ic_swap
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ExerciseRow
import io.github.lopezjavier.appfitness.designsystem.HelpTriggerButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.SupersetAccent
import io.github.lopezjavier.appfitness.designsystem.SupersetAccentWidth
import io.github.lopezjavier.appfitness.designsystem.supersetText
import io.github.lopezjavier.appfitness.domain.model.BlockExercise
import io.github.lopezjavier.appfitness.domain.model.BlockSession
import io.github.lopezjavier.appfitness.domain.model.BlockSetType
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.usecase.DurationFormat
import io.github.lopezjavier.appfitness.domain.usecase.SupersetLabel
import io.github.lopezjavier.appfitness.domain.usecase.supersetLabels
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
@Suppress("LongParameterList") // Data plus the opener callbacks of the rows.
internal fun BlockWeekTab(
    sessions: List<BlockSession>,
    exerciseNames: Map<String, String>,
    onOpenExercise: (String) -> Unit,
    onOpenReason: (String) -> Unit,
    isChanged: (sessionIndex: Int, position: Int) -> Boolean,
    canSwap: (sessionIndex: Int, position: Int) -> Boolean,
    onReplace: (sessionIndex: Int, position: Int) -> Unit,
    routineTotalMin: List<Int> = emptyList(),
) {
    sessions.forEach { session ->
        BlockRoutineCard(
            session,
            exerciseNames,
            onOpenExercise,
            onOpenReason,
            isChanged,
            canSwap,
            onReplace,
            totalMin = routineTotalMin.getOrNull(session.sessionIndex),
        )
    }
}

@Composable
@Suppress("LongParameterList") // Data plus the opener callbacks of the rows.
private fun BlockRoutineCard(
    session: BlockSession,
    exerciseNames: Map<String, String>,
    onOpenExercise: (String) -> Unit,
    onOpenReason: (String) -> Unit,
    isChanged: (sessionIndex: Int, position: Int) -> Boolean,
    canSwap: (sessionIndex: Int, position: Int) -> Boolean,
    onReplace: (sessionIndex: Int, position: Int) -> Unit,
    totalMin: Int?,
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
        val supersets = supersetLabels(session.exercises.map { it.supersetGroup })
        session.exercises.forEachIndexed { position, exercise ->
            BlockExerciseRow(
                session = session,
                superset = supersets[position],
                position = position,
                exercise = exercise,
                name = exerciseNames.getValue(exercise.exerciseId),
                onOpenExercise = onOpenExercise,
                onOpenReason = onOpenReason,
                changed = isChanged(session.sessionIndex, position),
                swappable = canSwap(session.sessionIndex, position),
                onReplace = { onReplace(session.sessionIndex, position) },
            )
        }
        if (totalMin != null) {
            Text(
                text = stringResource(
                    Res.string.block_preview_cardio_total,
                    DurationFormat.formatHoursMinutes(totalMin),
                ),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.testTag("block_preview_cardio_total_${session.week}_${session.sessionIndex}"),
            )
        }
    }
}

@Suppress("LongParameterList") // The row needs its place, its data and both openers.
@Composable
private fun BlockExerciseRow(
    session: BlockSession,
    position: Int,
    superset: SupersetLabel?,
    exercise: BlockExercise,
    name: String,
    onOpenExercise: (String) -> Unit,
    onOpenReason: (String) -> Unit,
    changed: Boolean,
    swappable: Boolean,
    onReplace: () -> Unit,
) {
    val tag = "${session.week}_${session.sessionIndex}_$position"
    val detail = if (exercise.isCardio) {
        // Cardio is minutes at an easy pace: no sets, reps or rest.
        stringResource(Res.string.block_preview_cardio_detail, exercise.durationMin ?: 0)
    } else {
        blockExerciseDetail(exercise)
    }
    val changedText = stringResource(Res.string.block_replace_changed)
    // The marker is text, not colour alone (R-41); it also reads as the state of the row.
    val detailLine = if (changed) stringResource(Res.string.block_exercise_detail, changedText, detail) else detail
    val supporting = exercise.techniqueNote?.takeIf { it.isNotBlank() }?.let { "$detailLine\n$it" } ?: detailLine
    val rowSemantics = if (changed) Modifier.semantics { stateDescription = changedText } else Modifier
    val supportingTag = when {
        changed -> "block_preview_changed_$tag"
        exercise.isCardio -> "block_preview_cardio_row_${session.week}_${session.sessionIndex}"
        else -> null
    }
    val reasonButton: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (swappable) {
                AppIconButton(
                    contentDescription = stringResource(Res.string.block_replace_action_cd, name),
                    icon = Res.drawable.ic_swap,
                    testTag = "block_preview_replace_$tag",
                    onClick = onReplace,
                    tint = AppTheme.colors.textSecondary,
                )
            }
            HelpTriggerButton(
                contentDescription = stringResource(Res.string.block_reason_cd),
                testTag = "block_preview_reason_$tag",
                onClick = { onOpenReason(blockExerciseKey(session.week, session.sessionIndex, position)) },
            )
        }
    }
    SupersetFrame(superset, exercise.supersetGroup, session, tag) {
        ExerciseRow(
            name = name,
            supporting = supporting,
            onOpen = { onOpenExercise(exercise.exerciseId) },
            tagPrefix = "block_preview_exercise_$tag",
            modifier = rowSemantics,
            trailing = reasonButton,
            supportingTestTag = supportingTag,
        )
    }
}

/** The row, with the "Superserie A1" label and the cyan accent on its left edge when it is a superset member. */
@Composable
private fun SupersetFrame(
    superset: SupersetLabel?,
    group: Int?,
    session: BlockSession,
    tag: String,
    row: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = if (superset != null) SupersetAccentWidth + Spacing.sm else Spacing.none)) {
            if (superset != null) {
                val labelTag = "block_preview_superset_${session.week}_${session.sessionIndex}_$group"
                Text(
                    text = supersetText(superset),
                    style = AppTheme.typography.labelTechnical,
                    color = AppTheme.colors.athleticCyan,
                    modifier = Modifier.testTag(
                        if (superset.number ==
                            1
                        ) {
                            labelTag
                        } else {
                            "${labelTag}_${superset.number}"
                        },
                    ),
                )
            }
            row()
        }
        if (superset != null) {
            SupersetAccent(
                Modifier.align(Alignment.CenterStart).fillMaxHeight().testTag("block_preview_superset_accent_$tag"),
            )
        }
    }
}

/**
 * "3×8-12 reps · RPE 8 · 1 min 30 s · 62,5 kg · Última serie descendente" of a lifting exercise: every part after the
 * reps is left out when the block does not carry it (older blocks have no RPE, weight or drop set).
 */
@Composable
private fun blockExerciseDetail(exercise: BlockExercise): String {
    val reps = if (exercise.repMin == exercise.repMax) {
        stringResource(Res.string.block_reps_single, exercise.sets, exercise.repMin)
    } else {
        stringResource(Res.string.block_reps_range, exercise.sets, exercise.repMin, exercise.repMax)
    }
    val rpe = exercise.targetRpe?.let { stringResource(Res.string.block_rpe_detail, AppNumberFormat.decimal(it)) }
    val rest = restDurationText(exercise.restSeconds)
    val weight = exercise.plannedWeightKg?.let {
        stringResource(Res.string.block_planned_weight, AppNumberFormat.decimal(it))
    }
    val drop = if (exercise.setPlan.any { it.type == BlockSetType.Drop }) {
        stringResource(Res.string.block_drop_set)
    } else {
        null
    }
    return listOfNotNull(reps, rpe, rest, weight, drop).reduce { acc, part ->
        stringResource(Res.string.block_exercise_detail, acc, part)
    }
}
