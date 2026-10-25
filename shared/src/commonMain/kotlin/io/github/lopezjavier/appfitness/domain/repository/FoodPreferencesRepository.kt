package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.FoodPreferences

/** The signed-in user's `food_preferences` row (written through `save_preferences`). */
interface FoodPreferencesRepository {
    /** The saved preferences, read fresh from the server; a missing row yields the defaults. */
    suspend fun get(): Result<FoodPreferences>
}
