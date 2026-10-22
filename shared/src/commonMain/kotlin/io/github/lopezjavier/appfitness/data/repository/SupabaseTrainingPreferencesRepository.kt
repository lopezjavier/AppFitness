package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.lopezjavier.appfitness.data.remote.TrainingPreferencesDto
import io.github.lopezjavier.appfitness.domain.model.TrainingPreferences
import io.github.lopezjavier.appfitness.domain.repository.TrainingPreferencesRepository

/** The signed-in user's `training_preferences` row (RLS owner policies, primary key `user_id`). */
class SupabaseTrainingPreferencesRepository(private val auth: Auth, private val postgrest: Postgrest) :
    TrainingPreferencesRepository {
    override suspend fun get(): Result<TrainingPreferences> = auth.ownRow { userId ->
        postgrest.from("training_preferences")
            .select(Columns.raw(TrainingPreferencesDto.COLUMNS)) { filter { eq("user_id", userId) } }
            .decodeList<TrainingPreferencesDto>()
            .firstOrNull()
            ?.toDomain()
            ?: TrainingPreferences()
    }
}
