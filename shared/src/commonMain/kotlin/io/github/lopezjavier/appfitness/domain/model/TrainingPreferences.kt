package io.github.lopezjavier.appfitness.domain.model

/**
 * Per-user training settings. [warmUpIncluded]: warm-up sets count in volume, 1RM, records and set totals.
 * [previousValuesMode]: where the live session takes the previous values from. The remaining fields are the
 * calculator settings (ADR-52, `04` §4.9): default bar, available plates per side and the dumbbell handle; the
 * rounding is derived (ADR-56).
 */
data class TrainingPreferences(
    val warmUpIncluded: Boolean = false,
    val previousValuesMode: PreviousValuesMode = PreviousValuesMode.Overall,
    val barWeightKg: Double = CalculatorSettings.DEFAULT_BAR_KG,
    val plateKg: List<Double> = CalculatorSettings.PLATE_CATALOGUE,
    val dumbbellHandleKg: Double = CalculatorSettings.DEFAULT_DUMBBELL_HANDLE_KG,
    /** A finished routine session updates its routine without asking (ADR-70, one global setting). */
    val autoUpdateRoutine: Boolean = false,
) {
    val calculators: CalculatorSettings
        get() = CalculatorSettings(barWeightKg, plateKg, dumbbellHandleKg)
}

/** The calculator settings stored in `training_preferences` and saved with `save_preferences`. */
data class CalculatorSettings(
    val barWeightKg: Double = DEFAULT_BAR_KG,
    val plateKg: List<Double> = PLATE_CATALOGUE,
    val dumbbellHandleKg: Double = DEFAULT_DUMBBELL_HANDLE_KG,
) {
    /** Warm-up rounding (ADR-56): twice the smallest enabled plate (smallest of the catalogue if none). */
    val roundingKg: Double
        get() = ROUNDING_FACTOR * (plateKg.minOrNull() ?: PLATE_CATALOGUE.min())

    companion object {
        private const val ROUNDING_FACTOR = 2.0
        const val DEFAULT_BAR_KG = 20.0
        const val DEFAULT_DUMBBELL_HANDLE_KG = 1.0

        /** The bars offered, heaviest first. */
        val BARS_KG: List<Double> = listOf(20.0, 15.0, 10.0, 7.5)

        /** Every plate weight (per side) the app knows, heaviest first. */
        val PLATE_CATALOGUE: List<Double> = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 2.0, 1.25, 1.0, 0.5)
    }
}
