package io.github.lopezjavier.appfitness.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FoodPreferencesTest {
    @Test
    fun defaultsAreOneTwoAndEightPercent() {
        val prefs = FoodPreferences()
        assertEquals(1.0, prefs.fatPctGriddle)
        assertEquals(2.0, prefs.fatPctAirFryer)
        assertEquals(8.0, prefs.fatPctFried)
        assertTrue(prefs.isValid)
    }

    @Test
    fun zeroAndTwentyAreValid() {
        assertTrue(FoodPreferences(0.0, 0.0, 0.0).isValid)
        assertTrue(FoodPreferences(20.0, 20.0, 20.0).isValid)
    }

    @Test
    fun negativeOrAboveTwentyOrNaNIsInvalid() {
        assertFalse(FoodPreferences(fatPctGriddle = -0.1).isValid)
        assertFalse(FoodPreferences(fatPctAirFryer = 20.1).isValid)
        assertFalse(FoodPreferences(fatPctFried = Double.NaN).isValid)
    }

    @Test
    fun pctForReadsThePercentageOfEachMethod() {
        val prefs = FoodPreferences(1.5, 2.5, 3.5)
        assertEquals(0.0, prefs.pctFor(CookingMethod.Raw))
        assertEquals(1.5, prefs.pctFor(CookingMethod.Griddle))
        assertEquals(2.5, prefs.pctFor(CookingMethod.AirFryer))
        assertEquals(3.5, prefs.pctFor(CookingMethod.Fried))
    }

    @Test
    fun wireNamesMatchTheServerEnum() {
        assertEquals(listOf("raw", "griddle", "air_fryer", "fried"), CookingMethod.entries.map { it.wireName })
        assertEquals(CookingMethod.AirFryer, CookingMethod.fromWireName("air_fryer"))
    }
}
