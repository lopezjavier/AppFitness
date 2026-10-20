package io.github.lopezjavier.appfitness.domain.model

import kotlinx.datetime.LocalDate

/** Nutrition of 100 base units (g or ml) of a food. */
data class NutritionPer100(val kcal: Double, val proteinG: Double, val carbsG: Double, val fatG: Double)

/** Nutrition of an amount eaten, in tenths of a unit (the snapshot stored in a diary entry). */
data class NutritionSnapshot(val kcal: Double, val proteinG: Double, val carbsG: Double, val fatG: Double)

/** A named amount of a food ("1 huevo" = 55 g), in base units. */
data class FoodPortion(val id: String, val label: String, val amount: Double)

/**
 * A catalogue food (`foods`). [baseUnit] is "g" or "ml". [servingLabel] / [servingAmount] describe the default
 * serving when the source provides one.
 */
data class Food(
    val id: String,
    val name: String,
    val brand: String?,
    val baseUnit: String,
    val nutrition: NutritionPer100,
    val servingLabel: String?,
    val servingAmount: Double?,
    val portions: List<FoodPortion>,
    val imageUrl: String?,
)

/** A diary entry to create: [amount] is in the food's base unit and [snapshot] is the nutrition of that amount. */
data class NewFoodEntry(
    val date: LocalDate,
    val mealSlotId: String,
    val foodId: String,
    val foodName: String,
    val amount: Double,
    val portionLabel: String?,
    val snapshot: NutritionSnapshot,
)

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
