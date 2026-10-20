package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
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
}
