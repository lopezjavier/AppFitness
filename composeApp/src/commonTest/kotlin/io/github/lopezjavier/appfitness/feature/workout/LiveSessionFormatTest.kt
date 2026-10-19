package io.github.lopezjavier.appfitness.feature.workout

import kotlin.test.Test
import kotlin.test.assertEquals

class LiveSessionFormatTest {
    @Test
    fun underAnHourShowsMinutesAndSeconds() {
        assertEquals("00:00", formatElapsed(0))
        assertEquals("05:07", formatElapsed(307))
        assertEquals("59:59", formatElapsed(3599))
    }

    @Test
    fun fromAnHourOnShowsHours() {
        assertEquals("1:00:00", formatElapsed(3600))
        assertEquals("1:02:03", formatElapsed(3723))
    }

    @Test
    fun aNegativeValueIsZero() {
        assertEquals("00:00", formatElapsed(-5))
    }

    @Test
    fun weightsShowWithoutTrailingZerosAndWithACommaDecimal() {
        assertEquals("60", weightText(60.0))
        assertEquals("87,5", weightText(87.5))
        assertEquals("0,25", weightText(0.25))
        assertEquals("0", weightText(0.0))
    }
}
