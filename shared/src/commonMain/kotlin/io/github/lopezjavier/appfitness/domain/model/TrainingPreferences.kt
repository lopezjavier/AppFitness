package io.github.lopezjavier.appfitness.domain.model

/** Per-user training settings. [warmUpIncluded]: warm-up sets count in volume, 1RM, records and set totals. */
data class TrainingPreferences(val warmUpIncluded: Boolean = false)
