package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.lopezjavier.appfitness.data.remote.MealSlotDto
import io.github.lopezjavier.appfitness.domain.model.MealSlot
import io.github.lopezjavier.appfitness.domain.repository.MealSlotRepository

/** The signed-in user's `meal_slots` (RLS owner policies). */
class SupabaseMealSlotRepository(private val auth: Auth, private val postgrest: Postgrest) : MealSlotRepository {
    override suspend fun listEnabled(): Result<List<MealSlot>> = auth.ownRow {
        postgrest.from("meal_slots")
            .select(Columns.raw(MealSlotDto.COLUMNS)) {
                filter { eq("enabled", true) }
                order("position", Order.ASCENDING)
            }
            .decodeList<MealSlotDto>()
            .map { it.toDomain() }
    }
}
