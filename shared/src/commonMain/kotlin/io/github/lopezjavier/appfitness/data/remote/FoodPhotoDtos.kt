package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.AmountEstimate
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.FoodPhotoConfidence
import io.github.lopezjavier.appfitness.domain.model.FoodPhotoDraft
import io.github.lopezjavier.appfitness.domain.model.FoodPhotoMode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Body of `POST food-photo`; [comment] is omitted when null (the client does not encode defaults). */
@Serializable
internal data class FoodPhotoRequestDto(
    @SerialName("image_base64") val imageBase64: String,
    val mode: String,
    val comment: String? = null,
) {
    companion object {
        fun from(mode: FoodPhotoMode, imageBase64: String, comment: String?) = FoodPhotoRequestDto(
            imageBase64 = imageBase64,
            mode = mode.wireName,
            // The server only accepts a note for a dish.
            comment = comment?.trim()?.takeIf { mode == FoodPhotoMode.Dish && it.isNotEmpty() },
        )

        private val FoodPhotoMode.wireName: String
            get() = when (this) {
                FoodPhotoMode.Dish -> "dish"
                FoodPhotoMode.Label -> "label"
                FoodPhotoMode.Barcode -> "barcode"
            }
    }
}

/** The food already chosen, sent as data for a weight estimate; nutrition is per 100 base units. */
@Serializable
internal data class FoodContextDto(
    val name: String,
    val brand: String? = null,
    @SerialName("base_unit") val baseUnit: String,
    val kcal: Double,
    @SerialName("protein_g") val proteinG: Double,
    @SerialName("carbs_g") val carbsG: Double,
    @SerialName("fat_g") val fatG: Double,
) {
    companion object {
        fun from(food: Food) = FoodContextDto(
            name = food.name,
            brand = food.brand?.takeIf { it.isNotBlank() },
            baseUnit = if (food.baseUnit == "ml") "ml" else "g",
            kcal = food.nutrition.kcal,
            proteinG = food.nutrition.proteinG,
            carbsG = food.nutrition.carbsG,
            fatG = food.nutrition.fatG,
        )
    }
}

/** Body of `POST food-photo` in `weight` mode. */
@Serializable
internal data class AmountRequestDto(
    @SerialName("image_base64") val imageBase64: String,
    val mode: String,
    val food: FoodContextDto,
) {
    companion object {
        fun from(food: Food, imageBase64: String) = AmountRequestDto(imageBase64, "weight", FoodContextDto.from(food))
    }
}

@Serializable
internal data class AmountEstimateDto(
    @SerialName("estimated_amount") val estimatedAmount: Double,
    @SerialName("base_unit") val baseUnit: String = "g",
    val confidence: String? = null,
) {
    fun toDomain() = AmountEstimate(
        amount = estimatedAmount,
        baseUnit = if (baseUnit == "ml") "ml" else "g",
        confidence = confidence.toConfidence(),
    )
}

@Serializable
internal data class AmountResponseDto(val estimate: AmountEstimateDto)

private fun String?.toConfidence() = when (this) {
    "low" -> FoodPhotoConfidence.Low
    "medium" -> FoodPhotoConfidence.Medium
    "high" -> FoodPhotoConfidence.High
    else -> null
}

@Serializable
internal data class FoodPhotoResponseDto(val draft: FoodPhotoDraftDto)

@Serializable
internal data class FoodPhotoNutritionDto(
    val kcal: Double? = null,
    @SerialName("protein_g") val proteinG: Double? = null,
    @SerialName("carbs_g") val carbsG: Double? = null,
    @SerialName("fat_g") val fatG: Double? = null,
)

@Serializable
internal data class FoodPhotoDraftDto(
    val name: String,
    val brand: String? = null,
    @SerialName("base_unit") val baseUnit: String = "g",
    val nutrition: FoodPhotoNutritionDto = FoodPhotoNutritionDto(),
    val micronutrients: Map<String, Double> = emptyMap(),
    @SerialName("estimated_grams") val estimatedGrams: Double? = null,
    val confidence: String? = null,
) {
    fun toDomain() = FoodPhotoDraft(
        name = name,
        brand = brand,
        baseUnit = if (baseUnit == "ml") "ml" else "g",
        kcal = nutrition.kcal,
        protein = nutrition.proteinG,
        carbs = nutrition.carbsG,
        fat = nutrition.fatG,
        micronutrients = micronutrients,
        estimatedGrams = estimatedGrams,
        confidence = confidence.toConfidence(),
    )
}
