package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.HealthReading
import io.github.lopezjavier.appfitness.domain.model.HealthSyncState
import kotlin.time.Instant

/** The signed-in user's Health Connect sync state and imports. A failure carries an `AppException`. */
interface HealthSyncRepository {
    /** The stored state, or null when the user never synced. */
    suspend fun getState(): Result<HealthSyncState?>

    /**
     * Imports [rows] idempotently and stores [syncedAt] and [changesToken] in the same transaction. Returns the number
     * of rows that were new.
     */
    suspend fun importReadings(rows: List<HealthReading>, syncedAt: Instant, changesToken: String?): Result<Int>
}
