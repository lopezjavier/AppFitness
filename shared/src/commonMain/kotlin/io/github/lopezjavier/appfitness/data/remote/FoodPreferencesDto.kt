package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.FoodPreferences
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Row of `public.food_preferences` as read. */
@Serializable
internal data class FoodPreferencesDto(
    @SerialName("fat_pct_griddle") val fatPctGriddle: Double = FoodPreferences.DEFAULT_GRIDDLE_PCT,
    @SerialName("fat_pct_air_fryer") val fatPctAirFryer: Double = FoodPreferences.DEFAULT_AIR_FRYER_PCT,
    @SerialName("fat_pct_fried") val fatPctFried: Double = FoodPreferences.DEFAULT_FRIED_PCT,
) {
    fun toDomain(): FoodPreferences = FoodPreferences(fatPctGriddle, fatPctAirFryer, fatPctFried)

    companion object {
        const val COLUMNS = "fat_pct_griddle,fat_pct_air_fryer,fat_pct_fried"
    }
}
