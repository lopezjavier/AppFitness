package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.FoodPreferences
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Row of `public.food_preferences` as read. */
@Serializable
internal data class FoodPreferencesDto(
    @SerialName("olive_oil_griddle_g") val oliveOilGriddleG: Double = FoodPreferences.DEFAULT_GRIDDLE_G,
    @SerialName("olive_oil_air_fryer_g") val oliveOilAirFryerG: Double = FoodPreferences.DEFAULT_AIR_FRYER_G,
    @SerialName("olive_oil_fried_g") val oliveOilFriedG: Double = FoodPreferences.DEFAULT_FRIED_G,
) {
    fun toDomain(): FoodPreferences = FoodPreferences(oliveOilGriddleG, oliveOilAirFryerG, oliveOilFriedG)

    companion object {
        const val COLUMNS = "olive_oil_griddle_g,olive_oil_air_fryer_g,olive_oil_fried_g"
    }
}
