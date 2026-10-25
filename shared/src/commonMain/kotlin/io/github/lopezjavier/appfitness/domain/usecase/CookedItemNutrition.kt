package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.CookingMethod
import io.github.lopezjavier.appfitness.domain.model.FoodPreferences
import io.github.lopezjavier.appfitness.domain.model.MealTemplateItem
import io.github.lopezjavier.appfitness.domain.model.NutritionSnapshot

/**
 * Nutrition of a comida item including the olive oil of its cooking method (ADR-60/63, `04` §2.2), computed with
 * the CURRENT [FoodPreferences]. A null `prefs` means "no impact" (preferences unavailable). Drinks stay raw.
 */
class CookedItemNutrition(private val scaleNutrition: ScaleNutrition) {
    operator fun invoke(item: MealTemplateItem, prefs: FoodPreferences?): NutritionSnapshot {
        val base = scaleNutrition(item.food.nutrition, item.amount)
        val retainedG = retainedG(item, prefs) ?: return base
        return CookingImpact.apply(base, retainedG)
    }

    fun total(items: List<MealTemplateItem>, prefs: FoodPreferences?): NutritionSnapshot =
        items.map { invoke(it, prefs) }.fold(NutritionSnapshot(0.0, 0.0, 0.0, 0.0)) { acc, n ->
            NutritionSnapshot(
                kcal = acc.kcal + n.kcal,
                proteinG = acc.proteinG + n.proteinG,
                carbsG = acc.carbsG + n.carbsG,
                fatG = acc.fatG + n.fatG,
            )
        }

    /** Grams of oil retained by [item], or null when there is no impact (raw, a drink or no preferences). */
    fun retainedG(item: MealTemplateItem, prefs: FoodPreferences?): Double? {
        if (prefs == null) return null
        val method = CookingImpact.effectiveMethod(item.cookingMethod, item.food.baseUnit)
        return if (method == CookingMethod.Raw) null else CookingImpact.retainedG(method, prefs)
    }
}
