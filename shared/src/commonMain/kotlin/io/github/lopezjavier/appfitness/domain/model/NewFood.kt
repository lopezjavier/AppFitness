package io.github.lopezjavier.appfitness.domain.model

/** A named amount of a new food, in base units. */
data class NewPortion(val label: String, val amount: Double)

/** The optional label values of a new food, in grams per 100 base units; null means not typed. */
data class NewMicronutrients(
    val saturatedFat: Double? = null,
    val sugars: Double? = null,
    val fiber: Double? = null,
    val salt: Double? = null,
)

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

/** Why a portion is invalid. */
enum class PortionError { LabelInvalid, AmountInvalid }

/** Per-field problems of a [NewFood]; null means the field is valid. [portions] is indexed like the portion list. */
data class NewFoodErrors(
    val name: NewFoodError? = null,
    val brand: NewFoodError? = null,
    val kcal: NewFoodError? = null,
    val protein: NewFoodError? = null,
    val carbs: NewFoodError? = null,
    val fat: NewFoodError? = null,
    val portions: Map<Int, PortionError> = emptyMap(),
    val tooManyPortions: Boolean = false,
    val saturatedFat: NewFoodError? = null,
    val sugars: NewFoodError? = null,
    val fiber: NewFoodError? = null,
    val salt: NewFoodError? = null,
) {
    val isValid: Boolean
        get() = name == null && brand == null && kcal == null && protein == null && carbs == null && fat == null &&
            portions.isEmpty() && !tooManyPortions && saturatedFat == null && sugars == null && fiber == null &&
            salt == null
}

enum class NewFoodError { Required, TooLong, Negative, TooLarge, NotANumber }
