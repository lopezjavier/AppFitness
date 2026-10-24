package io.github.lopezjavier.appfitness.domain.model

private const val MAX_NAME = 200
private const val MAX_LABEL = 100
private const val MAX_NUTRIENT = 9999.99
private const val MAX_PORTION_AMOUNT = 9999.9
private const val MAX_PORTIONS = 10

/** Pure validation of a [NewFood] / [UpdatedFood] against the limits of the personal-food functions. */
object NewFoodValidation {
    fun validate(food: NewFood): NewFoodErrors =
        validate(food.name, food.brand, food.nutrition, food.portions, food.micronutrients)

    fun validate(food: UpdatedFood): NewFoodErrors =
        validate(food.name, food.brand, food.nutrition, food.portions, food.micronutrients)

    private fun validate(
        name: String,
        brand: String?,
        nutrition: NutritionPer100,
        portions: List<NewPortion>,
        micronutrients: NewMicronutrients,
    ): NewFoodErrors = NewFoodErrors(
        name = text(name, required = true),
        brand = brand?.let { text(it, required = false) },
        kcal = nutrient(nutrition.kcal),
        protein = nutrient(nutrition.proteinG),
        carbs = nutrient(nutrition.carbsG),
        fat = nutrient(nutrition.fatG),
        portions = portions.withIndex().mapNotNull { (i, p) -> portion(p)?.let { i to it } }.toMap(),
        tooManyPortions = portions.size > MAX_PORTIONS,
        nutrients = micronutrients.values.mapNotNull { (kind, typed) ->
            nutrient(typed, kind.labelUnit)?.let { kind to it }
        }.toMap(),
    )

    private fun text(value: String, required: Boolean): NewFoodError? = when {
        required && value.isBlank() -> NewFoodError.Required
        value.trim().length > MAX_NAME -> NewFoodError.TooLong
        else -> null
    }

    /** [value] is typed in [unit]; the limit applies to the grams it stands for. */
    private fun nutrient(value: Double, unit: MicronutrientUnit = MicronutrientUnit.G): NewFoodError? = when {
        !value.isFinite() -> NewFoodError.NotANumber
        value < 0.0 -> NewFoodError.Negative
        unit.toGrams(value) > MAX_NUTRIENT -> NewFoodError.TooLarge
        else -> null
    }

    private fun portion(portion: NewPortion): PortionError? = when {
        portion.label.isBlank() || portion.label.trim().length > MAX_LABEL -> PortionError.LabelInvalid

        !portion.amount.isFinite() || portion.amount <= 0.0 || portion.amount > MAX_PORTION_AMOUNT ->
            PortionError.AmountInvalid

        else -> null
    }
}
