package io.github.lopezjavier.appfitness.domain.usecase

import kotlin.time.Instant

/** Duration of a session without the paused time (04 §duration). Pure function of the stored instants. */
object SessionDuration {
    /**
     * The end of the span is [endedAt] when finished, else [pausedAt] when paused (the display freezes), else
     * [now]. A pause still open at [endedAt] counts as paused until then.
     * [pausedTotalSeconds] is the already closed pause time. The result is never negative.
     */
    fun seconds(
        startedAt: Instant,
        endedAt: Instant?,
        pausedAt: Instant?,
        pausedTotalSeconds: Long,
        now: Instant,
    ): Long {
        val end = endedAt ?: pausedAt ?: now
        // Finishing while paused closes the open pause at the end instant (the server folds it the same way).
        val openPause = if (endedAt != null && pausedAt != null) (endedAt - pausedAt).inWholeSeconds else 0L
        return ((end - startedAt).inWholeSeconds - pausedTotalSeconds - openPause.coerceAtLeast(0L)).coerceAtLeast(0L)
    }
}
