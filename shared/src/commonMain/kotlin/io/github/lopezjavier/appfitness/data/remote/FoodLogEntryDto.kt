package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The embedded `foods(base_unit)` of an entry. */
@Serializable
internal data class FoodBaseUnitDto(@SerialName("base_unit") val baseUnit: String)

/** Row of `public.food_log_entries` as read, with the food's base unit embedded. */
@Serializable
internal data class FoodLogEntryDto(
    val id: String,
    @SerialName("log_date") val logDate: String,
    @SerialName("meal_slot_id") val mealSlotId: String,
    @SerialName("food_id") val foodId: String,
    @SerialName("food_name") val foodName: String,
    val amount: Double,
    @SerialName("portion_label") val portionLabel: String? = null,
    val kcal: Double,
    @SerialName("protein_g") val proteinG: Double,
    @SerialName("carbs_g") val carbsG: Double,
    @SerialName("fat_g") val fatG: Double,
    val foods: FoodBaseUnitDto? = null,
) {
    fun toDomain(): FoodLogEntry = FoodLogEntry(
        id = id,
        logDate = LocalDate.parse(logDate),
        mealSlotId = mealSlotId,
        foodId = foodId,
        foodName = foodName,
        amount = amount,
        portionLabel = portionLabel,
        baseUnit = foods?.baseUnit,
        kcal = kcal,
        proteinG = proteinG,
        carbsG = carbsG,
        fatG = fatG,
    )

    companion object {
        const val COLUMNS =
            "id,log_date,meal_slot_id,food_id,food_name,amount,portion_label,kcal,protein_g,carbs_g,fat_g," +
                "foods(base_unit)"
    }
}
