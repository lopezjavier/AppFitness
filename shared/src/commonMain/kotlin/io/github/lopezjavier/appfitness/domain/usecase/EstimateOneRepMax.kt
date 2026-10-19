package io.github.lopezjavier.appfitness.domain.usecase

import kotlin.math.floor

/** Epley estimated 1RM rounded to 0.5 kg; null when reps are outside 1..12 or the weight is not positive. */
object EstimateOneRepMax {
    const val MIN_REPS = 1
    const val MAX_REPS = 12
    private const val EPLEY_DIVISOR = 30.0
    private const val STEPS_PER_KG = 2.0
    private const val HALF = 0.5

    operator fun invoke(weightKg: Double, reps: Int): Double? {
        if (reps !in MIN_REPS..MAX_REPS || weightKg <= 0.0) return null
        val raw = if (reps == 1) weightKg else weightKg * (1 + reps / EPLEY_DIVISOR)
        return floor(raw * STEPS_PER_KG + HALF) / STEPS_PER_KG
    }
}
