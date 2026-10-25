package io.github.lopezjavier.appfitness.domain.model

/** The weight (g) or volume (ml) of the portion shown in a photo, for a food already chosen (US-54). */
data class AmountEstimate(val amount: Double, val baseUnit: String, val confidence: FoodPhotoConfidence?)
