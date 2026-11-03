package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.lopezjavier.appfitness.data.remote.FavoriteExerciseDto
import io.github.lopezjavier.appfitness.data.remote.FavoriteExerciseInsertDto
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.repository.FavoriteExercisesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Starred exercises over `exercise_favorites`; the id set is cached and only changes after the server accepts. */
class SupabaseFavoriteExercisesRepository(private val auth: Auth, private val postgrest: Postgrest) :
    FavoriteExercisesRepository {
    private val ids = MutableStateFlow<Set<String>>(emptySet())

    override val favoriteIds: StateFlow<Set<String>> = ids

    override suspend fun refresh(): Result<Unit> = auth.ownRow {
        ids.value = postgrest.from(TABLE)
            .select(Columns.raw("exercise_id"))
            .decodeList<FavoriteExerciseDto>()
            .mapTo(mutableSetOf()) { it.exerciseId }
    }

    override suspend fun setFavorite(exerciseId: String, favorite: Boolean): Result<Unit> = auth.ownRow {
        if (favorite) {
            postgrest.from(TABLE).upsert(FavoriteExerciseInsertDto(exerciseId)) {
                onConflict = "user_id,exercise_id"
                ignoreDuplicates = true
            }
        } else {
            postgrest.from(TABLE).delete { filter { eq("exercise_id", exerciseId) } }
        }
        ids.update { if (favorite) it + exerciseId else it - exerciseId }
    }

    private companion object {
        const val TABLE = "exercise_favorites"
    }
}

/** Counterpart of [SupabaseFavoriteExercisesRepository] when Supabase is not configured: nothing to star. */
class UnconfiguredFavoriteExercisesRepository : FavoriteExercisesRepository {
    override val favoriteIds: StateFlow<Set<String>> = MutableStateFlow(emptySet())

    override suspend fun refresh(): Result<Unit> = Result.failure(AppException(AppError.Unknown))

    override suspend fun setFavorite(exerciseId: String, favorite: Boolean): Result<Unit> =
        Result.failure(AppException(AppError.Unknown))
}
