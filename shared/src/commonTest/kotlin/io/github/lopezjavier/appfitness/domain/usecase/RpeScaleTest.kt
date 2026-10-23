package io.github.lopezjavier.appfitness.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RpeScaleTest {
    @Test
    fun values_areNineHalfPointsFromSixToTen() {
        assertEquals(listOf(6.0, 6.5, 7.0, 7.5, 8.0, 8.5, 9.0, 9.5, 10.0), RpeScale.values)
    }

    @Test
    fun text_hasNoTrailingZeroAndUsesADecimalComma() {
        assertEquals("6", RpeScale.text(6.0))
        assertEquals("7,5", RpeScale.text(7.5))
        assertEquals("10", RpeScale.text(10.0))
    }

    @Test
    fun parse_acceptsCommaAndDot() {
        assertEquals(7.5, RpeScale.parse("7,5"))
        assertEquals(7.5, RpeScale.parse("7.5"))
        assertEquals(8.0, RpeScale.parse("8"))
    }

    @Test
    fun parse_rejectsValuesOffTheGridAndGarbage() {
        listOf("5.5", "10.5", "7.25", "abc", "").forEach { assertNull(RpeScale.parse(it), it) }
    }

    @Test
    fun isValid_onlyForGridValues() {
        assertTrue(RpeScale.isValid(9.5))
        assertFalse(RpeScale.isValid(5.5))
        assertFalse(RpeScale.isValid(7.25))
        assertFalse(RpeScale.isValid(10.5))
    }
}
