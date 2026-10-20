package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.NewFoodEntry
import io.github.lopezjavier.appfitness.domain.model.UpdatedFoodEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/** The signed-in user's food log. A failure carries an [AppException]. */
interface FoodDiaryRepository {
    /**
     * The entries of [date], oldest first. Cache-backed: nothing is emitted until [refresh] has loaded the day once,
     * then every later change to the cache (a write, a refresh) emits the new list.
     */
    fun entries(date: LocalDate): Flow<List<FoodLogEntry>>

    /** Loads the entries of [date] from the server into the cache. */
    suspend fun refresh(date: LocalDate): Result<Unit>

    /** Logs a food; on success the entry joins the cache of its day, so [entries] re-emits without a refresh. */
    suspend fun addEntry(entry: NewFoodEntry): Result<FoodLogEntry>

    /** Changes the amount, portion, meal and snapshot of entry [id]; the cache of its day is updated in place. */
    suspend fun updateEntry(id: String, update: UpdatedFoodEntry): Result<FoodLogEntry>

    /** Deletes entry [id]; it leaves the cache of its day. */
    suspend fun deleteEntry(id: String): Result<Unit>

    /** Logs several foods in ONE request (copy, repeat); each created entry joins the cache of its day if loaded. */
    suspend fun addEntries(entries: List<NewFoodEntry>): Result<List<FoodLogEntry>>

    /** Deletes the entries [ids] in ONE request (undo of a copy); they leave the cache. */
    suspend fun deleteEntries(ids: List<String>): Result<Unit>
}
