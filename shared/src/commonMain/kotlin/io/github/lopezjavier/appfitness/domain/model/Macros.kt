package io.github.lopezjavier.appfitness.domain.model

private const val PROTEIN_CARBS_KCAL_PER_GRAM = 4
private const val FAT_KCAL_PER_GRAM = 9

/** The three tracked macronutrients with their energy density (planning/04-business-rules.md §1.5). */
enum class Macronutrient(val kcalPerGram: Int) {
    Protein(PROTEIN_CARBS_KCAL_PER_GRAM),
    Carbs(PROTEIN_CARBS_KCAL_PER_GRAM),
    Fat(FAT_KCAL_PER_GRAM),
}

/** Daily grams and kilocalories of each macro for a calorie target and a [MacroSplit]. */
data class MacroTargets(
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val proteinKcal: Int,
    val carbsKcal: Int,
    val fatKcal: Int,
)
