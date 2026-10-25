package io.github.lopezjavier.appfitness.domain.model

/**
 * How a food was cooked (ADR-60). [retentionFactor] is the share of the olive oil put in the pan that stays in the
 * food; [wireName] is the value of the `food_cooking_method` enum.
 */
@Suppress("MagicNumber") // The retention factors of ADR-60.
enum class CookingMethod(val wireName: String, val retentionFactor: Double) {
    Raw("raw", 0.0),
    Griddle("griddle", 0.5),
    AirFryer("air_fryer", 1.0),
    Fried("fried", 0.1),
    ;

    companion object {
        /** The method with the given wire name, or null when unknown. */
        fun fromWireName(name: String?): CookingMethod? = entries.firstOrNull { it.wireName == name }
    }
}
