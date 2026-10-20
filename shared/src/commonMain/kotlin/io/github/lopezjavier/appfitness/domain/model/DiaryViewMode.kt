package io.github.lopezjavier.appfitness.domain.model

/** How the Diary shows a day (US-19): every entry with macros, one line per entry, or only the meal totals. */
enum class DiaryViewMode { Detail, List, Summary }

/**
 * The new values of an existing diary entry: [amount] is in the food's base unit and [snapshot] the nutrition of
 * that amount.
 */
data class UpdatedFoodEntry(
    val mealSlotId: String,
    val amount: Double,
    val portionLabel: String?,
    val snapshot: NutritionSnapshot,
)
