package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import io.github.lopezjavier.appfitness.data.remote.CreateRoutineFolderParams
import io.github.lopezjavier.appfitness.data.remote.DeleteRoutineFolderParams
import io.github.lopezjavier.appfitness.data.remote.MoveRoutineToFolderParams
import io.github.lopezjavier.appfitness.data.remote.RenameRoutineFolderParams
import io.github.lopezjavier.appfitness.data.remote.RoutineDto
import io.github.lopezjavier.appfitness.data.remote.RoutineFolderDto
import io.github.lopezjavier.appfitness.data.remote.RoutineSummaryDto
import io.github.lopezjavier.appfitness.data.remote.SaveRoutineOrderParams
import io.github.lopezjavier.appfitness.data.remote.SaveRoutineParams
import io.github.lopezjavier.appfitness.data.remote.SaveRoutineRequest
import io.github.lopezjavier.appfitness.data.remote.SaveWorkoutAsRoutineParams
import io.github.lopezjavier.appfitness.data.remote.SessionIdParams
import io.github.lopezjavier.appfitness.data.remote.SetNextRoutineParams
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.Routine
import io.github.lopezjavier.appfitness.domain.model.RoutineBounds
import io.github.lopezjavier.appfitness.domain.model.RoutineFolder
import io.github.lopezjavier.appfitness.domain.model.RoutineSummary
import io.github.lopezjavier.appfitness.domain.repository.RoutineRepository

private const val COPY_SUFFIX = " (copia)"
private const val MAX_COPY_ATTEMPTS = 20

// Raised by the unique index `routines_user_name_key`.
private const val UNIQUE_VIOLATION = "23505"

/** Routines of the signed-in user (RLS scopes every query). Writes go through the atomic `save_routine` RPC. */
@Suppress("TooManyFunctions") // One method per port operation.
class SupabaseRoutineRepository(private val auth: Auth, private val postgrest: Postgrest) : RoutineRepository {
    override suspend fun listActive(): Result<List<RoutineSummary>> = auth.ownRow {
        postgrest.from("routines")
            .select(Columns.raw(RoutineSummaryDto.COLUMNS)) {
                filter { eq("archived", false) }
                order("position", Order.ASCENDING)
                order("created_at", Order.ASCENDING)
            }
            .decodeList<RoutineSummaryDto>()
            .map { it.toDomain() }
    }

    override suspend fun get(id: String): Result<Routine> = auth.ownRow {
        postgrest.from("routines")
            .select(Columns.raw(RoutineDto.COLUMNS)) { filter { eq("id", id) } }
            .decodeList<RoutineDto>()
            .firstOrNull()
            ?.toDomain()
    }.mapCatching { it ?: throw AppException(AppError.NotFound) }

    override suspend fun save(routine: Routine): Result<String> = auth.ownRow {
        postgrest.rpc("save_routine", SaveRoutineParams(SaveRoutineRequest.from(routine))).decodeAs<String>()
    }.mapNameErrors()

    override suspend fun duplicate(id: String): Result<String> {
        val original = get(id).getOrElse { return Result.failure(it) }
        var result: Result<String> = Result.failure(AppException(AppError.NameAlreadyExists))
        for (attempt in 1..MAX_COPY_ATTEMPTS) {
            result = save(original.copy(id = null, name = copyName(original.name, attempt), archived = false))
            if (result.appErrorOrNull() != AppError.NameAlreadyExists) break
        }
        return result
    }

    /** "<name> (copia)" for the first attempt, then "<name> (copia 2)", "(copia 3)"...; the name part is truncated. */
    private fun copyName(name: String, attempt: Int): String {
        val suffix = if (attempt == 1) COPY_SUFFIX else " (copia $attempt)"
        return name.take(RoutineBounds.NAME_MAX_LENGTH - suffix.length) + suffix
    }

    override suspend fun updateFromSession(sessionId: String): Result<String> = auth.ownRow {
        postgrest.rpc("update_routine_from_session", SessionIdParams(sessionId)).decodeAs<String>()
    }

    override suspend fun createFromSession(sessionId: String, name: String): Result<String> = auth.ownRow {
        postgrest.rpc("save_workout_as_routine", SaveWorkoutAsRoutineParams(sessionId, name.trim())).decodeAs<String>()
    }.mapNameErrors()

    override suspend fun delete(id: String): Result<Unit> = auth.ownRow {
        postgrest.from("routines").delete { filter { eq("id", id) } }
    }.map {}

    override suspend fun listFolders(): Result<List<RoutineFolder>> = auth.ownRow {
        postgrest.from("routine_folders")
            .select(Columns.raw(RoutineFolderDto.COLUMNS)) {
                order("position", Order.ASCENDING)
                order("created_at", Order.ASCENDING)
            }
            .decodeList<RoutineFolderDto>()
            .map { it.toDomain() }
    }

    override suspend fun createFolder(name: String): Result<String> = auth.ownRow {
        postgrest.rpc("create_routine_folder", CreateRoutineFolderParams(name)).decodeAs<String>()
    }

    override suspend fun renameFolder(id: String, name: String): Result<Unit> = auth.ownRow {
        postgrest.rpc("rename_routine_folder", RenameRoutineFolderParams(id, name))
    }.map {}

    override suspend fun deleteFolder(id: String): Result<Int> = auth.ownRow {
        postgrest.rpc("delete_routine_folder", DeleteRoutineFolderParams(id)).decodeAs<Int>()
    }

    override suspend fun moveToFolder(routineId: String, folderId: String?): Result<Unit> = auth.ownRow {
        postgrest.rpc("move_routine_to_folder", MoveRoutineToFolderParams(routineId, folderId))
    }.map {}

    override suspend fun saveOrder(folderId: String?, orderedIds: List<String>): Result<Unit> = auth.ownRow {
        postgrest.rpc("save_routine_order", SaveRoutineOrderParams(folderId, orderedIds))
    }.map {}

    override suspend fun setNext(routineId: String?): Result<Unit> = auth.ownRow {
        postgrest.rpc("set_next_routine", SetNextRoutineParams(routineId))
    }.map {}

    private fun <T> Result<T>.mapNameErrors(): Result<T> = recoverCatching { failure ->
        val cause = (failure as? AppException)?.cause as? PostgrestRestException ?: throw failure
        if (cause.code == UNIQUE_VIOLATION) throw AppException(AppError.NameAlreadyExists, cause) else throw failure
    }
}
