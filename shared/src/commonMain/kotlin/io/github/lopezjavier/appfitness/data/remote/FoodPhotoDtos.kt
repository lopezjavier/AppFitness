package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.AmountEstimate
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.FoodPhotoConfidence
import io.github.lopezjavier.appfitness.domain.model.FoodPhotoDraft
import io.github.lopezjavier.appfitness.domain.model.FoodPhotoMode
import io.github.lopezjavier.appfitness.domain.model.PreviousEstimate
import io.github.lopezjavier.appfitness.domain.usecase.EnergyCheckLevel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.roundToInt

/** Body of `POST food-photo`; [comment] is omitted when null (the client does not encode defaults). */
@Serializable
internal data class FoodPhotoRequestDto(
    @SerialName("image_base64") val imageBase64: String,
    val mode: String,
    val comment: String? = null,
    val previous: PreviousDto? = null,
) {
    companion object {
        fun from(mode: FoodPhotoMode, imageBase64: String, comment: String?, previous: PreviousEstimate? = null) =
            FoodPhotoRequestDto(
                imageBase64 = imageBase64,
                mode = mode.wireName,
                // The server only accepts a note for a dish.
                comment = comment?.trim()?.takeIf { mode == FoodPhotoMode.Dish && it.isNotEmpty() },
                // A retry only exists for a dish or a label.
                previous = previous?.takeIf { mode == FoodPhotoMode.Dish || mode == FoodPhotoMode.Label }
                    ?.let(PreviousDto::from),
            )
    }
}

private val FoodPhotoMode.wireName: String
    get() = when (this) {
        FoodPhotoMode.Dish -> "dish"
        FoodPhotoMode.Label -> "label"
        FoodPhotoMode.Barcode -> "barcode"
        FoodPhotoMode.Text -> "text"
    }

/** Body of `POST food-photo` in `text` mode: the sentence travels as [comment]; there is no image. */
@Serializable
internal data class MealTextRequestDto(val mode: String, val comment: String, val previous: PreviousDto? = null) {
    companion object {
        fun from(text: String, previous: PreviousEstimate? = null) = MealTextRequestDto(
            mode = FoodPhotoMode.Text.wireName,
            comment = text.trim(),
            previous = previous?.let(PreviousDto::from),
        )
    }
}

/** The failed estimate of a retry, as `food-photo` validates it (`previous.ts`); micronutrients in grams per 100. */
@Serializable
internal data class PreviousDto(val estimate: PreviousEstimateDto, val reason: PreviousReasonDto) {
    companion object {
        fun from(previous: PreviousEstimate) = PreviousDto(
            estimate = PreviousEstimateDto(
                name = previous.name,
                baseUnit = previous.baseUnit,
                nutrition = PreviousNutritionDto(
                    kcal = previous.kcal,
                    proteinG = previous.protein,
                    carbsG = previous.carbs,
                    fatG = previous.fat,
                    alcoholG = previous.alcohol ?: 0.0,
                ),
                micronutrients = PreviousMicronutrientsDto(previous.fibre, previous.polyols),
            ),
            reason = PreviousReasonDto(
                result = if (previous.reason.result == EnergyCheckLevel.ClearlyWrong) "clearly_wrong" else "mismatch",
                kcal = previous.reason.kcal,
                computed = previous.reason.computed,
                gap = previous.reason.gap,
            ),
        )
    }
}

@Serializable
internal data class PreviousEstimateDto(
    val name: String,
    @SerialName("base_unit") val baseUnit: String,
    val nutrition: PreviousNutritionDto,
    val micronutrients: PreviousMicronutrientsDto,
)

@Serializable
internal data class PreviousNutritionDto(
    val kcal: Double,
    @SerialName("protein_g") val proteinG: Double?,
    @SerialName("carbs_g") val carbsG: Double?,
    @SerialName("fat_g") val fatG: Double?,
    @SerialName("alcohol_g") val alcoholG: Double,
)

@Serializable
internal data class PreviousMicronutrientsDto(
    @SerialName("fiber_100g") val fiber100g: Double? = null,
    @SerialName("polyols_100g") val polyols100g: Double? = null,
)

@Serializable
internal data class PreviousReasonDto(val result: String, val kcal: Double, val computed: Double, val gap: Double)

/** The food already chosen, sent as data for a weight estimate (ADR-72: name, brand and base unit only). */
@Serializable
internal data class FoodContextDto(
    val name: String,
    val brand: String? = null,
    @SerialName("base_unit") val baseUnit: String,
) {
    companion object {
        fun from(food: Food) = FoodContextDto(
            name = food.name,
            brand = food.brand?.takeIf { it.isNotBlank() },
            baseUnit = if (food.baseUnit == "ml") "ml" else "g",
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
    // Decoded leniently: an old string answer ("high") or any non-number must not fail the whole estimate.
    val confidence: JsonElement? = null,
) {
    fun toDomain() = AmountEstimate(
        amount = estimatedAmount,
        baseUnit = if (baseUnit == "ml") "ml" else "g",
        confidence = confidence.toScore(),
    )
}

private const val MIN_SCORE = 1
private const val MAX_SCORE = 10

private fun JsonElement?.toScore(): Int? {
    val number = (this as? JsonPrimitive)?.takeUnless { it is JsonNull || it.isString }
    return number?.doubleOrNull?.roundToInt()?.takeIf { it in MIN_SCORE..MAX_SCORE }
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
    @SerialName("alcohol_g") val alcoholG: Double? = null,
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
        alcohol = nutrition.alcoholG,
        estimatedGrams = estimatedGrams,
        confidence = confidence.toConfidence(),
    )
}
