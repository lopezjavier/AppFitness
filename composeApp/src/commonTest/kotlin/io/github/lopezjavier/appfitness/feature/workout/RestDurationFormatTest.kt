package io.github.lopezjavier.appfitness.feature.workout

import kotlin.test.Test
import kotlin.test.assertEquals

class RestDurationFormatTest {
    @Test
    fun underAMinuteIsSecondsOnly() {
        assertEquals(RestText(minutes = 0, seconds = 0), formatRestDuration(0))
        assertEquals(RestText(minutes = 0, seconds = 10), formatRestDuration(10))
        assertEquals(RestText(minutes = 0, seconds = 45), formatRestDuration(45))
    }

    @Test
    fun wholeMinutesHaveNoSeconds() {
        assertEquals(RestText(minutes = 1, seconds = 0), formatRestDuration(60))
        assertEquals(RestText(minutes = 2, seconds = 0), formatRestDuration(120))
        assertEquals(RestText(minutes = 10, seconds = 0), formatRestDuration(600))
    }

    @Test
    fun minutesAndSecondsAreBothKept() {
        assertEquals(RestText(minutes = 1, seconds = 10), formatRestDuration(70))
        assertEquals(RestText(minutes = 2, seconds = 10), formatRestDuration(130))
    }

    @Test
    fun aNegativeValueIsZero() {
        assertEquals(RestText(minutes = 0, seconds = 0), formatRestDuration(-5))
    }
}
