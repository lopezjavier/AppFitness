package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.CookingMethod
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.FoodPreferences
import io.github.lopezjavier.appfitness.domain.model.MealTemplateItem
import io.github.lopezjavier.appfitness.domain.model.NutritionPer100
import io.github.lopezjavier.appfitness.domain.model.NutritionSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

class CookedItemNutritionTest {
    private val cooked = CookedItemNutrition(ScaleNutrition())
    private val defaults = FoodPreferences()

    private fun item(unit: String = "g", amount: Double = 200.0, method: CookingMethod = CookingMethod.Raw) =
        MealTemplateItem(
            food = Food(
                id = "f",
                name = "Pollo",
                brand = null,
                baseUnit = unit,
                nutrition = NutritionPer100(kcal = 100.0, proteinG = 20.0, carbsG = 0.0, fatG = 2.0),
                servingLabel = null,
                servingAmount = null,
                portions = emptyList(),
                imageUrl = null,
            ),
            amount = amount,
            portionLabel = null,
            cookingMethod = method,
        )

    @Test
    fun raw_isTheScaledFood() {
        assertEquals(NutritionSnapshot(200.0, 40.0, 0.0, 4.0), cooked(item(), defaults))
    }

    @Test
    fun griddle_addsFatAsPercentOfTheAmountWithDefaults() {
        assertEquals(NutritionSnapshot(218.0, 40.0, 0.0, 6.0), cooked(item(method = CookingMethod.Griddle), defaults))
    }

    @Test
    fun airFryer_addsFatAsPercentOfTheAmountWithDefaults() {
        assertEquals(NutritionSnapshot(236.0, 40.0, 0.0, 8.0), cooked(item(method = CookingMethod.AirFryer), defaults))
    }

    @Test
    fun fried_addsFatAsPercentOfTheAmountWithDefaults() {
        assertEquals(NutritionSnapshot(344.0, 40.0, 0.0, 20.0), cooked(item(method = CookingMethod.Fried), defaults))
    }

    @Test
    fun fried_fatDependsOnTheAmount() {
        assertEquals(
            NutritionSnapshot(172.0, 20.0, 0.0, 10.0),
            cooked(item(amount = 100.0, method = CookingMethod.Fried), defaults),
        )
    }

    @Test
    fun nullPreferences_meansNoImpact() {
        assertEquals(NutritionSnapshot(200.0, 40.0, 0.0, 4.0), cooked(item(method = CookingMethod.Fried), null))
    }

    @Test
    fun drinks_areAlwaysRaw() {
        assertEquals(
            NutritionSnapshot(200.0, 40.0, 0.0, 4.0),
            cooked(item("ml", method = CookingMethod.Fried), defaults),
        )
    }

    @Test
    fun totals_sumTheItemsIncludingTheImpact() {
        val total = cooked.total(
            listOf(item(method = CookingMethod.Griddle), item(amount = 100.0, method = CookingMethod.Fried)),
            defaults,
        )

        assertEquals(NutritionSnapshot(218.0 + 172.0, 60.0, 0.0, 6.0 + 10.0), total)
    }

    @Test
    fun impactOf_isNullForRawAndDrinks() {
        assertEquals(null, cooked.retainedG(item(), defaults))
        assertEquals(null, cooked.retainedG(item("ml", method = CookingMethod.Fried), defaults))
        assertEquals(2.0, cooked.retainedG(item(method = CookingMethod.Griddle), defaults))
        assertEquals(null, cooked.retainedG(item(method = CookingMethod.Griddle), null))
    }
}
