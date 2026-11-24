package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.ComparableSession
import io.github.lopezjavier.appfitness.domain.model.ProgressState
import io.github.lopezjavier.appfitness.domain.model.SessionComparison
import kotlinx.datetime.daysUntil

/**
 * "Frente a la última vez" for one exercise: the strength states of `coach_progress_strength_for` (T-313) applied to
 * two sessions (07 section 3 (F)). Never returns [ProgressState.Stalled]; the stall test needs a whole block.
 */
object CompareToPreviousSession {
    /** Two sessions this far apart or more are not compared. */
    const val MAX_GAP_DAYS = 14

    /** Largest RPE rise at which a gain still counts as progress. */
    private const val RPE_TOLERANCE = 1.0

    operator fun invoke(
        exerciseId: String,
        thisSession: ComparableSession,
        previousSession: ComparableSession?,
        inCut: Boolean,
        openDiscomfort: Boolean,
    ): SessionComparison {
        val base = baseState(thisSession, previousSession)
        val state = if (openDiscomfort && base != ProgressState.Up) ProgressState.Paused else base
        return SessionComparison(
            exerciseId = exerciseId,
            state = state,
            before = previousSession,
            now = thisSession,
            hold = state == ProgressState.Same && inCut,
        )
    }

    private fun baseState(now: ComparableSession, before: ComparableSession?): ProgressState = when {
        before == null || now.deload || before.deload -> ProgressState.Uncompared
        now.repMin != before.repMin || now.repMax != before.repMax -> ProgressState.Uncompared
        before.date.daysUntil(now.date) >= MAX_GAP_DAYS -> ProgressState.Uncompared
        now.topLoadKg < before.topLoadKg -> ProgressState.Down
        now.topLoadKg > before.topLoadKg -> if (loadGainHolds(now, before)) ProgressState.Up else ProgressState.Same
        else -> sameLoad(now, before)
    }

    private fun loadGainHolds(now: ComparableSession, before: ComparableSession): Boolean {
        val repsHold = if (now.ranged) (now.margin ?: 0) >= 0 else now.lowestReps >= before.lowestReps
        return repsHold && rpeAllows(now, before)
    }

    private fun sameLoad(now: ComparableSession, before: ComparableSession): ProgressState {
        val shared = minOf(now.repsAtTop.size, before.repsAtTop.size)
        val nowSum = now.repsAtTop.take(shared).sum()
        val beforeSum = before.repsAtTop.take(shared).sum()
        return when {
            nowSum > beforeSum -> if (rpeAllows(now, before)) ProgressState.Up else ProgressState.Same
            nowSum < beforeSum -> ProgressState.Down
            else -> ProgressState.Same
        }
    }

    private fun rpeAllows(now: ComparableSession, before: ComparableSession): Boolean {
        val nowRpe = now.rpeAtTop
        val beforeRpe = before.rpeAtTop
        return nowRpe == null || beforeRpe == null || nowRpe <= beforeRpe + RPE_TOLERANCE
    }
}
