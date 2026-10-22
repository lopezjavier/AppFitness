package io.github.lopezjavier.appfitness.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BmiTest {
    private val bmi = Bmi()

    @Test
    fun weightAndHeight_giveBmiWithOneDecimal() {
        assertEquals(23.4, bmi.compute(74.2, 178))
    }

    @Test
    fun missingHeight_givesNoBmi() {
        assertNull(bmi.compute(74.2, null))
    }

    @Test
    fun statusBoundaries_followTheTable() {
        assertEquals(BmiStatus.Underweight, bmi.status(18.4))
        assertEquals(BmiStatus.Normal, bmi.status(18.5))
        assertEquals(BmiStatus.Normal, bmi.status(24.9))
        assertEquals(BmiStatus.Overweight, bmi.status(25.0))
        assertEquals(BmiStatus.Overweight, bmi.status(29.9))
        assertEquals(BmiStatus.Obese, bmi.status(30.0))
    }

    @Test
    fun statusComparesOnTheRoundedValue() {
        assertEquals(BmiStatus.Overweight, bmi.status(24.96))
        assertEquals(BmiStatus.Normal, bmi.status(18.46))
        assertEquals(BmiStatus.Underweight, bmi.status(18.44))
    }
}
