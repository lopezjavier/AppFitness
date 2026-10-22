package io.github.lopezjavier.appfitness.domain.usecase

import kotlin.math.floor

/** Rounds half up to 1 decimal (planning/04-business-rules.md rounds weights and ratios to tenths). */
internal fun roundToTenths(value: Double): Double = floor(value * TENTHS + HALF) / TENTHS

private const val TENTHS = 10.0
private const val HALF = 0.5

/** The BMI bands of planning/04-business-rules.md §3.8. */
enum class BmiStatus { Underweight, Normal, Overweight, Obese }

/** Body mass index (§3.6), derived and never stored. */
class Bmi {
    /** `weight / (height / 100)^2` with 1 decimal, or null when the profile has no height. */
    fun compute(weightKg: Double, heightCm: Int?): Double? {
        if (heightCm == null || heightCm <= 0) return null
        val meters = heightCm / CM_PER_METER
        return roundToTenths(weightKg / (meters * meters))
    }

    /** The band of [bmi], compared on its 1-decimal value. */
    fun status(bmi: Double): BmiStatus {
        val value = roundToTenths(bmi)
        return when {
            value < NORMAL_FROM -> BmiStatus.Underweight
            value < OVERWEIGHT_FROM -> BmiStatus.Normal
            value < OBESE_FROM -> BmiStatus.Overweight
            else -> BmiStatus.Obese
        }
    }

    private companion object {
        const val CM_PER_METER = 100.0
        const val NORMAL_FROM = 18.5
        const val OVERWEIGHT_FROM = 25.0
        const val OBESE_FROM = 30.0
    }
}
