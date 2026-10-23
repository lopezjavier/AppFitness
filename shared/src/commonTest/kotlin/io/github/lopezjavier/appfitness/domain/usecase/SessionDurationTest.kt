package io.github.lopezjavier.appfitness.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class SessionDurationTest {
    private val start = Instant.parse("2026-10-19T10:00:00Z")

    private fun at(minutes: Int) = start + minutes.minutes

    @Test
    fun running_isWallClockSinceStart() {
        assertEquals(600L, SessionDuration.seconds(start, null, null, 0, now = at(10)))
    }

    @Test
    fun paused_freezesAtThePauseInstant() {
        val result = SessionDuration.seconds(start, null, pausedAt = at(10), pausedTotalSeconds = 0, now = at(25))

        assertEquals(600L, result)
    }

    @Test
    fun resumed_excludesTheAccumulatedPause() {
        val result = SessionDuration.seconds(start, null, null, pausedTotalSeconds = 300, now = at(30))

        assertEquals(1500L, result)
    }

    @Test
    fun pausedAgainAfterAResume_subtractsEarlierPausesToo() {
        val result = SessionDuration.seconds(start, null, pausedAt = at(20), pausedTotalSeconds = 300, now = at(40))

        assertEquals(900L, result)
    }

    @Test
    fun finished_usesEndedAtMinusTotal() {
        val result = SessionDuration.seconds(start, endedAt = at(50), null, pausedTotalSeconds = 300, now = at(90))

        assertEquals(2700L, result)
    }

    @Test
    fun clockBeforeStart_coercesToZero() {
        assertEquals(0L, SessionDuration.seconds(start, null, null, 0, now = start - 5.seconds))
    }

    @Test
    fun totalLargerThanElapsed_coercesToZero() {
        assertEquals(0L, SessionDuration.seconds(start, null, null, pausedTotalSeconds = 9999, now = at(1)))
    }

    @Test
    fun finishedWhileStillPaused_foldsTheOpenPause() {
        val result = SessionDuration.seconds(
            start,
            endedAt = at(30),
            pausedAt = at(20),
            pausedTotalSeconds = 0,
            now = at(90),
        )

        assertEquals(1200L, result)
    }
}
