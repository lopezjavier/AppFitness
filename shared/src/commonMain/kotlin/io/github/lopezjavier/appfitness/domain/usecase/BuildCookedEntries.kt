package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.CookingMethod
import io.github.lopezjavier.appfitness.domain.model.FoodPreferences
import io.github.lopezjavier.appfitness.domain.model.MealTemplateItem
import io.github.lopezjavier.appfitness.domain.model.NewFoodEntry
import kotlinx.datetime.LocalDate

/**
 * The diary entries of a comida's [items] (ADR-63): each one carries its cooking method, the olive oil retained
 * according to the CURRENT [prefs] and a snapshot that includes that oil. Drinks (`ml`) are always raw.
 */
class BuildCookedEntries(private val scaleNutrition: ScaleNutrition) {
    operator fun invoke(
        items: List<MealTemplateItem>,
        mealSlotId: String,
        date: LocalDate,
        prefs: FoodPreferences,
    ): List<NewFoodEntry> = items.map { item ->
        val method = CookingImpact.effectiveMethod(item.cookingMethod, item.food.baseUnit)
        val retainedG = CookingImpact.retainedG(method, prefs)
        NewFoodEntry(
            date = date,
            mealSlotId = mealSlotId,
            foodId = item.food.id,
            foodName = item.food.name,
            amount = item.amount,
            portionLabel = item.portionLabel,
            snapshot = CookingImpact.apply(scaleNutrition(item.food.nutrition, item.amount), retainedG),
            cookingMethod = method,
            cookingOilRetainedG = retainedG,
        )
    }

    /** Whether any of [items] needs the preferences (a non-raw method). */
    fun needsPreferences(items: List<MealTemplateItem>): Boolean = items.any { it.cookingMethod != CookingMethod.Raw }
}
