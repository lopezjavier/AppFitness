package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import io.github.lopezjavier.appfitness.data.remote.ArchiveRoutineUpdateDto
import io.github.lopezjavier.appfitness.data.remote.RoutineDto
import io.github.lopezjavier.appfitness.data.remote.RoutineSummaryDto
import io.github.lopezjavier.appfitness.data.remote.SaveRoutineParams
import io.github.lopezjavier.appfitness.data.remote.SaveRoutineRequest
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.Routine
import io.github.lopezjavier.appfitness.domain.model.RoutineBounds
import io.github.lopezjavier.appfitness.domain.model.RoutineSummary
import io.github.lopezjavier.appfitness.domain.repository.RoutineRepository

private const val COPY_SUFFIX = " (copia)"

/** Routines of the signed-in user (RLS scopes every query). Writes go through the atomic `save_routine` RPC. */
class SupabaseRoutineRepository(private val auth: Auth, private val postgrest: Postgrest) : RoutineRepository {
    override suspend fun listActive(): Result<List<RoutineSummary>> = auth.ownRow {
        postgrest.from("routines")
            .select(Columns.raw(RoutineSummaryDto.COLUMNS)) {
                filter { eq("archived", false) }
                order("created_at", Order.DESCENDING)
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
    }

    override suspend fun duplicate(id: String): Result<String> {
        val original = get(id).getOrElse { return Result.failure(it) }
        val name = original.name.take(RoutineBounds.NAME_MAX_LENGTH - COPY_SUFFIX.length) + COPY_SUFFIX
        return save(original.copy(id = null, name = name, archived = false))
    }

    override suspend fun archive(id: String): Result<Unit> = auth.ownRow {
        postgrest.from("routines").update(ArchiveRoutineUpdateDto(archived = true)) { filter { eq("id", id) } }
    }.map {}
}
