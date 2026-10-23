package io.github.lopezjavier.appfitness.domain.usecase

import kotlin.time.Instant

/**
 * A stopwatch as a pure value: [elapsedBeforeMs] is the time banked by earlier runs and [startedAtMs] the epoch
 * millisecond of the current run (null when stopped). It never ticks: callers read [elapsedSeconds] against the
 * current instant, so it stays exact while the app is away. The constructor arguments are the serialisable snapshot.
 */
data class Stopwatch(val elapsedBeforeMs: Long = 0, val startedAtMs: Long? = null) {
    val isRunning: Boolean get() = startedAtMs != null

    /** Starts a run at [now]; ignored while already running. */
    fun start(now: Instant): Stopwatch = if (isRunning) this else copy(startedAtMs = now.toEpochMilliseconds())

    /** Banks the current run at [now]; ignored while stopped. */
    fun stop(now: Instant): Stopwatch = if (isRunning) Stopwatch(elapsedMs(now)) else this

    /** Whole seconds elapsed at [now]. */
    fun elapsedSeconds(now: Instant): Int = (elapsedMs(now) / MS_PER_SECOND).toInt()

    /** A stopped stopwatch showing [seconds] (manual entry). */
    fun withElapsedSeconds(seconds: Int): Stopwatch = Stopwatch(seconds * MS_PER_SECOND)

    private fun elapsedMs(now: Instant): Long {
        val run = startedAtMs?.let { now.toEpochMilliseconds() - it } ?: 0
        return elapsedBeforeMs + run.coerceAtLeast(0)
    }

    private companion object {
        const val MS_PER_SECOND = 1_000L
    }
}
