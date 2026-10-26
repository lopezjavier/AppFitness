package io.github.lopezjavier.appfitness.domain.model

/**
 * The weight (g) or volume (ml) of the portion shown in a photo, for a food already chosen (US-54, ADR-72).
 * [confidence] is an integer 1 (pure guess) to 10 (near certain); null when the server sent none.
 */
data class AmountEstimate(val amount: Double, val baseUnit: String, val confidence: Int?)
