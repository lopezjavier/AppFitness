package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import io.github.lopezjavier.appfitness.data.remote.DeleteCustomExerciseParams
import io.github.lopezjavier.appfitness.data.remote.ExerciseDto
import io.github.lopezjavier.appfitness.data.remote.ExerciseInsertDto
import io.github.lopezjavier.appfitness.data.remote.UpdateCustomExerciseParams
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.Exercise
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.MovementPattern
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.repository.ExerciseRepository

private const val INVALID_PARAMETER = "22023"
private const val NO_DATA_FOUND = "P0002"
private const val FOREIGN_KEY_VIOLATION = "23503"

// Detail `sets=N;routines=M` or message `(N sets, M routines)` of `delete_custom_exercise`.
private val IN_USE_COUNTS = Regex("""sets=(\d+);routines=(\d+)|\((\d+) sets, (\d+) routines\)""")

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

    override suspend fun createCustom(
        name: String,
        muscleGroup: MuscleGroup,
        equipment: Equipment,
        metricType: ExerciseMetricType,
        perHand: Boolean,
        movementPattern: MovementPattern?,
    ): Result<Exercise> = signedIn {
        val body = ExerciseInsertDto(name.trim(), muscleGroup, equipment, metricType, true, perHand, movementPattern)
        postgrest.from("exercises")
            .insert(body) {
                select()
            }
            .decodeSingle<ExerciseDto>()
            .toDomain()
            .also { cache = null }
    }

    override suspend fun updateCustom(
        id: String,
        name: String,
        muscleGroup: MuscleGroup,
        equipment: Equipment,
        perHand: Boolean,
        movementPattern: MovementPattern?,
    ): Result<Unit> = signedIn {
        postgrest.rpc(
            "update_custom_exercise",
            UpdateCustomExerciseParams(id, name.trim(), muscleGroup, equipment, perHand, movementPattern),
        )
        cache = null
    }.mapRpcErrors()

    override suspend fun deleteCustom(id: String): Result<Unit> = signedIn {
        postgrest.rpc("delete_custom_exercise", DeleteCustomExerciseParams(id))
        cache = null
    }.mapRpcErrors()

    private fun Result<Unit>.mapRpcErrors(): Result<Unit> = recoverCatching { failure ->
        val cause = (failure as? AppException)?.cause as? PostgrestRestException ?: throw failure
        when (cause.code) {
            INVALID_PARAMETER -> throw AppException(AppError.Validation, cause)
            NO_DATA_FOUND -> throw AppException(AppError.NotFound, cause)
            FOREIGN_KEY_VIOLATION -> throw AppException(inUse(cause), cause)
            else -> throw failure
        }
    }

    private fun inUse(cause: PostgrestRestException): AppError {
        val text = "${cause.message} ${cause.details}"
        val match = IN_USE_COUNTS.find(text) ?: return AppError.Unknown
        val (sets, routines) = match.groupValues.drop(1).filter { it.isNotEmpty() }
        return AppError.InUse(sets.toInt(), routines.toInt())
    }

    private suspend fun <T> signedIn(block: suspend () -> T): Result<T> {
        if (auth.currentUserOrNull() == null) return Result.failure(AppException(AppError.Unauthorized))
        return runMappingErrors(block)
    }
}
