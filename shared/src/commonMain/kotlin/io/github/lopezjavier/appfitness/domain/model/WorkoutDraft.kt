package io.github.lopezjavier.appfitness.domain.model

import kotlinx.datetime.LocalDate

/** One set of a [WorkoutDraft]; [rpe] is null or a whole number 6-10 (ADR-56). */
data class DraftWorkoutSet(val setType: SetType, val rpe: Double?, val entry: SetEntry, val notes: String? = null)

/** One exercise of a [WorkoutDraft]; its position is its index in the list. */
data class DraftExercise(
    val exerciseId: String,
    val supersetGroup: Int?,
    val restSeconds: Int,
    val sets: List<DraftWorkoutSet>,
)

/**
 * A finished workout being created (past workout) or edited (US-37). It is always saved as `completed`, never
 * through the live session. [date] is the local day the workout happened on; [durationMinutes] is 0 when unknown.
 */
data class WorkoutDraft(
    val name: String,
    val description: String?,
    val gym: String?,
    val date: LocalDate,
    val routineId: String?,
    val durationMinutes: Int,
    val exercises: List<DraftExercise>,
)

/** Bounds of a [WorkoutDraft], mirroring the checks of the `save_completed_workout` RPC and the table columns. */
object WorkoutDraftBounds {
    const val NAME_MAX_LENGTH = 100
    const val GYM_MAX_LENGTH = 100
    const val DESCRIPTION_MAX_LENGTH = 1000
    const val EXERCISES_MAX = 30
    const val SETS_MAX = 30
    const val REPS_MIN = 1
    const val REPS_MAX = 999
    const val WEIGHT_MAX_KG = 9999.99
    const val DURATION_MAX_SECONDS = 86_400
    const val DISTANCE_MAX_M = 99_999.9
    const val COUNT_MAX = 1_000_000
    const val DURATION_MAX_MINUTES = 1_440
}
