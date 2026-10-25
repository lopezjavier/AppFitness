package io.github.lopezjavier.appfitness.domain.model

/** How a food was cooked (ADR-60/65). [wireName] is the value of the `food_cooking_method` enum. */
enum class CookingMethod(val wireName: String) {
    Raw("raw"),
    Griddle("griddle"),
    AirFryer("air_fryer"),
    Fried("fried"),
    ;

    companion object {
        /** The method with the given wire name, or null when unknown. */
        fun fromWireName(name: String?): CookingMethod? = entries.firstOrNull { it.wireName == name }
    }
}
