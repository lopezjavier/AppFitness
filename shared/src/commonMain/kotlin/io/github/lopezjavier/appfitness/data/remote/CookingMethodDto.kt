package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.CookingMethod
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire values of the `food_cooking_method` enum. */
@Serializable
internal enum class CookingMethodDto(val domain: CookingMethod) {
    @SerialName("raw")
    Raw(CookingMethod.Raw),

    @SerialName("griddle")
    Griddle(CookingMethod.Griddle),

    @SerialName("air_fryer")
    AirFryer(CookingMethod.AirFryer),

    @SerialName("fried")
    Fried(CookingMethod.Fried),
    ;

    companion object {
        fun of(method: CookingMethod): CookingMethodDto = entries.first { it.domain == method }
    }
}
