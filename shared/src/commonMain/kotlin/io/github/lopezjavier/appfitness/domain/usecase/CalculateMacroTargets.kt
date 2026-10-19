package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.MacroTargets
import io.github.lopezjavier.appfitness.domain.model.Macronutrient

/** Grams and kcal of each macro for a daily target (planning/04 §1.5); exact integer maths, halves round up. */
object CalculateMacroTargets {
    private const val PERCENT = 100

    operator fun invoke(targetKcal: Int, split: MacroSplit): MacroTargets = MacroTargets(
        proteinG = grams(targetKcal, split.proteinPct, Macronutrient.Protein),
        carbsG = grams(targetKcal, split.carbsPct, Macronutrient.Carbs),
        fatG = grams(targetKcal, split.fatPct, Macronutrient.Fat),
        proteinKcal = kcal(targetKcal, split.proteinPct),
        carbsKcal = kcal(targetKcal, split.carbsPct),
        fatKcal = kcal(targetKcal, split.fatPct),
    )

    private fun kcal(targetKcal: Int, pct: Int): Int = roundedDiv(targetKcal * pct, PERCENT)

    private fun grams(targetKcal: Int, pct: Int, macro: Macronutrient): Int =
        roundedDiv(targetKcal * pct, PERCENT * macro.kcalPerGram)

    /** Half-up integer division. */
    private fun roundedDiv(numerator: Int, denominator: Int): Int = (2 * numerator + denominator) / (2 * denominator)
}
