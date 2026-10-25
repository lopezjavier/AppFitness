package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.FoodPortion
import io.github.lopezjavier.appfitness.domain.model.NutritionSnapshot

/** Outcome of [RecalculateEntry]. */
sealed interface EntryRecalculation {
    /** [amount] is in the food's base unit and [snapshot] the nutrition of that amount. */
    data class Valid(val amount: Double, val snapshot: NutritionSnapshot) : EntryRecalculation

    data class Invalid(val check: QuantityCheck) : EntryRecalculation
}

/**
 * The amount and nutrition snapshot of a diary entry edited to [amountOrCount] (a count when [FoodPortion] is given):
 * validates what was typed, resolves the portion, validates the resolved amount and scales the food's per-100 values.
 * An entry keeps its added fat when the amount changes (ADR-60/65): [cookingOilRetainedG] (the entry's stored grams
 * of added fat, 0 for a raw one) is added to the scaled snapshot unchanged.
 */
class RecalculateEntry(
    private val resolvePortion: ResolvePortion,
    private val scaleNutrition: ScaleNutrition,
    private val validateQuantity: ValidateQuantity,
) {
    operator fun invoke(
        food: Food,
        amountOrCount: Double,
        portion: FoodPortion?,
        cookingOilRetainedG: Double = 0.0,
    ): EntryRecalculation {
        val typedCheck = validateQuantity(amountOrCount)
        if (typedCheck != QuantityCheck.Valid) return EntryRecalculation.Invalid(typedCheck)
        val amount = resolvePortion(amountOrCount, portion)
        val resolvedCheck = validateQuantity(amount)
        return if (resolvedCheck == QuantityCheck.Valid) {
            EntryRecalculation.Valid(
                amount,
                CookingImpact.apply(scaleNutrition(food.nutrition, amount), cookingOilRetainedG),
            )
        } else {
            EntryRecalculation.Invalid(resolvedCheck)
        }
    }
}
