package io.github.lopezjavier.appfitness.domain.model

/** Grams of olive oil put in the pan per cooking method (`food_preferences`, ADR-60). */
data class FoodPreferences(
    val oliveOilGriddleG: Double = DEFAULT_GRIDDLE_G,
    val oliveOilAirFryerG: Double = DEFAULT_AIR_FRYER_G,
    val oliveOilFriedG: Double = DEFAULT_FRIED_G,
) {
    /** Every amount is within 0..[MAX_OIL_G] inclusive. */
    val isValid: Boolean
        get() = listOf(oliveOilGriddleG, oliveOilAirFryerG, oliveOilFriedG).all(::isValidAmount)

    /** Grams of oil used with [method]; raw uses none. */
    fun usedG(method: CookingMethod): Double = when (method) {
        CookingMethod.Raw -> 0.0
        CookingMethod.Griddle -> oliveOilGriddleG
        CookingMethod.AirFryer -> oliveOilAirFryerG
        CookingMethod.Fried -> oliveOilFriedG
    }

    companion object {
        const val DEFAULT_GRIDDLE_G = 5.0
        const val DEFAULT_AIR_FRYER_G = 5.0
        const val DEFAULT_FRIED_G = 50.0
        const val MAX_OIL_G = 500.0

        fun isValidAmount(grams: Double): Boolean = !grams.isNaN() && grams in 0.0..MAX_OIL_G
    }
}
