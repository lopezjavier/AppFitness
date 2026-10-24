package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.LabelNutrient
import io.github.lopezjavier.appfitness.domain.model.LabelNutrientKey
import io.github.lopezjavier.appfitness.domain.model.MicronutrientKind
import io.github.lopezjavier.appfitness.domain.model.MicronutrientUnit
import io.github.lopezjavier.appfitness.domain.model.Micronutrients
import io.github.lopezjavier.appfitness.domain.model.NutritionPer100

/**
 * The nutrient rows of a food in label order (Annex XV, then vitamins and minerals). Energy, fat, carbohydrates and
 * protein are always present; an extra nutrient only when the food has it. The single source of the order for manual
 * and Open Food Facts foods alike.
 */
object BuildLabelNutrients {
    operator fun invoke(nutrition: NutritionPer100, micronutrients: Micronutrients): List<LabelNutrient> {
        val extras = micronutrients.items.associateBy { it.kind }
        val columns = mapOf(
            LabelNutrientKey.Energy to (nutrition.kcal to null),
            LabelNutrientKey.Fat to (nutrition.fatG to MicronutrientUnit.G),
            LabelNutrientKey.Carbs to (nutrition.carbsG to MicronutrientUnit.G),
            LabelNutrientKey.Protein to (nutrition.proteinG to MicronutrientUnit.G),
        )
        fun column(key: LabelNutrientKey) =
            columns.getValue(key).let { (amount, unit) -> LabelNutrient(key, amount, unit) }

        return buildList {
            add(column(LabelNutrientKey.Energy))
            // Each column goes right before the first extra of its group (fat, carbs, protein).
            MicronutrientKind.entries.forEach { kind ->
                if (kind == MicronutrientKind.Salt) add(column(LabelNutrientKey.Protein))
                if (kind == MicronutrientKind.SaturatedFat) add(column(LabelNutrientKey.Fat))
                if (kind == MicronutrientKind.Sugars) add(column(LabelNutrientKey.Carbs))
                extras[kind]?.let { add(LabelNutrient(LabelNutrientKey.Extra(kind), it.amount, it.unit)) }
            }
        }
    }
}
