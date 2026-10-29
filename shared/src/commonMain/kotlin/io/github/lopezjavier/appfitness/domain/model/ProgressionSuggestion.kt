package io.github.lopezjavier.appfitness.domain.model

import kotlinx.datetime.LocalDate

/** What the double-progression rule asks for (`04` §4.14). */
enum class ProgressionAction { RaiseWeight, AddRep, LowerWeight }

/** Why: drives the body text of the help sheet. No health wording (ADR-81). */
enum class ProgressionReason {
    /** Every working set reached the top of the range at RPE 8 or less. */
    AllSetsAtMax,

    /** Not every set reached the top: same weight, one more rep. */
    BelowMax,

    /** Most sets were below the minimum in the last two sessions. */
    TwoSessionsBelowMin,

    /** An open discomfort of level 1-3 blocks the increase: keep the last weight. */
    KeptDiscomfort,
}

/** One working set the suggestion was computed from. [rpe] is null when it was not recorded. */
data class SetUsed(val date: LocalDate, val weightKg: Double, val reps: Int, val rpe: Double?)

/**
 * The suggestion of one exercise. [weightKg] is the BASE weight as returned by the server (top working weight of the
 * latest matching session); [ProgressionWeight.apply] turns it into the weight to show. [goalReps] is the rep target.
 */
data class ProgressionSuggestion(
    val exerciseId: String,
    val action: ProgressionAction,
    val weightKg: Double,
    val goalReps: Int,
    val reason: ProgressionReason,
    val setsUsed: List<SetUsed>,
)
