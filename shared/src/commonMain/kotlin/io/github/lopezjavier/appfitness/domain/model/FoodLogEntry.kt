package io.github.lopezjavier.appfitness.domain.model

import kotlinx.datetime.LocalDate

/**
 * One logged food (`food_log_entries`). The nutrient values are snapshots of the amount eaten, in tenths of a unit.
 * [baseUnit] is the food's unit ("g" or "ml"), null when unknown.
 */
data class FoodLogEntry(
    val id: String,
    val logDate: LocalDate,
    val mealSlotId: String,
    val foodId: String,
    val foodName: String,
    val amount: Double,
    val portionLabel: String?,
    val baseUnit: String?,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
)
