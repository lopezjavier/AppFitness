package io.github.lopezjavier.appfitness.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RpeScaleTest {
    @Test
    fun values_areTheWholeNumbersFromSixToTen() {
        assertEquals((6..10).map { it.toDouble() }, RpeScale.values)
    }

    @Test
    fun text_isAWholeNumberAndHalvesRoundUp() {
        assertEquals("6", RpeScale.text(6.0))
        assertEquals("8", RpeScale.text(7.5))
        assertEquals("8", RpeScale.text(8.0))
        assertEquals("7", RpeScale.text(6.5))
        assertEquals("10", RpeScale.text(10.0))
    }

    @Test
    fun text_ofAStoredValueBelowSixReadsAsSix() {
        assertEquals("6", RpeScale.text(3.0))
        assertEquals("6", RpeScale.text(1.0))
    }

    @Test
    fun round_isHalfUp() {
        assertEquals(7.0, RpeScale.round(6.5))
        assertEquals(8.0, RpeScale.round(7.5))
        assertEquals(7.0, RpeScale.round(7.4))
    }

    @Test
    fun round_clampsBelowSixToSix() {
        assertEquals(6.0, RpeScale.round(3.0))
    }

    @Test
    fun parse_toleratesHalvesAndRoundsThem() {
        assertEquals(8.0, RpeScale.parse("7,5"))
        assertEquals(8.0, RpeScale.parse("7.5"))
        assertEquals(8.0, RpeScale.parse("8"))
    }

    @Test
    fun parse_rejectsOutOfRangeAndGarbage() {
        listOf("0", "3", "5", "11", "abc", "").forEach { assertNull(RpeScale.parse(it), it) }
    }

    @Test
    fun isValid_onlyForWholeNumbers() {
        assertTrue(RpeScale.isValid(6.0))
        assertTrue(RpeScale.isValid(10.0))
        assertFalse(RpeScale.isValid(7.5))
        assertFalse(RpeScale.isValid(5.0))
        assertFalse(RpeScale.isValid(11.0))
    }
}
