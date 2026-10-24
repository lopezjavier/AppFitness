package io.github.lopezjavier.appfitness.domain.model

/** Which nutrient a [LabelNutrient] row is: one of the four columns of a food, or an extra [MicronutrientKind]. */
sealed interface LabelNutrientKey {
    data object Energy : LabelNutrientKey

    data object Fat : LabelNutrientKey

    data object Carbs : LabelNutrientKey

    data object Protein : LabelNutrientKey

    data class Extra(val kind: MicronutrientKind) : LabelNutrientKey
}

/**
 * One row of the nutrient list of a food, in label order. [unit] is null for energy (kcal). [inUsualLabel] marks the
 * rows of the usual label (energy, fat, saturates, carbohydrates, sugars, fibre, protein, salt).
 */
data class LabelNutrient(val key: LabelNutrientKey, val amount: Double, val unit: MicronutrientUnit?) {
    val inUsualLabel: Boolean
        get() = when (key) {
            is LabelNutrientKey.Extra -> key.kind in USUAL_EXTRAS
            else -> true
        }

    private companion object {
        val USUAL_EXTRAS = setOf(
            MicronutrientKind.SaturatedFat,
            MicronutrientKind.Sugars,
            MicronutrientKind.Fiber,
            MicronutrientKind.Salt,
        )
    }
}
