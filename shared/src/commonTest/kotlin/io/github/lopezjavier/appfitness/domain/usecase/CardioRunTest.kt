package io.github.lopezjavier.appfitness.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class CardioRunTest {
    private val t0 = Instant.parse("2026-11-06T10:00:00Z")
    private val target = 40.minutes

    @Test
    fun elapsed_excludesPausedTime() {
        val stopwatch = Stopwatch().start(t0).stop(t0 + 10.minutes).start(t0 + 30.minutes)

        val run = CardioRun(stopwatch, target)

        assertEquals(15.minutes, run.elapsed(t0 + 35.minutes))
    }

    @Test
    fun targetReached_at2400Seconds() {
        val run = CardioRun(Stopwatch().start(t0), target)

        assertFalse(run.targetReached(t0 + 2399.seconds))
        assertTrue(run.targetReached(t0 + 2400.seconds))
        assertEquals(Duration.ZERO, run.overTarget(t0 + 2400.seconds))
    }

    @Test
    fun overTarget_at41m30s_is1m30s() {
        val run = CardioRun(Stopwatch().start(t0), target)
        val now = t0 + 41.minutes + 30.seconds

        assertTrue(run.targetReached(now))
        assertEquals(90.seconds, run.overTarget(now))
    }

    @Test
    fun withoutTarget_isNeverReached_andHasNoAlert() {
        val run = CardioRun(Stopwatch().start(t0), null)
        val now = t0 + 100.minutes

        assertFalse(run.targetReached(now))
        assertEquals(Duration.ZERO, run.overTarget(now))
        assertNull(run.alertAt(now))
    }

    @Test
    fun alertAt_isNowPlusWhatIsLeftOfTheTarget() {
        val run = CardioRun(Stopwatch().start(t0), target)

        assertEquals(t0 + 10.minutes + 30.minutes, run.alertAt(t0 + 10.minutes))
    }

    @Test
    fun alertAt_whenAlreadyReached_isNow() {
        val run = CardioRun(Stopwatch().start(t0), target)
        val now = t0 + 45.minutes

        assertEquals(now, run.alertAt(now))
    }
}
