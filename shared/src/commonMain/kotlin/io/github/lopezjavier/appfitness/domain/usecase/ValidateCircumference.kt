package io.github.lopezjavier.appfitness.domain.usecase

import kotlin.math.abs
import kotlin.math.round

/** Why a circumference is rejected. */
enum class CircumferenceProblem { NotANumber, TooLow, TooHigh, TooManyDecimals }

/** Outcome of [ValidateCircumference]. */
sealed interface CircumferenceCheck {
    data object Valid : CircumferenceCheck

    data class Invalid(val problem: CircumferenceProblem) : CircumferenceCheck
}

/** A circumference is valid in 20.0..250.0 cm with at most one decimal. */
class ValidateCircumference {
    operator fun invoke(valueCm: Double): CircumferenceCheck {
        val problem = when {
            valueCm.isNaN() || valueCm.isInfinite() -> CircumferenceProblem.NotANumber
            valueCm < MIN_CM -> CircumferenceProblem.TooLow
            valueCm > MAX_CM -> CircumferenceProblem.TooHigh
            abs(valueCm * TENTHS - round(valueCm * TENTHS)) > TOLERANCE -> CircumferenceProblem.TooManyDecimals
            else -> return CircumferenceCheck.Valid
        }
        return CircumferenceCheck.Invalid(problem)
    }

    companion object {
        const val MIN_CM = 20.0
        const val MAX_CM = 250.0
        private const val TENTHS = 10.0
        private const val TOLERANCE = 1e-6
    }
}
