package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.lopezjavier.appfitness.data.remote.FoodLogEntryDto
import io.github.lopezjavier.appfitness.data.remote.FoodLogEntryInsertDto
import io.github.lopezjavier.appfitness.data.remote.FoodLogEntryUpdateDto
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.NewFoodEntry
import io.github.lopezjavier.appfitness.domain.model.UpdatedFoodEntry
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

    override suspend fun addEntry(entry: NewFoodEntry): Result<FoodLogEntry> = auth.ownRow {
        val created = postgrest.from("food_log_entries")
            .insert(FoodLogEntryInsertDto.from(entry)) { select(Columns.raw(FoodLogEntryDto.COLUMNS)) }
            .decodeSingle<FoodLogEntryDto>()
            .toDomain()
        // A day that was never loaded has no cache yet; its first refresh will include the entry.
        cache.update { days -> days[created.logDate]?.let { days + (created.logDate to (it + created)) } ?: days }
        created
    }

    override suspend fun updateEntry(id: String, update: UpdatedFoodEntry): Result<FoodLogEntry> = auth.ownRow {
        val updated = postgrest.from("food_log_entries")
            .update(FoodLogEntryUpdateDto.from(update)) {
                select(Columns.raw(FoodLogEntryDto.COLUMNS))
                filter { eq("id", id) }
            }
            .decodeSingle<FoodLogEntryDto>()
            .toDomain()
        cache.update { days ->
            days.mapValues { (_, entries) -> entries.map { if (it.id == id) updated else it } }
        }
        updated
    }

    override suspend fun deleteEntry(id: String): Result<Unit> = auth.ownRow {
        postgrest.from("food_log_entries").delete { filter { eq("id", id) } }
        cache.update { days -> days.mapValues { (_, entries) -> entries.filterNot { it.id == id } } }
    }

    override suspend fun addEntries(entries: List<NewFoodEntry>): Result<List<FoodLogEntry>> = auth.ownRow {
        if (entries.isEmpty()) return@ownRow emptyList()
        val created = postgrest.from("food_log_entries")
            .insert(entries.map(FoodLogEntryInsertDto::from)) { select(Columns.raw(FoodLogEntryDto.COLUMNS)) }
            .decodeList<FoodLogEntryDto>()
            .map { it.toDomain() }
        cache.update { days ->
            days.mapValues { (date, loaded) ->
                val joining = created.filter { it.logDate == date }
                if (joining.isEmpty()) loaded else loaded + joining
            }
        }
        created
    }

    override suspend fun deleteEntries(ids: List<String>): Result<Unit> = auth.ownRow {
        if (ids.isEmpty()) return@ownRow
        postgrest.from("food_log_entries").delete { filter { isIn("id", ids) } }
        val gone = ids.toSet()
        cache.update { days -> days.mapValues { (_, entries) -> entries.filterNot { it.id in gone } } }
    }
}
