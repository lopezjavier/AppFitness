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

/**
 * The new values of an existing personal food; its barcode and owner never change. [baseUnit] ("g" or "ml") only
 * changes while no diary entry or comida references the food; otherwise the server refuses with `AppError.FoodInUse`.
 */
data class UpdatedFood(
    val id: String,
    val name: String,
    val brand: String?,
    val nutrition: NutritionPer100,
    val portions: List<NewPortion>,
    val micronutrients: NewMicronutrients = NewMicronutrients(),
    val baseUnit: String,
)

/**
 * A personal food as stored: [micronutrients] are grams per 100 base units exactly as saved (no display rounding).
 * [inUse] is true while a diary entry or a comida references it (its unit is then locked); favourites do not count.
 */
data class EditableFood(val food: Food, val micronutrients: Map<MicronutrientKind, Double>, val inUse: Boolean)

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
