package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.CalculatorSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class DiscLoaderTest {
    @Test
    fun barbellWithTwoDiscsPerSide_totalsOneHundred() {
        assertEquals(100.0, DiscLoader.total(20.0, mapOf(25.0 to 1, 15.0 to 1)))
    }

    @Test
    fun dumbbellHandleWithOneTenKgDisc_totalsTwentyOne() {
        assertEquals(21.0, DiscLoader.total(1.0, mapOf(10.0 to 1)))
    }

    @Test
    fun noDiscs_isTheBarOnly() {
        assertEquals(20.0, DiscLoader.total(20.0, emptyMap()))
    }

    @Test
    fun repeatedWeights_countEachDisc() {
        assertEquals(120.0, DiscLoader.total(20.0, mapOf(25.0 to 2)))
        assertEquals(50.0, DiscLoader.perSideKg(mapOf(25.0 to 2)))
    }

    @Test
    fun fractionalDisc_movesTheTotalInTwoAndAHalfKgSteps() {
        assertEquals(22.5, DiscLoader.total(20.0, mapOf(1.25 to 1)))
    }

    @Test
    fun nonPositiveCounts_areIgnored() {
        assertEquals(20.0, DiscLoader.total(20.0, mapOf(25.0 to 0, 10.0 to -2)))
    }

    @Test
    fun perSideText_listsHeaviestFirstRepeatingDiscs() {
        assertEquals("25 + 25 + 1,25", DiscLoader.perSideText(mapOf(1.25 to 1, 25.0 to 2)))
        assertEquals("", DiscLoader.perSideText(emptyMap()))
    }

    @Test
    fun roundingIsTwiceTheSmallestEnabledPlate() {
        assertEquals(1.0, CalculatorSettings().roundingKg)
        assertEquals(2.0, CalculatorSettings(plateKg = listOf(25.0, 1.0, 5.0)).roundingKg)
        assertEquals(50.0, CalculatorSettings(plateKg = listOf(25.0)).roundingKg)
        assertEquals(2.5, CalculatorSettings(plateKg = listOf(2.5, 1.25, 10.0)).roundingKg)
    }

    @Test
    fun roundingFallsBackToTheCatalogueWhenNoPlateIsEnabled() {
        assertEquals(1.0, CalculatorSettings(plateKg = emptyList()).roundingKg)
    }
}
