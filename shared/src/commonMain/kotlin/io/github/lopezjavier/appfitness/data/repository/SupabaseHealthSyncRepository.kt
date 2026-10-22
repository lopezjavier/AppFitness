package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import io.github.lopezjavier.appfitness.data.remote.HealthReadingDto
import io.github.lopezjavier.appfitness.data.remote.HealthSyncStateDto
import io.github.lopezjavier.appfitness.data.remote.ImportHealthParams
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.HealthReading
import io.github.lopezjavier.appfitness.domain.model.HealthSyncState
import io.github.lopezjavier.appfitness.domain.repository.HealthSyncRepository
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
}
