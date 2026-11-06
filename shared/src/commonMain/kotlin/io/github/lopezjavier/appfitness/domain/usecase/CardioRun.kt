package io.github.lopezjavier.appfitness.domain.usecase

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * A cardio run: a count-up over the set's [stopwatch] against an optional [target]. Pure functions of instants, so
 * the numbers stay exact while the app is away and the view model and the tests share one formula.
 */
data class CardioRun(val stopwatch: Stopwatch, val target: Duration?) {
    /** Time run at [now]; paused spans are not counted (see [Stopwatch]). */
    fun elapsed(now: Instant): Duration = stopwatch.elapsedSeconds(now).seconds

    /** The target exists and [elapsed] has reached it. */
    fun targetReached(now: Instant): Boolean = target != null && elapsed(now) >= target

    /** How far past the target the run is at [now]; zero before it or without a target. */
    fun overTarget(now: Instant): Duration = target?.let { (elapsed(now) - it).coerceAtLeast(Duration.ZERO) }
        ?: Duration.ZERO

    /** The instant the target is reached if the run keeps going from [now]; null without a target. */
    fun alertAt(now: Instant): Instant? = target?.let { now + (it - elapsed(now)).coerceAtLeast(Duration.ZERO) }
}
