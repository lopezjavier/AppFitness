package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.FoodPortion
import kotlin.math.floor

private const val TEN = 10.0
private const val HALF = 0.5
private const val EPSILON = 1e-9

/** Rounds to one decimal, ties up (half up) for positives, tolerating binary float noise. */
internal fun roundToTenth(value: Double): Double = floor(value * TEN + HALF + EPSILON) / TEN

/** Turns what the user typed into an amount in base units: the raw amount, or the portion amount times the count. */
class ResolvePortion {
    operator fun invoke(amountOrCount: Double, portion: FoodPortion?): Double =
        roundToTenth(if (portion == null) amountOrCount else portion.amount * amountOrCount)
}
