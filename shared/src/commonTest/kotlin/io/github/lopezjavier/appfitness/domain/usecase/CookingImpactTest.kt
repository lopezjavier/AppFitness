package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.CookingMethod
import io.github.lopezjavier.appfitness.domain.model.FoodPreferences
import io.github.lopezjavier.appfitness.domain.model.NutritionPer100
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CookingImpactTest {
    private val defaults = FoodPreferences()

    @Test
    fun griddleWithDefaultsRetainsTwoAndAHalfGrams() {
        val retained = CookingImpact.retainedG(CookingMethod.Griddle, defaults)
        assertEquals(2.5, retained)
        assertEquals(22.5, CookingImpact.addedKcal(retained))
        assertEquals(2.5, CookingImpact.addedFatG(retained))
    }

    @Test
    fun airFryerWithDefaultsRetainsAllFiveGrams() {
        val retained = CookingImpact.retainedG(CookingMethod.AirFryer, defaults)
        assertEquals(5.0, retained)
        assertEquals(45.0, CookingImpact.addedKcal(retained))
        assertEquals(5.0, CookingImpact.addedFatG(retained))
    }

    @Test
    fun friedWithDefaultsRetainsTenPercentOfFiftyGrams() {
        val retained = CookingImpact.retainedG(CookingMethod.Fried, defaults)
        assertEquals(5.0, retained)
        assertEquals(45.0, CookingImpact.addedKcal(retained))
    }

    @Test
    fun rawAddsNothing() {
        val retained = CookingImpact.retainedG(CookingMethod.Raw, defaults)
        assertEquals(0.0, retained)
        assertEquals(0.0, CookingImpact.addedKcal(retained))
        assertEquals(0.0, CookingImpact.addedFatG(retained))
    }

    @Test
    fun customGriddleAmountIsUsed() {
        val retained = CookingImpact.retainedG(CookingMethod.Griddle, FoodPreferences(oliveOilGriddleG = 10.0))
        assertEquals(5.0, retained)
        assertEquals(45.0, CookingImpact.addedKcal(retained))
        assertEquals(5.0, CookingImpact.addedFatG(retained))
    }

    @Test
    fun zeroGramsRetainsNothing() {
        assertEquals(0.0, CookingImpact.retainedG(CookingMethod.Griddle, FoodPreferences(oliveOilGriddleG = 0.0)))
    }

    @Test
    fun maximumFriedAmountRetainsFiftyGrams() {
        val retained = CookingImpact.retainedG(CookingMethod.Fried, FoodPreferences(oliveOilFriedG = 500.0))
        assertEquals(50.0, retained)
        assertEquals(450.0, CookingImpact.addedKcal(retained))
        assertEquals(50.0, CookingImpact.addedFatG(retained))
    }

    @Test
    fun retainedOilIsRoundedToTwoDecimals() {
        // 3.33 g x 0.5 = 1.665 -> 1.67 (half up, like the server).
        assertEquals(1.67, CookingImpact.retainedG(CookingMethod.Griddle, FoodPreferences(oliveOilGriddleG = 3.33)))
    }

    @Test
    fun addedKcalAndFatAreRoundedToOneDecimal() {
        assertEquals(15.0, CookingImpact.addedKcal(1.67))
        assertEquals(1.7, CookingImpact.addedFatG(1.67))
    }

    @Test
    fun chickenGriddledAddsOilToKcalAndFatOnly() {
        val chicken = ScaleNutrition()(NutritionPer100(110.0, 23.0, 0.0, 3.0), 200.0)
        val cooked = CookingImpact.apply(chicken, CookingMethod.Griddle, defaults)
        assertEquals(242.5, cooked.kcal)
        assertEquals(8.5, cooked.fatG)
        assertEquals(chicken.proteinG, cooked.proteinG)
        assertEquals(chicken.carbsG, cooked.carbsG)
    }

    @Test
    fun applyingRawLeavesTheSnapshotUnchanged() {
        val base = ScaleNutrition()(NutritionPer100(110.0, 23.0, 1.0, 3.0), 150.0)
        assertEquals(base, CookingImpact.apply(base, CookingMethod.Raw, defaults))
    }

    @Test
    fun onlyFoodsInGramsCanBeCooked() {
        assertTrue(CookingImpact.appliesTo("g"))
        assertFalse(CookingImpact.appliesTo("ml"))
        assertFalse(CookingImpact.appliesTo(null))
    }

    @Test
    fun drinksAreAlwaysRaw() {
        assertEquals(CookingMethod.Raw, CookingImpact.effectiveMethod(CookingMethod.Fried, "ml"))
        assertEquals(CookingMethod.Fried, CookingImpact.effectiveMethod(CookingMethod.Fried, "g"))
    }
}
