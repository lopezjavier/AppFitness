package io.github.lopezjavier.appfitness.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FoodPreferencesTest {
    @Test
    fun defaultsAreFiveFiveAndFifty() {
        val prefs = FoodPreferences()
        assertEquals(5.0, prefs.oliveOilGriddleG)
        assertEquals(5.0, prefs.oliveOilAirFryerG)
        assertEquals(50.0, prefs.oliveOilFriedG)
        assertTrue(prefs.isValid)
    }

    @Test
    fun zeroAndFiveHundredAreValid() {
        assertTrue(FoodPreferences(0.0, 0.0, 0.0).isValid)
        assertTrue(FoodPreferences(500.0, 500.0, 500.0).isValid)
    }

    @Test
    fun negativeOrAboveFiveHundredIsInvalid() {
        assertFalse(FoodPreferences(oliveOilGriddleG = -0.1).isValid)
        assertFalse(FoodPreferences(oliveOilAirFryerG = 500.1).isValid)
        assertFalse(FoodPreferences(oliveOilFriedG = Double.NaN).isValid)
    }

    @Test
    fun usedGReadsTheAmountOfEachMethod() {
        val prefs = FoodPreferences(1.0, 2.0, 3.0)
        assertEquals(0.0, prefs.usedG(CookingMethod.Raw))
        assertEquals(1.0, prefs.usedG(CookingMethod.Griddle))
        assertEquals(2.0, prefs.usedG(CookingMethod.AirFryer))
        assertEquals(3.0, prefs.usedG(CookingMethod.Fried))
    }

    @Test
    fun wireNamesMatchTheServerEnum() {
        assertEquals(listOf("raw", "griddle", "air_fryer", "fried"), CookingMethod.entries.map { it.wireName })
        assertEquals(CookingMethod.AirFryer, CookingMethod.fromWireName("air_fryer"))
    }
}
