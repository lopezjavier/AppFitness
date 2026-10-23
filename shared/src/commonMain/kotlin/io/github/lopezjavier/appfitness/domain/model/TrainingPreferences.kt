package io.github.lopezjavier.appfitness.domain.model

/**
 * Per-user training settings. [warmUpIncluded]: warm-up sets count in volume, 1RM, records and set totals.
 * [previousValuesMode]: where the live session takes the previous values from. The remaining fields are the
 * calculator settings (ADR-52, `04` §4.9): default bar, available plates per side and the plate/dumbbell rounding.
 */
data class TrainingPreferences(
    val warmUpIncluded: Boolean = false,
    val previousValuesMode: PreviousValuesMode = PreviousValuesMode.Overall,
    val barWeightKg: Double = CalculatorSettings.DEFAULT_BAR_KG,
    val plateKg: List<Double> = CalculatorSettings.PLATE_CATALOGUE,
    val plateRoundingKg: Double = CalculatorSettings.DEFAULT_PLATE_ROUNDING_KG,
    val dumbbellRoundingKg: Double = CalculatorSettings.DEFAULT_DUMBBELL_ROUNDING_KG,
) {
    val calculators: CalculatorSettings
        get() = CalculatorSettings(barWeightKg, plateKg, plateRoundingKg, dumbbellRoundingKg)
}

/** The calculator settings stored in `training_preferences` and saved with `save_preferences`. */
data class CalculatorSettings(
    val barWeightKg: Double = DEFAULT_BAR_KG,
    val plateKg: List<Double> = PLATE_CATALOGUE,
    val plateRoundingKg: Double = DEFAULT_PLATE_ROUNDING_KG,
    val dumbbellRoundingKg: Double = DEFAULT_DUMBBELL_ROUNDING_KG,
) {
    companion object {
        const val DEFAULT_BAR_KG = 20.0
        const val DEFAULT_PLATE_ROUNDING_KG = 2.5
        const val DEFAULT_DUMBBELL_ROUNDING_KG = 2.0

        /** The bars offered, heaviest first. */
        val BARS_KG: List<Double> = listOf(20.0, 15.0, 10.0, 7.5)

        /** Every plate weight (per side) the app knows, heaviest first. */
        val PLATE_CATALOGUE: List<Double> = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 2.0, 1.25, 1.0, 0.5)
    }
}
