package io.github.lopezjavier.appfitness.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** DB values of `public.set_type`; mapped for completeness, used by the workout epic. */
@Serializable
enum class SetType {
    @SerialName("working")
    Working,

    @SerialName("warm_up")
    WarmUp,

    @SerialName("failure")
    Failure,

    @SerialName("drop")
    Drop,
}

/**
 * One planned exercise of a routine. [position] is 0-based and contiguous in the routine's order; [supersetGroup]
 * is null for a standalone exercise, otherwise exercises of the same group are consecutive.
 */
data class RoutineExercise(
    val exerciseId: String,
    val position: Int,
    val supersetGroup: Int?,
    val targetSets: Int,
    val targetReps: Int,
    val restSeconds: Int,
)

/** A routine template. [id] is null until it has been saved. */
data class Routine(
    val id: String?,
    val name: String,
    val notes: String?,
    val archived: Boolean,
    val exercises: List<RoutineExercise>,
)

/** One row of "Mis rutinas". */
data class RoutineSummary(val id: String, val name: String, val exerciseCount: Int)

/** Bounds shared with the DB checks of `routine_exercises` and `routines`. */
object RoutineBounds {
    const val NAME_MAX_LENGTH = 100
    const val SETS_MIN = 1
    const val SETS_MAX = 10
    const val REPS_MIN = 1
    const val REPS_MAX = 50
    const val REST_MIN_SECONDS = 0
    const val REST_MAX_SECONDS = 600
    const val REST_STEP_SECONDS = 15
    const val DEFAULT_SETS = 3
    const val DEFAULT_REPS = 8
    const val DEFAULT_REST_SECONDS = 120
}
