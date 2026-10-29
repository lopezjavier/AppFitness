package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.ExerciseNoteKind
import io.github.lopezjavier.appfitness.domain.model.Note
import io.github.lopezjavier.appfitness.domain.model.PlannedSet
import io.github.lopezjavier.appfitness.domain.model.Routine
import io.github.lopezjavier.appfitness.domain.model.RoutineBounds
import io.github.lopezjavier.appfitness.domain.model.RoutineExercise
import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import io.github.lopezjavier.appfitness.domain.model.WorkoutSession
import io.github.lopezjavier.appfitness.domain.model.WorkoutSet
import io.github.lopezjavier.appfitness.domain.model.columns

/** One difference between what a session did and what its routine planned (US-36). */
sealed interface RoutineChange {
    data class ExerciseAdded(val exerciseId: String) : RoutineChange

    data class ExerciseRemoved(val exerciseId: String) : RoutineChange

    data class SetAdded(val exerciseId: String) : RoutineChange

    data class SetRemoved(val exerciseId: String) : RoutineChange

    /** Weight, reps or type of one set differs. */
    data class SetChanged(val exerciseId: String) : RoutineChange

    data class RestChanged(val exerciseId: String) : RoutineChange

    /** The routine-exercise note was edited during the session (ADR-82). */
    data class NoteChanged(val exerciseId: String) : RoutineChange

    data class SupersetChanged(val exerciseId: String) : RoutineChange

    /** The common exercises are done in another order than the routine's. */
    data object OrderChanged : RoutineChange
}

data class RoutineDiffResult(val changes: List<RoutineChange>) {
    val isSame: Boolean get() = changes.isEmpty()
}

/**
 * Compares the COMPLETED sets of [session] with the routine's planned sets. [rests] maps the session's exercise
 * position (original, not renumbered) to its rest seconds; a missing entry means "unchanged". Exercises are matched
 * by exercise id in order; the routine's weight and reps are the only values compared (null weight equals 0).
 */
fun routineDiff(
    session: WorkoutSession,
    rests: Map<Int, Int>,
    routine: Routine,
    editedNotes: Map<Int, Note<ExerciseNoteKind>?> = emptyMap(),
): RoutineDiffResult {
    val done = session.exercises
        .sortedBy { it.position }
        .mapNotNull { exercise ->
            val completed = exercise.sets.filter { it.isCompleted }.sortedBy { it.setIndex }
            if (completed.isEmpty()) null else exercise to completed
        }
    val planned = routine.exercises.sortedBy { it.position }.toMutableList()

    val changes = mutableListOf<RoutineChange>()
    val matchedSessionIds = mutableListOf<String>()
    for ((exercise, completed) in done) {
        val match = planned.firstOrNull { it.exerciseId == exercise.exerciseId }
        if (match == null) {
            changes += RoutineChange.ExerciseAdded(exercise.exerciseId)
            continue
        }
        planned.remove(match)
        matchedSessionIds += exercise.exerciseId
        changes += exerciseChanges(exercise, completed, match, rests)
        if (exercise.position in editedNotes && editedNotes[exercise.position] != match.note()) {
            changes += RoutineChange.NoteChanged(exercise.exerciseId)
        }
    }
    planned.forEach { changes += RoutineChange.ExerciseRemoved(it.exerciseId) }
    val routineOrder = routine.exercises.sortedBy { it.position }.map { it.exerciseId }
        .filter { it in matchedSessionIds }
    if (matchedSessionIds != routineOrder) changes += RoutineChange.OrderChanged
    return RoutineDiffResult(changes)
}

private fun exerciseChanges(
    exercise: SessionExercise,
    completed: List<WorkoutSet>,
    planned: RoutineExercise,
    rests: Map<Int, Int>,
): List<RoutineChange> {
    val id = exercise.exerciseId
    val changes = mutableListOf<RoutineChange>()
    val common = minOf(completed.size, planned.plannedSets.size)
    repeat(common) { index ->
        if (completed[index].asPlanned() != planned.plannedSets[index].normalized()) {
            changes += RoutineChange.SetChanged(id)
        }
    }
    repeat(completed.size - common) { changes += RoutineChange.SetAdded(id) }
    repeat(planned.plannedSets.size - common) { changes += RoutineChange.SetRemoved(id) }
    if ((rests[exercise.position] ?: planned.restSeconds) != planned.restSeconds) {
        changes += RoutineChange.RestChanged(id)
    }
    if (exercise.supersetGroup != planned.supersetGroup) changes += RoutineChange.SupersetChanged(id)
    return changes
}

/** The planned note of the exercise, or null when it has none (a kind without a valid pair counts as none). */
private fun RoutineExercise.note(): Note<ExerciseNoteKind>? = Note.of(noteKind, notes)

private fun WorkoutSet.asPlanned(): PlannedSet {
    val columns = entry.columns()
    return PlannedSet(columns.weightKg ?: 0.0, (columns.reps ?: 0).clampedReps(), setType)
}

private fun PlannedSet.normalized() = PlannedSet(weightKg ?: 0.0, reps.clampedReps(), type)

private fun Int.clampedReps() = coerceIn(RoutineBounds.REPS_MIN, RoutineBounds.REPS_MAX)
