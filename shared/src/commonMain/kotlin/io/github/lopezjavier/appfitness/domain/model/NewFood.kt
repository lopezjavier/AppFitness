package io.github.lopezjavier.appfitness.domain.model

/** A named amount of a new food, in base units. */
data class NewPortion(val label: String, val amount: Double)

/** A personal food to create: [baseUnit] is "g" or "ml", [nutrition] is per 100 base units. */
data class NewFood(
    val name: String,
    val brand: String?,
    val baseUnit: String,
    val nutrition: NutritionPer100,
    val barcode: String?,
    val portions: List<NewPortion>,
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
) {
    val isValid: Boolean
        get() = name == null && brand == null && kcal == null && protein == null && carbs == null && fat == null &&
            portions.isEmpty() && !tooManyPortions
}

enum class NewFoodError { Required, TooLong, Negative, TooLarge, NotANumber }
