package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.lopezjavier.appfitness.data.remote.ExerciseDto
import io.github.lopezjavier.appfitness.data.remote.ExerciseInsertDto
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.Exercise
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.repository.ExerciseRepository

/**
 * `exercises` visible to the signed-in user: the read-only catalog rows plus their own custom rows (RLS). The list
 * (about 650 rows) is fetched once and kept for the session; creating an exercise drops it.
 */
class SupabaseExerciseRepository(private val auth: Auth, private val postgrest: Postgrest) : ExerciseRepository {
    private var cache: List<Exercise>? = null

    override suspend fun getAll(): Result<List<Exercise>> = signedIn {
        cache ?: postgrest.from("exercises")
            .select(Columns.raw(ExerciseDto.COLUMNS))
            .decodeList<ExerciseDto>()
            .map { it.toDomain() }
            .also { cache = it }
    }

    override suspend fun getById(id: String): Result<Exercise> = signedIn {
        cache?.firstOrNull { it.id == id }
            ?: postgrest.from("exercises")
                .select(Columns.raw(ExerciseDto.COLUMNS)) { filter { eq("id", id) } }
                .decodeList<ExerciseDto>()
                .firstOrNull()
                ?.toDomain()
    }.mapCatching { it ?: throw AppException(AppError.NotFound) }

    override suspend fun createCustom(name: String, muscleGroup: MuscleGroup, equipment: Equipment): Result<Exercise> =
        signedIn {
            postgrest.from("exercises")
                .insert(ExerciseInsertDto(name.trim(), muscleGroup, equipment, isCustom = true)) { select() }
                .decodeSingle<ExerciseDto>()
                .toDomain()
                .also { cache = null }
        }

    private suspend fun <T> signedIn(block: suspend () -> T): Result<T> {
        if (auth.currentUserOrNull() == null) return Result.failure(AppException(AppError.Unauthorized))
        return runMappingErrors(block)
    }
}
