package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.ChangesResult
import io.github.lopezjavier.appfitness.domain.model.HealthAvailability
import io.github.lopezjavier.appfitness.domain.model.HealthMetric
import io.github.lopezjavier.appfitness.domain.model.RawHealthRecords
import io.github.lopezjavier.appfitness.domain.model.SyncOutcome
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.port.HealthDataSource
import io.github.lopezjavier.appfitness.domain.repository.HealthSyncRepository
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Imports the scale's weigh-ins from Health Connect (planning/04 §3.7). A new changes token is taken before every
 * read so nothing written in between is missed. Later syncs read only the time range the changed records touch (plus
 * two minutes, so the fat record of a weigh-in pairs with its weight even in another batch); the first sync, a
 * missing token or an expired one reads the last 90 days (idempotent on the server).
 */
class SyncHealthMeasurements(
    private val source: HealthDataSource,
    private val repository: HealthSyncRepository,
    private val clock: Clock,
) {
    @Suppress("ReturnCount") // Guard clauses: unavailable, no permission, then the failing calls.
    suspend operator fun invoke(): Result<SyncOutcome> {
        val availability = source.availability()
        if (availability != HealthAvailability.Available) {
            return Result.success(SyncOutcome.Unavailable(availability))
        }
        if (HealthMetric.Weight !in source.access().read) return Result.success(SyncOutcome.PermissionNeeded)

        val state = repository.getState().getOrElse { return Result.failure(it) }
        val now = clock.now()
        val token = source.newChangesToken().getOrElse { return Result.failure(it) }
        val records = readRecords(state?.changesToken, now).getOrElse { return Result.failure(it) }
        val readings = PairHealthReadings(records)
            .sortedByDescending { it.measuredAt }
            .take(MAX_ROWS_PER_IMPORT)
        return repository.importReadings(readings, now, token).map { SyncOutcome.Synced(it, now) }
    }

    private suspend fun readRecords(storedToken: String?, now: Instant): Result<RawHealthRecords> {
        val changes = storedToken?.let {
            source.readChanges(it).getOrElse { failure -> return Result.failure(failure) }
        }
        return when {
            changes == null || changes == ChangesResult.TokenExpired -> readInitialWindow(now)

            changes is ChangesResult.Changed && changes.touchedFrom != null && changes.touchedTo != null ->
                source.readRange(changes.touchedFrom - PAIRING_MARGIN, changes.touchedTo + PAIRING_MARGIN)

            else -> Result.success(RawHealthRecords())
        }
    }

    private suspend fun readInitialWindow(now: Instant) = source.readRange(now - INITIAL_WINDOW, now)

    private companion object {
        val INITIAL_WINDOW = 90.days
        val PAIRING_MARGIN = 2.minutes
        const val MAX_ROWS_PER_IMPORT = 1000
    }
}
