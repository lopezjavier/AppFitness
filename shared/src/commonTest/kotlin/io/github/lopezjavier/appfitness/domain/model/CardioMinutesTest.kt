package io.github.lopezjavier.appfitness.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CardioMinutesTest {
    @Test
    fun belowRecommended_isTrueOnlyUnder150() {
        assertTrue(CardioMinutes.isBelowRecommended(149))
        assertTrue(CardioMinutes.isBelowRecommended(0))
        assertFalse(CardioMinutes.isBelowRecommended(150))
        assertFalse(CardioMinutes.isBelowRecommended(600))
    }

    @Test
    fun isValid_acceptsStepsOfFiveBetween0And600() {
        assertTrue(CardioMinutes.isValid(0))
        assertTrue(CardioMinutes.isValid(90))
        assertTrue(CardioMinutes.isValid(600))
        assertFalse(CardioMinutes.isValid(-5))
        assertFalse(CardioMinutes.isValid(7))
        assertFalse(CardioMinutes.isValid(605))
    }

    @Test
    fun clamp_roundsToTheNearestStepAndStaysInRange() {
        assertEquals(150, CardioMinutes.clamp(150))
        assertEquals(150, CardioMinutes.clamp(152))
        assertEquals(155, CardioMinutes.clamp(153))
        assertEquals(0, CardioMinutes.clamp(-40))
        assertEquals(600, CardioMinutes.clamp(900))
    }
}
