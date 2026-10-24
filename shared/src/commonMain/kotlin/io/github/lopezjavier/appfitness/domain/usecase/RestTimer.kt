package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.RoutineBounds
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The rest countdown as a pure function of instants: it never counts down, so it stays exact when the app is in the
 * background. [total] is the length the progress bar is drawn against and grows when time is added past it.
 */
data class RestTimer(val endsAt: Instant, val total: Duration, val pausedRemaining: Duration? = null) {
    val isPaused: Boolean get() = pausedRemaining != null

    fun remaining(now: Instant): Duration = pausedRemaining ?: (endsAt - now).coerceAtLeast(Duration.ZERO)

    /** A paused timer never finishes. */
    fun isFinished(now: Instant): Boolean = !isPaused && now >= endsAt

    /** Adds [by] to what is left; [total] grows when the new remaining time exceeds it. Keeps the paused state. */
    fun extend(now: Instant, by: Duration = EXTENSION): RestTimer {
        val left = remaining(now) + by
        return RestTimer(now + left, maxOf(total, left), pausedRemaining?.let { left })
    }

    /** Freezes the countdown at what is left now. Pausing a paused timer changes nothing. */
    fun pause(now: Instant): RestTimer = if (isPaused) this else copy(pausedRemaining = remaining(now))

    /** Runs again from the frozen remaining time. Resuming a running timer changes nothing. */
    fun resume(now: Instant): RestTimer = pausedRemaining?.let { RestTimer(now + it, total) } ?: this

    companion object {
        val EXTENSION: Duration = 30.seconds

        fun start(duration: Duration, now: Instant) = RestTimer(now + duration, duration)
    }
}

/**
 * Rest after completing a set of the exercise at [position]: its own planned rest (Q3: in a superset a non-last member
 * holds the transition rest and the last one the rest between rounds). Exercises without an entry (added during the
 * session) get the default. Zero means no timer (null).
 */
fun resolveRest(position: Int, rests: Map<Int, Int>): Duration? {
    val seconds = rests[position] ?: RoutineBounds.DEFAULT_REST_SECONDS
    return if (seconds > 0) seconds.seconds else null
}
