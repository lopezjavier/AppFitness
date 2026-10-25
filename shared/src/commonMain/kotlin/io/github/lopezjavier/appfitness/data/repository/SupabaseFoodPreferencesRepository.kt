package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.lopezjavier.appfitness.data.remote.FoodPreferencesDto
import io.github.lopezjavier.appfitness.domain.model.FoodPreferences
import io.github.lopezjavier.appfitness.domain.repository.FoodPreferencesRepository

/** The signed-in user's `food_preferences` row (RLS owner policies, primary key `user_id`). */
class SupabaseFoodPreferencesRepository(private val auth: Auth, private val postgrest: Postgrest) :
    FoodPreferencesRepository {
    override suspend fun get(): Result<FoodPreferences> = auth.ownRow { userId ->
        postgrest.from("food_preferences")
            .select(Columns.raw(FoodPreferencesDto.COLUMNS)) { filter { eq("user_id", userId) } }
            .decodeList<FoodPreferencesDto>()
            .firstOrNull()
            ?.toDomain()
            ?: FoodPreferences()
    }
}
