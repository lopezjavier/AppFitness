package io.github.lopezjavier.appfitness.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class StopwatchTest {
    private fun at(seconds: Long) = Instant.fromEpochSeconds(1_800_000_000L + seconds)

    @Test
    fun newStopwatch_isStoppedAtZero() {
        val stopwatch = Stopwatch()

        assertFalse(stopwatch.isRunning)
        assertEquals(0, stopwatch.elapsedSeconds(at(100)))
    }

    @Test
    fun running_countsTheTimeSinceStart() {
        val stopwatch = Stopwatch().start(at(0))

        assertTrue(stopwatch.isRunning)
        assertEquals(90, stopwatch.elapsedSeconds(at(90)))
    }

    @Test
    fun stopped_keepsTheElapsedTime() {
        val stopwatch = Stopwatch().start(at(0)).stop(at(90))

        assertFalse(stopwatch.isRunning)
        assertEquals(90, stopwatch.elapsedSeconds(at(500)))
    }

    @Test
    fun resumed_addsToThePreviousElapsedTime() {
        val stopwatch = Stopwatch().start(at(0)).stop(at(30)).start(at(100))

        assertEquals(60, stopwatch.elapsedSeconds(at(130)))
    }

    @Test
    fun secondStart_isIgnored() {
        val stopwatch = Stopwatch().start(at(0)).start(at(50))

        assertEquals(60, stopwatch.elapsedSeconds(at(60)))
    }

    @Test
    fun stopWhileStopped_isIgnored() {
        val stopped = Stopwatch().start(at(0)).stop(at(10))

        assertEquals(stopped, stopped.stop(at(99)))
    }

    @Test
    fun elapsed_isTruncatedToWholeSeconds() {
        val stopwatch = Stopwatch(elapsedBeforeMs = 1_999)

        assertEquals(1, stopwatch.elapsedSeconds(at(0)))
    }

    @Test
    fun elapsed_neverGoesNegativeIfTheClockMovesBack() {
        val stopwatch = Stopwatch().start(at(100))

        assertEquals(0, stopwatch.elapsedSeconds(at(50)))
    }

    @Test
    fun snapshot_roundTripsAndResumesAgainstTheClock() {
        val running = Stopwatch().start(at(0)).stop(at(20)).start(at(40))

        val restored = Stopwatch(running.elapsedBeforeMs, running.startedAtMs)

        assertEquals(running, restored)
        assertEquals(20 + 300, restored.elapsedSeconds(at(340)))
    }

    @Test
    fun withElapsed_replacesTheTimeAndStops() {
        val stopwatch = Stopwatch().start(at(0)).withElapsedSeconds(90)

        assertFalse(stopwatch.isRunning)
        assertEquals(90, stopwatch.elapsedSeconds(at(1000)))
    }
}
