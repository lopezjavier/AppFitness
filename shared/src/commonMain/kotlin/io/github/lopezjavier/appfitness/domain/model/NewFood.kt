package io.github.lopezjavier.appfitness.domain.model

/** A named amount of a new food, in base units. */
data class NewPortion(val label: String, val amount: Double)

/**
 * The optional label values of a food: each typed in the nutrient's [MicronutrientKind.labelUnit]. A nutrient that
 * was not typed is not in the map.
 */
data class NewMicronutrients(val values: Map<MicronutrientKind, Double> = emptyMap())

/** A personal food to create: [baseUnit] is "g" or "ml", [nutrition] is per 100 base units. */
data class NewFood(
    val name: String,
    val brand: String?,
    val baseUnit: String,
    val nutrition: NutritionPer100,
    val barcode: String?,
    val portions: List<NewPortion>,
    val micronutrients: NewMicronutrients = NewMicronutrients(),
)

/** The new values of an existing personal food; its base unit, barcode and owner never change. */
data class UpdatedFood(
    val id: String,
    val name: String,
    val brand: String?,
    val nutrition: NutritionPer100,
    val portions: List<NewPortion>,
    val micronutrients: NewMicronutrients = NewMicronutrients(),
)

/** A personal food as stored: [micronutrients] are grams per 100 base units exactly as saved (no display rounding). */
data class EditableFood(val food: Food, val micronutrients: Map<MicronutrientKind, Double>)

/** Why a portion is invalid. */
enum class PortionError { LabelInvalid, AmountInvalid }

/**
 * Per-field problems of a food form; null means the field is valid. [portions] is indexed like the portion list and
 * [nutrients] only holds the invalid label nutrients.
 */
data class NewFoodErrors(
    val name: NewFoodError? = null,
    val brand: NewFoodError? = null,
    val kcal: NewFoodError? = null,
    val protein: NewFoodError? = null,
    val carbs: NewFoodError? = null,
    val fat: NewFoodError? = null,
    val portions: Map<Int, PortionError> = emptyMap(),
    val tooManyPortions: Boolean = false,
    val nutrients: Map<MicronutrientKind, NewFoodError> = emptyMap(),
) {
    val isValid: Boolean
        get() = name == null && brand == null && kcal == null && protein == null && carbs == null && fat == null &&
            portions.isEmpty() && !tooManyPortions && nutrients.isEmpty()
}

enum class NewFoodError { Required, TooLong, Negative, TooLarge, NotANumber }
