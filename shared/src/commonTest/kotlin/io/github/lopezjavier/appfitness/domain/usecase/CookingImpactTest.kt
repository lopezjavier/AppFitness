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
    fun griddleOf300gWithDefaultsAddsThreeGramsOfFat() {
        val fat = CookingImpact.addedFatG(CookingMethod.Griddle, 300.0, defaults)
        assertEquals(3.0, fat)
        assertEquals(27.0, CookingImpact.addedKcal(fat))
    }

    @Test
    fun airFryerOf300gWithDefaultsAddsSixGramsOfFat() {
        val fat = CookingImpact.addedFatG(CookingMethod.AirFryer, 300.0, defaults)
        assertEquals(6.0, fat)
        assertEquals(54.0, CookingImpact.addedKcal(fat))
    }

    @Test
    fun friedOf300gWithDefaultsAddsTwentyFourGramsOfFat() {
        val fat = CookingImpact.addedFatG(CookingMethod.Fried, 300.0, defaults)
        assertEquals(24.0, fat)
        assertEquals(216.0, CookingImpact.addedKcal(fat))
    }

    @Test
    fun friedOf200gAddsSixteenGramsAndOneHundredFortyFourKcal() {
        val fat = CookingImpact.addedFatG(CookingMethod.Fried, 200.0, defaults)
        assertEquals(16.0, fat)
        assertEquals(144.0, CookingImpact.addedKcal(fat))
    }

    @Test
    fun rawAddsNothing() {
        val fat = CookingImpact.addedFatG(CookingMethod.Raw, 300.0, defaults)
        assertEquals(0.0, fat)
        assertEquals(0.0, CookingImpact.addedKcal(fat))
    }

    @Test
    fun customPercentageIsUsed() {
        assertEquals(10.0, CookingImpact.addedFatG(CookingMethod.Griddle, 200.0, FoodPreferences(fatPctGriddle = 5.0)))
    }

    @Test
    fun zeroPercentAddsNothing() {
        assertEquals(0.0, CookingImpact.addedFatG(CookingMethod.Fried, 300.0, FoodPreferences(fatPctFried = 0.0)))
    }

    @Test
    fun maximumPercentAddsTwentyPercentOfTheWeight() {
        val fat = CookingImpact.addedFatG(CookingMethod.Fried, 300.0, FoodPreferences(fatPctFried = 20.0))
        assertEquals(60.0, fat)
        assertEquals(540.0, CookingImpact.addedKcal(fat))
    }

    @Test
    fun fatIsRoundedToOneDecimalHalfUpLikeTheServer() {
        // 175 g x 1 % = 1.75 -> 1.8; kcal come from the rounded fat: 1.8 x 9 = 16.2.
        val fat = CookingImpact.addedFatG(CookingMethod.Griddle, 175.0, defaults)
        assertEquals(1.8, fat)
        assertEquals(16.2, CookingImpact.addedKcal(fat))
    }

    @Test
    fun chickenGriddledAddsFatToKcalAndFatOnly() {
        val chicken = ScaleNutrition()(NutritionPer100(110.0, 23.0, 0.0, 3.0), 200.0)
        val cooked = CookingImpact.apply(chicken, CookingMethod.Griddle, 200.0, defaults)
        assertEquals(238.0, cooked.kcal)
        assertEquals(8.0, cooked.fatG)
        assertEquals(chicken.proteinG, cooked.proteinG)
        assertEquals(chicken.carbsG, cooked.carbsG)
    }

    @Test
    fun applyingRawLeavesTheSnapshotUnchanged() {
        val base = ScaleNutrition()(NutritionPer100(110.0, 23.0, 1.0, 3.0), 150.0)
        assertEquals(base, CookingImpact.apply(base, CookingMethod.Raw, 150.0, defaults))
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
