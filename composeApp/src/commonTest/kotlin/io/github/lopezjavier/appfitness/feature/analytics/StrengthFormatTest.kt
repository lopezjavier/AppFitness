package io.github.lopezjavier.appfitness.feature.analytics

import kotlin.test.Test
import kotlin.test.assertEquals

class StrengthFormatTest {
    @Test
    fun kilos_useOneDecimalWithComma() {
        assertEquals("113,5", formatKilos(113.5))
        assertEquals("100,0", formatKilos(100.0))
        assertEquals("0,5", formatKilos(0.5))
    }

    @Test
    fun kilos_groupThousands() {
        assertEquals("1.250,5", formatKilos(1250.5))
    }

    @Test
    fun signedKilos_marksPositiveWithPlusAndNegativeWithMinusSign() {
        assertEquals("+5,0", formatSignedKilos(5.0))
        assertEquals("−2,5", formatSignedKilos(-2.5))
    }

    @Test
    fun volume_roundsToWholeKilosWithGrouping() {
        assertEquals("4.260", formatVolume(4259.6))
        assertEquals("800", formatVolume(800.0))
    }
}
