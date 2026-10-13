package io.github.lopezjavier.appfitness.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals

class AppNumberFormatTest {
    @Test
    fun integer_zero() = assertEquals("0", AppNumberFormat.integer(0))

    @Test
    fun integer_threeDigits_noSeparator() = assertEquals("999", AppNumberFormat.integer(999))

    @Test
    fun integer_fourDigits_grouped() {
        assertEquals("1.000", AppNumberFormat.integer(1000))
        assertEquals("1.840", AppNumberFormat.integer(1840))
    }

    @Test
    fun integer_millions() = assertEquals("1.234.567", AppNumberFormat.integer(1_234_567))

    @Test
    fun integer_negative_usesUnicodeMinus() {
        assertEquals("−400", AppNumberFormat.integer(-400))
        assertEquals("−1.840", AppNumberFormat.integer(-1840))
    }

    @Test
    fun integer_minValue_doesNotOverflow() {
        assertEquals("−2.147.483.648", AppNumberFormat.integer(Int.MIN_VALUE))
    }
}
