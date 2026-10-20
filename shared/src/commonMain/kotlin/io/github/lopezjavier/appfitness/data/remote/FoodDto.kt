package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.FoodPortion
import io.github.lopezjavier.appfitness.domain.model.NewFoodEntry
import io.github.lopezjavier.appfitness.domain.model.NutritionPer100
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class FoodPortionDto(val id: String, val label: String, val amount: Double, val position: Int = 0)

/**
 * A food as returned by the `food-search` function (`portions`) or by PostgREST with the `food_portions` embed; only
 * one of the two portion keys is present.
 */
@Serializable
internal data class FoodDto(
    val id: String,
    val name: String,
    val brand: String? = null,
    @SerialName("base_unit") val baseUnit: String,
    @SerialName("kcal_100") val kcal100: Double,
    @SerialName("protein_100") val protein100: Double = 0.0,
    @SerialName("carbs_100") val carbs100: Double = 0.0,
    @SerialName("fat_100") val fat100: Double = 0.0,
    @SerialName("serving_label") val servingLabel: String? = null,
    @SerialName("serving_amount") val servingAmount: Double? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    val portions: List<FoodPortionDto> = emptyList(),
    @SerialName("food_portions") val foodPortions: List<FoodPortionDto> = emptyList(),
) {
    fun toDomain(): Food = Food(
        id = id,
        name = name,
        brand = brand,
        baseUnit = baseUnit,
        nutrition = NutritionPer100(kcal100, protein100, carbs100, fat100),
        servingLabel = servingLabel,
        servingAmount = servingAmount,
        portions = (portions + foodPortions).sortedBy { it.position }.map { FoodPortion(it.id, it.label, it.amount) },
        imageUrl = imageUrl,
    )

    companion object {
        /** PostgREST `select` of a food with its portions. */
        const val COLUMNS =
            "id,name,brand,base_unit,kcal_100,protein_100,carbs_100,fat_100,serving_label,serving_amount,image_url," +
                "food_portions(id,label,amount,position)"
    }
}

/** Body of `food-search`. */
@Serializable
internal data class FoodSearchResponseDto(val results: List<FoodDto> = emptyList(), val source: String? = null)

/** Row of `favorite_foods` with the food embedded. */
@Serializable
internal data class FavoriteFoodDto(@SerialName("food_id") val foodId: String, val foods: FoodDto? = null) {
    companion object {
        const val COLUMNS = "food_id,created_at,foods(${FoodDto.COLUMNS})"
    }
}

/** Insert body of `favorite_foods` (the user comes from the column default). */
@Serializable
internal data class FavoriteInsertDto(@SerialName("food_id") val foodId: String)

/** Row of `most_eaten_foods`. */
@Serializable
internal data class MostEatenDto(
    @SerialName("food_id") val foodId: String,
    @SerialName("times_logged") val timesLogged: Long = 0,
)

@Serializable
internal data class MostEatenParams(@SerialName("p_limit") val limit: Int)

/** Row of `food_log_entries` ids only, for the recents. */
@Serializable
internal data class LoggedFoodDto(@SerialName("food_id") val foodId: String)

/** Insert body of `food_log_entries` (the user comes from the column default). */
@Serializable
internal data class FoodLogEntryInsertDto(
    @SerialName("log_date") val logDate: String,
    @SerialName("meal_slot_id") val mealSlotId: String,
    @SerialName("food_id") val foodId: String,
    @SerialName("food_name") val foodName: String,
    val amount: Double,
    @SerialName("portion_label") val portionLabel: String?,
    val kcal: Double,
    @SerialName("protein_g") val proteinG: Double,
    @SerialName("carbs_g") val carbsG: Double,
    @SerialName("fat_g") val fatG: Double,
) {
    companion object {
        fun from(entry: NewFoodEntry) = FoodLogEntryInsertDto(
            logDate = entry.date.toString(),
            mealSlotId = entry.mealSlotId,
            foodId = entry.foodId,
            foodName = entry.foodName,
            amount = entry.amount,
            portionLabel = entry.portionLabel,
            kcal = entry.snapshot.kcal,
            proteinG = entry.snapshot.proteinG,
            carbsG = entry.snapshot.carbsG,
            fatG = entry.snapshot.fatG,
        )
    }
}
