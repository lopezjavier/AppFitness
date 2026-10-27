package io.github.lopezjavier.appfitness.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RestAlertPatternTest {
    @Test
    fun hasThreePulses() {
        assertEquals(3, RestAlertPattern.pulseCount)
    }

    @Test
    fun lastsAboutOnePointTwoSeconds() {
        assertTrue(RestAlertPattern.totalMillis in 1100..1300)
    }

    @Test
    fun pulsesAreStrong() {
        assertTrue(RestAlertPattern.amplitudes.filter { it > 0 }.all { it > 200 })
    }

    @Test
    fun timingsAndAmplitudesHaveTheSameLength() {
        assertEquals(RestAlertPattern.timings.size, RestAlertPattern.amplitudes.size)
    }
}
