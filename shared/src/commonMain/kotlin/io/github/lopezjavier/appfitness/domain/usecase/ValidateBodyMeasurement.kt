package io.github.lopezjavier.appfitness.domain.usecase

import kotlin.math.abs
import kotlin.math.round

/** The field of a manual measurement a [BodyMeasurementCheck.Invalid] refers to. */
enum class BodyMeasurementField { Weight, BodyFat }

/** Why a value is rejected. */
enum class BodyMeasurementProblem { NotANumber, TooLow, TooHigh, TooManyDecimals }

/** Outcome of [ValidateBodyMeasurement]. */
sealed interface BodyMeasurementCheck {
    data object Valid : BodyMeasurementCheck

    /** [field] breaks the rule [problem]; the allowed range is in [ValidateBodyMeasurement]'s companion. */
    data class Invalid(val field: BodyMeasurementField, val problem: BodyMeasurementProblem) : BodyMeasurementCheck
}

/**
 * A manual weight is valid in 20..400 kg and the optional body fat in 2..75 %, each with at most one decimal.
 * The weight is checked first.
 */
class ValidateBodyMeasurement {
    operator fun invoke(weightKg: Double, bodyFatPct: Double?): BodyMeasurementCheck {
        val weight = check(weightKg, WEIGHT_MIN, WEIGHT_MAX)
        val fat = bodyFatPct?.let { check(it, FAT_MIN, FAT_MAX) }
        return when {
            weight != null -> BodyMeasurementCheck.Invalid(BodyMeasurementField.Weight, weight)
            fat != null -> BodyMeasurementCheck.Invalid(BodyMeasurementField.BodyFat, fat)
            else -> BodyMeasurementCheck.Valid
        }
    }

    private fun check(value: Double, min: Double, max: Double): BodyMeasurementProblem? = when {
        value.isNaN() || value.isInfinite() -> BodyMeasurementProblem.NotANumber
        value < min -> BodyMeasurementProblem.TooLow
        value > max -> BodyMeasurementProblem.TooHigh
        abs(value * TENTHS - round(value * TENTHS)) > TOLERANCE -> BodyMeasurementProblem.TooManyDecimals
        else -> null
    }

    companion object {
        const val WEIGHT_MIN = 20.0
        const val WEIGHT_MAX = 400.0
        const val FAT_MIN = 2.0
        const val FAT_MAX = 75.0
        private const val TENTHS = 10.0
        private const val TOLERANCE = 1e-6
    }
}
