package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.DraftWorkoutSet
import io.github.lopezjavier.appfitness.domain.model.SetColumn
import io.github.lopezjavier.appfitness.domain.model.WorkoutDraft
import io.github.lopezjavier.appfitness.domain.model.WorkoutDraftBounds
import io.github.lopezjavier.appfitness.domain.model.columns
import io.github.lopezjavier.appfitness.domain.model.metricType
import io.github.lopezjavier.appfitness.domain.port.Clock

/** One reason a [WorkoutDraft] cannot be saved; mirrors the checks of the `save_completed_workout` RPC. */
sealed interface WorkoutDraftIssue {
    data object Name : WorkoutDraftIssue

    data object Description : WorkoutDraftIssue

    data object Gym : WorkoutDraftIssue

    data object Duration : WorkoutDraftIssue

    /** The date is in the future. */
    data object Date : WorkoutDraftIssue

    /** The workout has no exercise (or more than the maximum). */
    data object Exercises : WorkoutDraftIssue

    /** The exercise has no set (or more than the maximum). */
    data class ExerciseSets(val exercise: Int) : WorkoutDraftIssue

    /** A measurement of the set is out of range. */
    data class SetValue(val exercise: Int, val set: Int) : WorkoutDraftIssue

    /** The RPE of the set is not a whole number from 6 to 10. */
    data class SetRpe(val exercise: Int, val set: Int) : WorkoutDraftIssue

    /** The members of a superset are not consecutive or do not have the same number of sets. */
    data object Superset : WorkoutDraftIssue
}

/**
 * Checks a [WorkoutDraft] before it is sent (US-37). The date must not be in the future when [checkDate] is true
 * (creation); an edit does not change the date, so it skips that check.
 */
class ValidateWorkoutDraft(private val clock: Clock) {
    operator fun invoke(draft: WorkoutDraft, checkDate: Boolean = true): List<WorkoutDraftIssue> = buildList {
        addAll(headerIssues(draft, checkDate))
        if (draft.exercises.size !in 1..WorkoutDraftBounds.EXERCISES_MAX) add(WorkoutDraftIssue.Exercises)
        draft.exercises.forEachIndexed { exercise, item ->
            if (item.sets.size !in 1..WorkoutDraftBounds.SETS_MAX) add(WorkoutDraftIssue.ExerciseSets(exercise))
            item.sets.forEachIndexed { set, value ->
                if (!value.hasValidValues()) add(WorkoutDraftIssue.SetValue(exercise, set))
                if (value.rpe != null && !RpeScale.isValid(value.rpe)) add(WorkoutDraftIssue.SetRpe(exercise, set))
            }
        }
        if (supersetBroken(draft)) add(WorkoutDraftIssue.Superset)
    }

    private fun headerIssues(draft: WorkoutDraft, checkDate: Boolean): List<WorkoutDraftIssue> = buildList {
        if (draft.name.trim().length !in 1..WorkoutDraftBounds.NAME_MAX_LENGTH) add(WorkoutDraftIssue.Name)
        if ((draft.description?.trim()?.length ?: 0) > WorkoutDraftBounds.DESCRIPTION_MAX_LENGTH) {
            add(WorkoutDraftIssue.Description)
        }
        if ((draft.gym?.trim()?.length ?: 0) > WorkoutDraftBounds.GYM_MAX_LENGTH) add(WorkoutDraftIssue.Gym)
        if (draft.durationMinutes !in 0..WorkoutDraftBounds.DURATION_MAX_MINUTES) add(WorkoutDraftIssue.Duration)
        if (checkDate && draft.date > clock.today()) add(WorkoutDraftIssue.Date)
    }

    private fun supersetBroken(draft: WorkoutDraft): Boolean = draft.exercises.withIndex()
        .filter { it.value.supersetGroup != null }
        .groupBy { it.value.supersetGroup }
        .values
        .any { members ->
            members.last().index - members.first().index + 1 != members.size ||
                members.map { it.value.sets.size }.distinct().size > 1
        }

    @Suppress("CyclomaticComplexMethod") // One range check per measurement column.
    private fun DraftWorkoutSet.hasValidValues(): Boolean {
        val values = entry.columns()
        return entry.metricType.columns.all { column ->
            when (column) {
                SetColumn.WeightKg -> (values.weightKg ?: 0.0) in 0.0..WorkoutDraftBounds.WEIGHT_MAX_KG
                SetColumn.Reps -> (values.reps ?: 0) in WorkoutDraftBounds.REPS_MIN..WorkoutDraftBounds.REPS_MAX
                SetColumn.DurationS -> (values.durationS ?: 0) in 1..WorkoutDraftBounds.DURATION_MAX_SECONDS
                SetColumn.DistanceM -> (values.distanceM ?: 0.0) in 0.0..WorkoutDraftBounds.DISTANCE_MAX_M
                SetColumn.Steps -> (values.steps ?: 0) in 0..WorkoutDraftBounds.COUNT_MAX
                SetColumn.Floors -> (values.floors ?: 0) in 0..WorkoutDraftBounds.COUNT_MAX
            }
        }
    }
}
