package io.github.lopezjavier.appfitness.domain.usecase

import kotlin.math.abs
import kotlin.math.round

/** Outcome of [ValidateQuantity]. */
sealed interface QuantityCheck {
    data object Valid : QuantityCheck

    /** Zero or negative. */
    data object TooLow : QuantityCheck

    /** Above [ValidateQuantity.MAX]. */
    data object TooHigh : QuantityCheck

    /** More than one decimal. */
    data object TooManyDecimals : QuantityCheck
}

/** An amount is valid when it is in (0, 5000] with at most one decimal. */
class ValidateQuantity {
    operator fun invoke(amount: Double): QuantityCheck = when {
        amount.isNaN() || amount <= 0.0 -> QuantityCheck.TooLow
        amount > MAX -> QuantityCheck.TooHigh
        abs(amount * TENTHS - round(amount * TENTHS)) > TOLERANCE -> QuantityCheck.TooManyDecimals
        else -> QuantityCheck.Valid
    }

    companion object {
        const val MAX = 5000.0
        private const val TENTHS = 10.0
        private const val TOLERANCE = 1e-6
    }
}
