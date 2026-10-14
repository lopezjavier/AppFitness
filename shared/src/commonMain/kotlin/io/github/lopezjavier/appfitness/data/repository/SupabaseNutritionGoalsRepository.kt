package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.lopezjavier.appfitness.data.remote.NutritionGoalsDto
import io.github.lopezjavier.appfitness.data.remote.NutritionGoalsUpsertDto
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.repository.NutritionGoalsRepository

/** The signed-in user's `nutrition_goals` row (RLS owner policies, primary key `user_id`). */
class SupabaseNutritionGoalsRepository(private val auth: Auth, private val postgrest: Postgrest) :
    NutritionGoalsRepository {
    override suspend fun getOwn(): Result<NutritionGoals?> = auth.ownRow { userId ->
        postgrest.from("nutrition_goals")
            .select(Columns.raw(NutritionGoalsDto.COLUMNS)) { filter { eq("user_id", userId) } }
            .decodeList<NutritionGoalsDto>()
            .firstOrNull()
            ?.toDomain()
    }

    override suspend fun upsert(goals: NutritionGoals): Result<Unit> = auth.ownRow { userId ->
        postgrest.from("nutrition_goals").upsert(NutritionGoalsUpsertDto.from(userId, goals)) {
            onConflict = "user_id"
        }
    }.map {}
}
