package io.github.lopezjavier.appfitness.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class RestTimerTest {
    private val t0 = Instant.parse("2026-10-19T10:00:00Z")

    @Test
    fun start_hasTheFullDurationRemaining() {
        val timer = RestTimer.start(120.seconds, t0)

        assertEquals(120.seconds, timer.remaining(t0))
        assertEquals(120.seconds, timer.total)
    }

    @Test
    fun remaining_isAFunctionOfTheInstant_evenAfterALongGap() {
        val timer = RestTimer.start(120.seconds, t0)

        assertEquals(48.seconds, timer.remaining(t0 + 72.seconds))
        assertEquals(0.seconds, timer.remaining(t0 + 500.seconds))
    }

    @Test
    fun extend_at72SecondsElapsed_makesItAMinuteAndAHalfPlusTwelve() {
        val timer = RestTimer.start(120.seconds, t0).extend(t0 + 72.seconds)

        assertEquals(78.seconds, timer.remaining(t0 + 72.seconds))
        assertEquals(120.seconds, timer.total)
    }

    @Test
    fun extend_whenNearlyDone_growsTheTotal() {
        val now = t0 + 10.seconds
        val timer = RestTimer.start(30.seconds, t0).extend(now)

        assertEquals(50.seconds, timer.remaining(now))
        assertEquals(50.seconds, timer.total)
    }

    @Test
    fun extend_with60SecondsLeft_leaves90() {
        val timer = RestTimer.start(120.seconds, t0).extend(t0 + 60.seconds)

        assertEquals(90.seconds, timer.remaining(t0 + 60.seconds))
    }

    @Test
    fun isFinished_exactlyAtTheEnd() {
        val timer = RestTimer.start(60.seconds, t0)

        assertFalse(timer.isFinished(t0 + 59.seconds))
        assertTrue(timer.isFinished(t0 + 60.seconds))
    }

    @Test
    fun pause_keepsTheRemainingAfterAnyElapsedTime() {
        val paused = RestTimer.start(120.seconds, t0).pause(t0 + 70.seconds)

        assertTrue(paused.isPaused)
        assertEquals(50.seconds, paused.remaining(t0 + 70.seconds))
        assertEquals(50.seconds, paused.remaining(t0 + 1000.seconds))
        assertEquals(120.seconds, paused.total)
    }

    @Test
    fun pausedTimer_neverFinishes() {
        val paused = RestTimer.start(60.seconds, t0).pause(t0 + 10.seconds)

        assertFalse(paused.isFinished(t0 + 5000.seconds))
    }

    @Test
    fun resume_endsAtNowPlusTheFrozenRemaining() {
        val resumeAt = t0 + 500.seconds
        val resumed = RestTimer.start(120.seconds, t0).pause(t0 + 70.seconds).resume(resumeAt)

        assertFalse(resumed.isPaused)
        assertEquals(resumeAt + 50.seconds, resumed.endsAt)
        assertEquals(50.seconds, resumed.remaining(resumeAt))
        assertTrue(resumed.isFinished(resumeAt + 50.seconds))
    }

    @Test
    fun extend_whilePaused_adds30SecondsToTheRemainingAndStaysPaused() {
        val paused = RestTimer.start(120.seconds, t0).pause(t0 + 70.seconds)
        val extended = paused.extend(t0 + 900.seconds)

        assertTrue(extended.isPaused)
        assertEquals(80.seconds, extended.remaining(t0 + 900.seconds))
    }

    @Test
    fun resolveRest_zeroMeansNoTimer() = assertNull(resolveRest(0, mapOf(0 to 0)))

    @Test
    fun resolveRest_soloUsesItsOwnRest() = assertEquals(90.seconds, resolveRest(1, mapOf(0 to 60, 1 to 90)))

    @Test
    fun resolveRest_supersetMembersUseTheirOwnRest() {
        val rests = mapOf(0 to 15, 1 to 120)

        assertEquals(15.seconds, resolveRest(0, rests))
        assertEquals(120.seconds, resolveRest(1, rests))
    }

    @Test
    fun resolveRest_addedExerciseDefaultsTo120() = assertEquals(120.seconds, resolveRest(5, mapOf(0 to 60)))
}
