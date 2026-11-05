package io.github.lopezjavier.appfitness.domain.model

import kotlinx.datetime.Instant

/** Kind of an automatic coach change (T-227). [Unknown] covers kinds added by a newer server. */
enum class CoachChangeKind { LoadRaise, LoadDrop, LoadKept, LoadFlag, NoRangeFlag, Unknown }

/** Lifecycle of a [CoachChange]: applied or undone changes, pending or decided flags. */
enum class CoachChangeStatus { Applied, Undone, Pending, Decided, Unknown }

/** What the user chose for a flagged raise (US-104). */
enum class ProgressionFlagDecision(val code: String) {
    Raise("raise"),
    Keep("keep"),
}

/** Recorded decision of a change; [RangeAdded] closes a no-range flag once the exercise got a range. */
enum class CoachChangeDecision { Raise, Keep, RangeAdded, Unknown }

/** Numbers behind a change, all optional because each kind carries a different subset. */
data class CoachChangeFigures(
    val weightFromKg: Double? = null,
    val weightToKg: Double? = null,
    val currentKg: Double? = null,
    val nextKg: Double? = null,
    val stepKg: Double? = null,
    val percent: Double? = null,
    val repsBest: Int? = null,
    val rpeMax: Double? = null,
    val sessions: Int? = null,
    val repMin: Int? = null,
    val repMax: Int? = null,
)

/** One planned set inside a [PlannedRoutineValues] snapshot. */
data class PlannedSetValues(val setIndex: Int, val setType: String?, val targetWeightKg: Double?, val targetReps: Int?)

/** Planned values of one exercise in one routine, before or after a change. */
data class PlannedRoutineValues(
    val routineId: String,
    val exerciseId: String,
    val repMin: Int?,
    val repMax: Int?,
    val sets: List<PlannedSetValues>,
)

/** A load change the coach applied, or a flag waiting for the user. */
data class CoachChange(
    val id: String,
    val planId: String?,
    val kind: CoachChangeKind,
    val exerciseId: String,
    val status: CoachChangeStatus,
    val reason: String,
    val figures: CoachChangeFigures,
    val before: List<PlannedRoutineValues>?,
    val after: List<PlannedRoutineValues>?,
    val createdAt: Instant?,
    val decidedAt: Instant?,
    val decision: CoachChangeDecision?,
)

/** Routines restored by an undo and routines skipped because the user edited them since. */
data class UndoResult(val restored: Int, val skipped: Int)

/** Result of deciding a flag: the closed flag and the new raise (null when the user kept the weight). */
data class FlagDecisionResult(val flag: CoachChange, val change: CoachChange?)

/** Why a coach-change call was refused. */
sealed interface CoachChangeFailure {
    /** `coach_change_conflict`: the planned values changed since; nothing was written. */
    data object Conflict : CoachChangeFailure

    /** `coach_change_not_undoable`: the change is not an applied weight change. */
    data object NotUndoable : CoachChangeFailure

    /** `coach_change_not_pending`: the flag was already decided. */
    data object NotPending : CoachChangeFailure
}

/** Failure carrier of [CoachChangeFailure] in a [Result]. */
class CoachChangeException(val failure: CoachChangeFailure, cause: Throwable? = null) :
    Exception(failure.toString(), cause)
