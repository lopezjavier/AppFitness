package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.FolderFalta
import io.github.lopezjavier.appfitness.domain.model.FolderOp
import io.github.lopezjavier.appfitness.domain.model.FolderReview

/** The kind of a proposed change; the screen picks the Spanish sentence from it. */
enum class ChangeKind { Add, Replace, Remove, SetSets, SetRepRange, SetRpe, SetRest }

/**
 * A proposed change ready to format. [routineName] is null when the folder's routine is not known (the screen then
 * says "Rutina [routineIndex]"); [exerciseName] is the exercise the change touches; [extra] holds the figures or the
 * new exercise name, in the order the sentence uses them.
 */
data class ChangeLine(
    val kind: ChangeKind,
    val routineIndex: Int,
    val routineName: String?,
    val exerciseName: String,
    val extra: List<String>,
)

/** A routine of the reviewed folder in the server's order: its name and its exercise ids in position order. */
class FolderRoutine(val name: String, val exerciseIds: List<String>)

/**
 * What names a review refers to. `routine_index` and `position` are 1-based over the folder's active routines (in
 * manual order) and over each routine's exercises. An `exerciseKey` is a catalogue key, or the exercise id of a
 * custom exercise.
 */
class FolderContext(
    private val routines: List<FolderRoutine>,
    private val idNames: Map<String, String>,
    private val catalogue: Map<String, String>,
) {
    fun routineName(index: Int): String? = routines.getOrNull(index - 1)?.name

    fun exerciseAt(routineIndex: Int, position: Int): String? =
        routines.getOrNull(routineIndex - 1)?.exerciseIds?.getOrNull(position - 1)?.let { idNames[it] }

    fun exerciseOfKey(key: String): String? = catalogue[key] ?: idNames[key]

    fun itemOf(falta: FolderFalta) = FaltaItem(
        falta,
        falta.routineIndex?.let(::routineName),
        falta.exerciseKey?.let(::exerciseOfKey),
    )
}

/** The line of this change, or null when a name it needs is not known (the screen then fails, R-54). */
internal fun FolderOp.toLine(context: FolderContext): ChangeLine? {
    fun line(kind: ChangeKind, routine: Int, exercise: String?, vararg extra: String) =
        exercise?.let { ChangeLine(kind, routine, context.routineName(routine), it, extra.toList()) }
    return when (this) {
        is FolderOp.AddExercise ->
            line(ChangeKind.Add, routineIndex, context.exerciseOfKey(exerciseKey), sets.size.toString())

        is FolderOp.ReplaceExercise -> context.exerciseOfKey(exerciseKey)?.let { new ->
            line(ChangeKind.Replace, routineIndex, context.exerciseAt(routineIndex, position), new)
        }

        is FolderOp.RemoveExercise ->
            line(ChangeKind.Remove, routineIndex, context.exerciseAt(routineIndex, position))

        is FolderOp.SetSets ->
            line(ChangeKind.SetSets, routineIndex, context.exerciseAt(routineIndex, position), n.toString())

        is FolderOp.SetRepRange -> line(
            ChangeKind.SetRepRange,
            routineIndex,
            context.exerciseAt(routineIndex, position),
            repMin.toString(),
            repMax.toString(),
        )

        is FolderOp.SetRpe ->
            line(ChangeKind.SetRpe, routineIndex, context.exerciseAt(routineIndex, position), rpe.asText())

        is FolderOp.SetRest -> line(
            ChangeKind.SetRest,
            routineIndex,
            context.exerciseAt(routineIndex, position),
            restSeconds.toString(),
        )
    }
}

// Locale-free: "8" for a whole RPE, "8.5" otherwise.
private fun Double.asText(): String = if (this == kotlin.math.floor(this)) toInt().toString() else toString()

/** Codes of the review's Falta items that no accepted change fixes. */
internal fun openFaltaCodes(review: FolderReview, accepted: Set<String>): Set<String> {
    val fixed = review.changes.filter { it.id in accepted }.mapNotNull { it.fixes }.toSet()
    return review.falta.map { it.code }.filter { it !in fixed }.toSet()
}

/** Every change checked, except the later members of a conflict group (the user takes one of each). */
internal fun defaultAccepted(review: FolderReview): Set<String> {
    val accepted = linkedSetOf<String>()
    review.changes.forEach { change ->
        val conflicts = accepted.any { other ->
            other in change.conflictsWith || review.changes.first { it.id == other }.conflictsWith.contains(change.id)
        }
        if (!conflicts) accepted += change.id
    }
    return accepted
}

/** [accepted] after tapping [id]: checking a change unchecks the ones it conflicts with. */
internal fun toggled(review: FolderReview, accepted: Set<String>, id: String): Set<String> {
    val change = review.changes.firstOrNull { it.id == id }
    return when {
        change == null -> accepted

        id in accepted -> accepted - id

        else -> {
            val rivals = change.conflictsWith.toSet() + review.changes.filter { id in it.conflictsWith }.map { it.id }
            accepted - rivals + id
        }
    }
}
