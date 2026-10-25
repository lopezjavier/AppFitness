package io.github.lopezjavier.appfitness.domain.model

/** Percentage of the raw weight added as fat per cooking method (`food_preferences`, ADR-65). */
data class FoodPreferences(
    val fatPctGriddle: Double = DEFAULT_GRIDDLE_PCT,
    val fatPctAirFryer: Double = DEFAULT_AIR_FRYER_PCT,
    val fatPctFried: Double = DEFAULT_FRIED_PCT,
) {
    /** Every percentage is within 0..[MAX_PCT] inclusive. */
    val isValid: Boolean
        get() = listOf(fatPctGriddle, fatPctAirFryer, fatPctFried).all(::isValidPct)

    /** Percentage of fat added with [method]; raw adds none. */
    fun pctFor(method: CookingMethod): Double = when (method) {
        CookingMethod.Raw -> 0.0
        CookingMethod.Griddle -> fatPctGriddle
        CookingMethod.AirFryer -> fatPctAirFryer
        CookingMethod.Fried -> fatPctFried
    }

    companion object {
        const val DEFAULT_GRIDDLE_PCT = 1.0
        const val DEFAULT_AIR_FRYER_PCT = 2.0
        const val DEFAULT_FRIED_PCT = 8.0
        const val MAX_PCT = 20.0

        fun isValidPct(pct: Double): Boolean = !pct.isNaN() && pct in 0.0..MAX_PCT
    }
}
