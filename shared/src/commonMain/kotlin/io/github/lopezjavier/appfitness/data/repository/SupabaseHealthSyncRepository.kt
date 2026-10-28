package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import io.github.lopezjavier.appfitness.data.remote.HealthReadingDto
import io.github.lopezjavier.appfitness.data.remote.HealthSyncStateDto
import io.github.lopezjavier.appfitness.data.remote.ImportHealthActivityParams
import io.github.lopezjavier.appfitness.data.remote.ImportHealthActivityResultDto
import io.github.lopezjavier.appfitness.data.remote.ImportHealthParams
import io.github.lopezjavier.appfitness.data.remote.MarkAutoSyncParams
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.HealthActivityBatch
import io.github.lopezjavier.appfitness.domain.model.HealthActivityImportResult
import io.github.lopezjavier.appfitness.domain.model.HealthReading
import io.github.lopezjavier.appfitness.domain.model.HealthSyncState
import io.github.lopezjavier.appfitness.domain.repository.HealthSyncRepository
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** `health_sync_state` and the `import_health_measurements` RPC of the signed-in user (RLS owner policies). */
class SupabaseHealthSyncRepository(private val auth: Auth, private val postgrest: Postgrest) : HealthSyncRepository {
    override suspend fun getState(): Result<HealthSyncState?> = signedIn {
        postgrest.from("health_sync_state")
            .select(Columns.raw(HealthSyncStateDto.COLUMNS)) { limit(1) }
            .decodeList<HealthSyncStateDto>()
            .firstOrNull()
            ?.toDomain()
    }

    override suspend fun importReadings(
        rows: List<HealthReading>,
        syncedAt: Instant,
        changesToken: String?,
    ): Result<Int> = signedIn {
        val params = ImportHealthParams(rows.map(HealthReadingDto::from), syncedAt.toString(), changesToken)
        postgrest.rpc("import_health_measurements", params).decodeAs<Int>()
    }

    override suspend fun importActivity(batch: HealthActivityBatch): Result<HealthActivityImportResult> = signedIn {
        postgrest.rpc("import_health_activity", ImportHealthActivityParams.from(batch))
            .decodeAs<ImportHealthActivityResultDto>()
            .toDomain()
    }

    override suspend fun markAutoSynced(day: LocalDate): Result<Unit> = signedIn {
        postgrest.rpc("mark_health_auto_sync", MarkAutoSyncParams(day.toString()))
        Unit
    }

    private suspend fun <T> signedIn(block: suspend () -> T): Result<T> {
        if (auth.currentUserOrNull() == null) return Result.failure(AppException(AppError.Unauthorized))
        return runMappingErrors(block)
    }
}

/** Health counterpart of [UnconfiguredBodyMeasurementRepository]: there is no server to sync with. */
class UnconfiguredHealthSyncRepository : HealthSyncRepository {
    override suspend fun getState(): Result<HealthSyncState?> = Result.failure(AppException(AppError.Unknown))

    override suspend fun importReadings(
        rows: List<HealthReading>,
        syncedAt: Instant,
        changesToken: String?,
    ): Result<Int> = Result.failure(AppException(AppError.Unknown))

    override suspend fun importActivity(batch: HealthActivityBatch): Result<HealthActivityImportResult> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun markAutoSynced(day: LocalDate): Result<Unit> = Result.failure(AppException(AppError.Unknown))
}
