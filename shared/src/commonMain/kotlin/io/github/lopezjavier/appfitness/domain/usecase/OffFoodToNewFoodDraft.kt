package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.MicronutrientKind
import io.github.lopezjavier.appfitness.domain.model.Micronutrients
import io.github.lopezjavier.appfitness.domain.model.NewFoodDraft
import io.github.lopezjavier.appfitness.domain.model.NewMicronutrients
import io.github.lopezjavier.appfitness.domain.model.NewPortion

/**
 * The create-food form prefill for correcting an Open Food Facts [Food] that fails the energy check (ADR-104): name,
 * brand, unit, kcal and macros, portions, barcode, plus the fibre and polyols of [extras] and the [alcohol] per 100.
 * Other nutrients are not carried: the check does not use them.
 */
object OffFoodToNewFoodDraft {
    private val CHECK_KINDS = setOf(MicronutrientKind.Fiber, MicronutrientKind.Polyols)

    operator fun invoke(food: Food, extras: Micronutrients, alcohol: Double?): NewFoodDraft {
        val values = extras.items.filter { it.kind in CHECK_KINDS }.associate {
            it.kind to it.kind.labelUnit.fromGrams(it.unit.toGrams(it.amount))
        }
        return NewFoodDraft(
            name = food.name,
            brand = food.brand,
            baseUnit = food.baseUnit,
            kcal = food.nutrition.kcal,
            protein = food.nutrition.proteinG,
            carbs = food.nutrition.carbsG,
            fat = food.nutrition.fatG,
            micronutrients = NewMicronutrients(values),
            portions = food.portions.map { NewPortion(it.label, it.amount) },
            barcode = food.barcode,
            alcohol = alcohol,
        )
    }
}
