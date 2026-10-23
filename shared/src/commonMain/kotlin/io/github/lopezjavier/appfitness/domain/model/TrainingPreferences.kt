package io.github.lopezjavier.appfitness.domain.model

/**
 * Per-user training settings. [warmUpIncluded]: warm-up sets count in volume, 1RM, records and set totals.
 * [previousValuesMode]: where the live session takes the previous values from.
 */
data class TrainingPreferences(
    val warmUpIncluded: Boolean = false,
    val previousValuesMode: PreviousValuesMode = PreviousValuesMode.Overall,
)
