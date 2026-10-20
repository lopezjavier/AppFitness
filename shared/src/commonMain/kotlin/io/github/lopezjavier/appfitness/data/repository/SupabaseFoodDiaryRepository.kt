package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.lopezjavier.appfitness.data.remote.FoodLogEntryDto
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.repository.FoodDiaryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate

/** The signed-in user's `food_log_entries` (RLS owner policies) with an in-memory cache per day. */
class SupabaseFoodDiaryRepository(private val auth: Auth, private val postgrest: Postgrest) : FoodDiaryRepository {
    private val cache = MutableStateFlow<Map<LocalDate, List<FoodLogEntry>>>(emptyMap())

    override fun entries(date: LocalDate): Flow<List<FoodLogEntry>> =
        cache.map { it[date] }.filterNotNull().distinctUntilChanged()

    override suspend fun refresh(date: LocalDate): Result<Unit> = auth.ownRow {
        val rows = postgrest.from("food_log_entries")
            .select(Columns.raw(FoodLogEntryDto.COLUMNS)) {
                filter { eq("log_date", date.toString()) }
                order("created_at", Order.ASCENDING)
                order("id", Order.ASCENDING)
            }
            .decodeList<FoodLogEntryDto>()
            .map { it.toDomain() }
        cache.update { it + (date to rows) }
    }
}
