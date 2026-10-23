package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.CalculatorSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlateCalculatorTest {
    private val all = CalculatorSettings.PLATE_CATALOGUE

    @Test
    fun hundredKilosWithTwentyKiloBar_isTwentyFivePlusFifteenPerSide() {
        val result = PlateCalculator(100.0, 20.0, all)

        assertEquals(listOf(25.0, 15.0), result.platesPerSide)
        assertEquals(100.0, result.achievedKg)
        assertTrue(result.exact)
        assertEquals("25 + 15", result.perSideText())
    }

    @Test
    fun targetEqualToTheBar_needsNoPlates() {
        val result = PlateCalculator(20.0, 20.0, all)

        assertTrue(result.platesPerSide.isEmpty())
        assertTrue(result.exact)
        assertEquals(20.0, result.achievedKg)
    }

    @Test
    fun smallPlatesAreUsed() {
        assertEquals(listOf(25.0, 15.0, 1.25), PlateCalculator(102.5, 20.0, all).platesPerSide)
        assertEquals("25 + 15 + 1,25", PlateCalculator(102.5, 20.0, all).perSideText())
        assertEquals(listOf(2.0), PlateCalculator(24.0, 20.0, all).platesPerSide)
        assertEquals(listOf(1.0), PlateCalculator(22.0, 20.0, all).platesPerSide)
        assertEquals(listOf(0.5), PlateCalculator(21.0, 20.0, all).platesPerSide)
    }

    @Test
    fun withoutTheTwentyFive_theNextPlatesAreUsed() {
        val result = PlateCalculator(100.0, 20.0, all - 25.0)

        assertEquals(listOf(20.0, 20.0), result.platesPerSide)
        assertTrue(result.exact)
    }

    @Test
    fun impossibleTarget_showsTheClosestLowerWeight() {
        val result = PlateCalculator(101.0, 20.0, listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5))

        assertEquals(listOf(25.0, 15.0), result.platesPerSide)
        assertEquals(100.0, result.achievedKg)
        assertFalse(result.exact)
    }

    @Test
    fun theClosestLowerWeightIsNotJustGreedy() {
        // 30 kg per side with only 20 and 15 plates: greedy would stop at 20; 15 + 15 is exact.
        val result = PlateCalculator(80.0, 20.0, listOf(20.0, 15.0))

        assertEquals(listOf(15.0, 15.0), result.platesPerSide)
        assertTrue(result.exact)
    }

    @Test
    fun targetBelowTheBar_showsTheBar() {
        val result = PlateCalculator(15.0, 20.0, all)

        assertTrue(result.platesPerSide.isEmpty())
        assertEquals(20.0, result.achievedKg)
        assertFalse(result.exact)
    }

    @Test
    fun lighterBars() {
        assertEquals(listOf(10.0, 1.25), PlateCalculator(30.0, 7.5, all - listOf(25.0, 20.0, 15.0)).platesPerSide)
        assertEquals(listOf(5.0), PlateCalculator(30.0, 20.0, all).platesPerSide)
        assertEquals(17.5, PlateCalculator(17.5, 7.5, all).achievedKg)
    }

    @Test
    fun noPlatesAvailable_isJustTheBar() {
        val result = PlateCalculator(100.0, 20.0, emptyList())

        assertEquals(20.0, result.achievedKg)
        assertFalse(result.exact)
    }

    @Test
    fun emptyPlatesText_isBlank() {
        assertEquals("", PlateCalculator(20.0, 20.0, all).perSideText())
    }

    @Test
    fun dumbbellTwentyOneKilosWithOneKiloHandle_isTenPerSide() {
        val result = PlateCalculator(21.0, 1.0, all)

        assertEquals("10", result.perSideText())
        assertEquals(21.0, result.achievedKg)
        assertTrue(result.exact)
    }

    @Test
    fun dumbbellTargetBetweenSteps_givesTheClosestLowerAndIsNotExact() {
        val result = PlateCalculator(20.3, 1.0, all)

        assertEquals(20.0, result.achievedKg)
        assertFalse(result.exact)
    }

    @Test
    fun dumbbellTargetAtOrBelowTheHandle_givesTheHandleOnly() {
        val atHandle = PlateCalculator(1.0, 1.0, all)
        val below = PlateCalculator(0.5, 1.0, all)

        assertEquals("", atHandle.perSideText())
        assertTrue(atHandle.exact)
        assertEquals(1.0, below.achievedKg)
        assertFalse(below.exact)
    }
}
