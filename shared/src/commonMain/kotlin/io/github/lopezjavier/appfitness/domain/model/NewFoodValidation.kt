package io.github.lopezjavier.appfitness.domain.model

private const val MAX_NAME = 200
private const val MAX_LABEL = 100
private const val MAX_NUTRIENT = 9999.99
private const val MAX_PORTION_AMOUNT = 9999.9
private const val MAX_PORTIONS = 10

/** Pure validation of a [NewFood] against the limits of the `create_personal_food` function. */
object NewFoodValidation {
    fun validate(food: NewFood): NewFoodErrors = NewFoodErrors(
        name = text(food.name, required = true),
        brand = food.brand?.let { text(it, required = false) },
        kcal = nutrient(food.nutrition.kcal),
        protein = nutrient(food.nutrition.proteinG),
        carbs = nutrient(food.nutrition.carbsG),
        fat = nutrient(food.nutrition.fatG),
        portions = food.portions.withIndex().mapNotNull { (i, p) -> portion(p)?.let { i to it } }.toMap(),
        tooManyPortions = food.portions.size > MAX_PORTIONS,
    )

    private fun text(value: String, required: Boolean): NewFoodError? = when {
        required && value.isBlank() -> NewFoodError.Required
        value.trim().length > MAX_NAME -> NewFoodError.TooLong
        else -> null
    }

    private fun nutrient(value: Double): NewFoodError? = when {
        !value.isFinite() -> NewFoodError.NotANumber
        value < 0.0 -> NewFoodError.Negative
        value > MAX_NUTRIENT -> NewFoodError.TooLarge
        else -> null
    }

    private fun portion(portion: NewPortion): PortionError? = when {
        portion.label.isBlank() || portion.label.trim().length > MAX_LABEL -> PortionError.LabelInvalid

        !portion.amount.isFinite() || portion.amount <= 0.0 || portion.amount > MAX_PORTION_AMOUNT ->
            PortionError.AmountInvalid

        else -> null
    }
}
