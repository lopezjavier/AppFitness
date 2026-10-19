package io.github.lopezjavier.appfitness.domain.model

/** Energy strategy of `nutrition_goals.strategy` (planning/04-business-rules.md §1.4). */
enum class GoalStrategy { Deficit, Maintenance, Surplus }

/** Macro split in whole percent of the daily calories; the three values add up to 100. */
data class MacroSplit(val proteinPct: Int, val carbsPct: Int, val fatPct: Int) {
    operator fun get(macro: Macronutrient): Int = when (macro) {
        Macronutrient.Protein -> proteinPct
        Macronutrient.Carbs -> carbsPct
        Macronutrient.Fat -> fatPct
    }

    fun with(macro: Macronutrient, percent: Int): MacroSplit = when (macro) {
        Macronutrient.Protein -> copy(proteinPct = percent)
        Macronutrient.Carbs -> copy(carbsPct = percent)
        Macronutrient.Fat -> copy(fatPct = percent)
    }

    companion object {
        val DEFAULT = MacroSplit(proteinPct = 30, carbsPct = 45, fatPct = 25)
    }
}

/** The user's daily goals: [calorieAdjustment] is the choice made, [targetKcal] the stored (floored) target. */
data class NutritionGoals(
    val strategy: GoalStrategy,
    val calorieAdjustment: Int,
    val targetKcal: Int,
    val split: MacroSplit = MacroSplit.DEFAULT,
)
