package io.github.lopezjavier.appfitness.domain.model

import kotlinx.datetime.LocalDate

/** One completed working set of a [ComparisonSession], as the `session_comparison` RPC returns it. */
data class ComparisonSet(
    val weightKg: Double,
    val reps: Int,
    val rpe: Double? = null,
    val repMin: Int? = null,
    val repMax: Int? = null,
)

/** The working sets of one exercise in one completed session; [date] is the UTC day, [sets] in set order. */
data class ComparisonSession(
    val sessionId: String,
    val date: LocalDate,
    val deload: Boolean,
    val sets: List<ComparisonSet>,
)

/** Inputs of "Frente a la última vez" for one exercise: the finished session and the previous one (null if none). */
data class ExerciseComparisonInput(
    val exerciseId: String,
    val openDiscomfort: Boolean,
    val current: ComparisonSession,
    val previous: ComparisonSession?,
)

/**
 * One session of one exercise reduced to what the comparison needs (07 section 3 (F)): the top working load, the
 * reps at it in set order, the highest RPE at it, the rep range of the first set and, at the top load, whether every
 * set has a [repMin] ([ranged]) and the lowest `reps - repMin` ([margin]).
 */
data class ComparableSession(
    val date: LocalDate,
    val deload: Boolean,
    val topLoadKg: Double,
    val repsAtTop: List<Int>,
    val rpeAtTop: Double?,
    val repMin: Int?,
    val repMax: Int?,
    val ranged: Boolean,
    val margin: Int?,
) {
    val lowestReps: Int get() = repsAtTop.min()

    companion object {
        /** Null when the session has no working set. */
        fun of(session: ComparisonSession): ComparableSession? {
            val first = session.sets.firstOrNull() ?: return null
            val top = session.sets.maxOf { it.weightKg }
            val atTop = session.sets.filter { it.weightKg == top }
            return ComparableSession(
                date = session.date,
                deload = session.deload,
                topLoadKg = top,
                repsAtTop = atTop.map { it.reps },
                rpeAtTop = atTop.mapNotNull { it.rpe }.maxOrNull(),
                repMin = first.repMin,
                repMax = first.repMax,
                ranged = atTop.all { it.repMin != null },
                margin = atTop.mapNotNull { set -> set.repMin?.let { set.reps - it } }.minOrNull(),
            )
        }
    }
}

/**
 * Result of comparing an exercise with its previous session. [before] is null when there was no previous session;
 * [hold] is "Igual" in a cut ("Mantienes"). Never [ProgressState.Stalled].
 */
data class SessionComparison(
    val exerciseId: String,
    val state: ProgressState,
    val before: ComparableSession?,
    val now: ComparableSession,
    val hold: Boolean,
)
