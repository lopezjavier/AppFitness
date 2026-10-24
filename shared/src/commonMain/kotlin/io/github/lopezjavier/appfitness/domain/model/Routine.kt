package io.github.lopezjavier.appfitness.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** DB values of `public.set_type`. Labels in the UI: Normal, Calentamiento, Fallo, Drop. */
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

/** One planned set of a routine exercise (ADR-50). A null [weightKg] means "no planned weight". */
data class PlannedSet(val weightKg: Double?, val reps: Int, val type: SetType = SetType.Working)

/**
 * One planned exercise of a routine. [position] is 0-based and contiguous in the routine's order; [supersetGroup]
 * is null for a standalone exercise, otherwise exercises of the same group are consecutive. [plannedSets] is the
 * ordered list of sets to perform; [targetRpe] is optional (6-10).
 */
data class RoutineExercise(
    val exerciseId: String,
    val position: Int,
    val supersetGroup: Int?,
    val plannedSets: List<PlannedSet>,
    val restSeconds: Int,
    val targetRpe: Double? = null,
) {
    val targetSets: Int get() = plannedSets.size
}

/** A routine template. [id] is null until it has been saved. */
data class Routine(
    val id: String?,
    val name: String,
    val notes: String?,
    val archived: Boolean,
    val exercises: List<RoutineExercise>,
    /** Compare finished sessions of this routine with it (US-36). */
    val trackAsRoutine: Boolean = true,
    /** Update the routine from a finished session without asking. */
    val autoUpdateRoutine: Boolean = false,
)

/** A folder of routines ("Mis rutinas" itself is the no-folder group). */
data class RoutineFolder(val id: String, val name: String)

/** One row of "Mis rutinas". The list arrives sorted by the user's manual order (position). */
data class RoutineSummary(
    val id: String,
    val name: String,
    val exerciseCount: Int,
    val folderId: String? = null,
    val isNext: Boolean = false,
)

/** A group of routines: a folder, or the no-folder group ("Mis rutinas") when [folder] is null. */
data class RoutineGroup(val folder: RoutineFolder?, val routines: List<RoutineSummary>)

/** Bounds shared with the DB checks of `routine_exercises` and `routines`. */
object RoutineBounds {
    const val NAME_MAX_LENGTH = 100
    const val SETS_MIN = 1
    const val SETS_MAX = 10
    const val REPS_MIN = 1
    const val REPS_MAX = 50
    const val WEIGHT_MIN_KG = 0.0
    const val RPE_MIN = 1.0
    const val RPE_MAX = 10.0
    const val REST_MIN_SECONDS = 0
    const val REST_MAX_SECONDS = 600
    const val REST_STEP_SECONDS = 10
    const val DEFAULT_SETS = 3
    const val DEFAULT_REPS = 8
    const val DEFAULT_REST_SECONDS = 120

    /** Planned sets of a newly added exercise: [DEFAULT_SETS] Normal sets of [DEFAULT_REPS] reps, no weight. */
    fun defaultPlannedSets(): List<PlannedSet> = List(DEFAULT_SETS) { PlannedSet(null, DEFAULT_REPS, SetType.Working) }
}
