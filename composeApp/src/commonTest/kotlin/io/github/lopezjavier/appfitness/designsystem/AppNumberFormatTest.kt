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
    fun signedInteger_marksTheSign() {
        assertEquals("−400", AppNumberFormat.signedInteger(-400))
        assertEquals("+250", AppNumberFormat.signedInteger(250))
        assertEquals("0", AppNumberFormat.signedInteger(0))
        assertEquals("−1.000", AppNumberFormat.signedInteger(-1000))
    }

    @Test
    fun decimal_wholeValues_haveNoDecimals() {
        assertEquals("0", AppNumberFormat.decimal(0.0))
        assertEquals("7", AppNumberFormat.decimal(7.0))
        assertEquals("300", AppNumberFormat.decimal(300.0))
    }

    @Test
    fun decimal_usesACommaAndOneDecimal() {
        assertEquals("7,5", AppNumberFormat.decimal(7.5))
        assertEquals("7,3", AppNumberFormat.decimal(7.2999999))
        assertEquals("87,5", AppNumberFormat.decimal(87.5))
    }

    @Test
    fun decimal_floatNoise_isRoundedAway() {
        assertEquals("7", AppNumberFormat.decimal(7.0000000001))
        assertEquals("6", AppNumberFormat.decimal(5.9999999999))
        assertEquals("7", AppNumberFormat.decimal(7.04))
    }

    @Test
    fun decimal_roundsHalfUp() {
        assertEquals("7,1", AppNumberFormat.decimal(7.05))
        assertEquals("8", AppNumberFormat.decimal(7.95))
        assertEquals("−7,1", AppNumberFormat.decimal(-7.05))
    }

    @Test
    fun decimal_groupsThousandsAndUsesUnicodeMinus() {
        assertEquals("1.234,6", AppNumberFormat.decimal(1234.56))
        assertEquals("−0,5", AppNumberFormat.decimal(-0.5))
        assertEquals("−1.000", AppNumberFormat.decimal(-1000.0))
    }

    @Test
    fun decimal_negativeThatRoundsToZero_hasNoSign() = assertEquals("0", AppNumberFormat.decimal(-0.04))

    @Test
    fun decimal_maxDecimals_keepsTheRequestedPrecision() {
        assertEquals("0,25", AppNumberFormat.decimal(0.25, maxDecimals = 2))
        assertEquals("2,5", AppNumberFormat.decimal(2.5, maxDecimals = 2))
        assertEquals("2", AppNumberFormat.decimal(2.0000001, maxDecimals = 2))
        assertEquals("1,05", AppNumberFormat.decimal(1.05, maxDecimals = 2))
    }

    @Test
    fun decimal_withoutGrouping_isParseableText() {
        assertEquals("1234,5", AppNumberFormat.decimal(1234.5, grouping = false))
    }

    @Test
    fun decimal_nonFiniteAndHugeValues_doNotCrash() {
        assertEquals("0", AppNumberFormat.decimal(Double.NaN))
        assertEquals("0", AppNumberFormat.decimal(Double.POSITIVE_INFINITY))
        assertEquals("999.999.999.999.999", AppNumberFormat.decimal(1.0e30))
    }

    @Test
    fun decimal_sleepHoursFromMinutes_neverShowMoreThanOneDecimal() {
        val hours = listOf(438.0000001 / 60, 360.0000001 / 60, 5.9999999999, 7.2999999, 8.123456789)
        hours.forEach { assertEquals(true, (AppNumberFormat.decimal(it).substringAfter(',', "")).length <= 1) }
        assertEquals("7,3", AppNumberFormat.decimal(438.0000001 / 60))
    }

    @Test
    fun integer_minValue_doesNotOverflow() {
        assertEquals("−2.147.483.648", AppNumberFormat.integer(Int.MIN_VALUE))
    }
}
