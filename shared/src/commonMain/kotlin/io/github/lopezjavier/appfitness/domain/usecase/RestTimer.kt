package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.RoutineBounds
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The rest countdown as a pure function of instants: it never counts down, so it stays exact when the app is in the
 * background. [total] is the length the progress bar is drawn against and grows when time is added past it.
 */
data class RestTimer(val endsAt: Instant, val total: Duration) {
    fun remaining(now: Instant): Duration = (endsAt - now).coerceAtLeast(Duration.ZERO)

    fun isFinished(now: Instant): Boolean = now >= endsAt

    /** Adds [by] to what is left; [total] grows when the new remaining time exceeds it. */
    fun extend(now: Instant, by: Duration = EXTENSION): RestTimer {
        val left = remaining(now) + by
        return RestTimer(now + left, maxOf(total, left))
    }

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
