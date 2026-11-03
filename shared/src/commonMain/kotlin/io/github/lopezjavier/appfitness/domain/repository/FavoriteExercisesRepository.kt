package io.github.lopezjavier.appfitness.domain.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * The exercises the signed-in user starred (catalogue and own alike, by exercise id). The set is cached so a star
 * flips, and the lists re-sort, without re-reading. Offline-safe: a failed read keeps the last known set.
 */
interface FavoriteExercisesRepository {
    /** Ids of the starred exercises; empty until the first successful [refresh]. */
    val favoriteIds: StateFlow<Set<String>>

    /** Reads the set from the server; on failure the last known set is kept. */
    suspend fun refresh(): Result<Unit>

    /** Stars or unstars [exerciseId] (idempotent). On failure the set is unchanged. */
    suspend fun setFavorite(exerciseId: String, favorite: Boolean): Result<Unit>
}
