package io.github.lopezjavier.appfitness.domain.usecase

import kotlin.math.floor

/**
 * Converts alcohol by volume (% vol) to grams of ethanol per 100 ml (07 section 4). Same rule as the OFF import in the
 * food Edge Function (`_shared/off-client.ts`: `% vol x 0.789`, rounded to 0.1 with `Math.round`, i.e. half up); keep
 * both in step. The 0..100 cap is the field validation of the food form.
 */
object AlcoholByVolume {
    const val FACTOR = 0.789
    private const val TENTHS = 10
    private const val HALF = 0.5

    fun toGramsPer100(abvPercent: Double): Double = floor(abvPercent * FACTOR * TENTHS + HALF) / TENTHS
}
