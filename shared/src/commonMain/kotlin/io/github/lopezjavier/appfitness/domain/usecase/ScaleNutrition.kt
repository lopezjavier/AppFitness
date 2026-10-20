package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.NutritionPer100
import io.github.lopezjavier.appfitness.domain.model.NutritionSnapshot

private const val PER = 100.0

/** `value_100 * amount / 100` for each nutrient, rounded to 0.1 (04-business-rules.md section 2). */
class ScaleNutrition {
    operator fun invoke(per100: NutritionPer100, amount: Double): NutritionSnapshot = NutritionSnapshot(
        kcal = scale(per100.kcal, amount),
        proteinG = scale(per100.proteinG, amount),
        carbsG = scale(per100.carbsG, amount),
        fatG = scale(per100.fatG, amount),
    )

    private fun scale(per100: Double, amount: Double) = roundToTenth(per100 * amount / PER)
}
