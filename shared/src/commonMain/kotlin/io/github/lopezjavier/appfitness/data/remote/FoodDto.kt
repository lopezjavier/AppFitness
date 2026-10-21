package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.DiaryDaySummary
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.FoodPortion
import io.github.lopezjavier.appfitness.domain.model.MealTotals
import io.github.lopezjavier.appfitness.domain.model.NewFood
import io.github.lopezjavier.appfitness.domain.model.NewFoodEntry
import io.github.lopezjavier.appfitness.domain.model.NewMicronutrients
import io.github.lopezjavier.appfitness.domain.model.NutritionPer100
import io.github.lopezjavier.appfitness.domain.model.UpdatedFoodEntry
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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

/** Body of a 200 from `food-barcode`. */
@Serializable
internal data class FoodBarcodeResponseDto(val food: FoodDto)

@Serializable
internal data class CreateFoodPortionParam(val label: String, val amount: Double)

/** Arguments of the `create_personal_food` function; the food and its portions are stored in one transaction. */
@Serializable
internal data class CreateFoodParams(
    @SerialName("p_name") val name: String,
    @SerialName("p_brand") val brand: String?,
    @SerialName("p_base_unit") val baseUnit: String,
    @SerialName("p_kcal") val kcal: Double,
    @SerialName("p_protein") val protein: Double,
    @SerialName("p_carbs") val carbs: Double,
    @SerialName("p_fat") val fat: Double,
    @SerialName("p_barcode") val barcode: String?,
    @SerialName("p_portions") val portions: List<CreateFoodPortionParam>,
    @SerialName("p_micronutrients") val micronutrients: Map<String, Double>? = null,
) {
    companion object {
        fun from(food: NewFood) = CreateFoodParams(
            name = food.name.trim(),
            brand = food.brand?.trim()?.takeIf { it.isNotEmpty() },
            baseUnit = food.baseUnit,
            kcal = food.nutrition.kcal,
            protein = food.nutrition.proteinG,
            carbs = food.nutrition.carbsG,
            fat = food.nutrition.fatG,
            barcode = food.barcode?.trim()?.takeIf { it.isNotEmpty() },
            portions = food.portions.map { CreateFoodPortionParam(it.label.trim(), it.amount) },
            micronutrients = food.micronutrients.toParam(),
        )

        /** Only the typed values, keyed like the `foods.micronutrients` column; null when none was typed. */
        private fun NewMicronutrients.toParam(): Map<String, Double>? = buildMap {
            saturatedFat?.let { put("saturated-fat_100g", it) }
            sugars?.let { put("sugars_100g", it) }
            fiber?.let { put("fiber_100g", it) }
            salt?.let { put("salt_100g", it) }
        }.ifEmpty { null }
    }
}

/**
 * The nutrient columns of one `foods` row read for the details: `micronutrients` (personal foods) and the
 * `nutriments` object of `off_data` (OFF foods), never the whole OFF blob.
 */
@Serializable
internal data class FoodMicronutrientsDto(
    val source: String,
    val micronutrients: JsonElement? = null,
    val nutriments: JsonElement? = null,
) {
    /** The typed values of a personal food; numbers sent as strings are tolerated. */
    fun typedValues(): JsonObject? = micronutrients.asObject()

    /** The OFF `nutriments`; an object, or an object serialised as a JSON string, else none. */
    fun offNutriments(): JsonObject? = nutriments.asObject()

    private fun JsonElement?.asObject(): JsonObject? = when (this) {
        is JsonObject -> this

        is JsonPrimitive -> if (isString) {
            runCatching { Json.parseToJsonElement(content) as? JsonObject }.getOrNull()
        } else {
            null
        }

        else -> null
    }

    companion object {
        const val COLUMNS = "source,micronutrients,nutriments:off_data->nutriments"
    }
}

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

/** Row of `food_diary_summary`: the totals of one day with entries. */
@Serializable
internal data class DiaryDaySummaryDto(
    @SerialName("log_date") val logDate: String,
    val kcal: Double,
    @SerialName("protein_g") val proteinG: Double,
    @SerialName("carbs_g") val carbsG: Double,
    @SerialName("fat_g") val fatG: Double,
) {
    fun toDomain() = DiaryDaySummary(LocalDate.parse(logDate), MealTotals(kcal, proteinG, carbsG, fatG))
}

@Serializable
internal data class DiaryDaySummaryParams(@SerialName("p_from") val from: String, @SerialName("p_to") val to: String)

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

/** Patch body of `food_log_entries` for an edited entry: only what an edit can change. */
@Serializable
internal data class FoodLogEntryUpdateDto(
    @SerialName("meal_slot_id") val mealSlotId: String,
    val amount: Double,
    @SerialName("portion_label") val portionLabel: String?,
    val kcal: Double,
    @SerialName("protein_g") val proteinG: Double,
    @SerialName("carbs_g") val carbsG: Double,
    @SerialName("fat_g") val fatG: Double,
) {
    companion object {
        fun from(update: UpdatedFoodEntry) = FoodLogEntryUpdateDto(
            mealSlotId = update.mealSlotId,
            amount = update.amount,
            portionLabel = update.portionLabel,
            kcal = update.snapshot.kcal,
            proteinG = update.snapshot.proteinG,
            carbsG = update.snapshot.carbsG,
            fatG = update.snapshot.fatG,
        )
    }
}
