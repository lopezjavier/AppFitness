package io.github.lopezjavier.appfitness.domain.port

import io.github.lopezjavier.appfitness.domain.model.ChangesResult
import io.github.lopezjavier.appfitness.domain.model.HealthAccess
import io.github.lopezjavier.appfitness.domain.model.HealthActivityBatch
import io.github.lopezjavier.appfitness.domain.model.HealthAvailability
import io.github.lopezjavier.appfitness.domain.model.RawHealthRecords
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * Health Connect as seen by the domain (planning/04 §3.7). Every Health Connect exception becomes
 * `Result.failure`; nothing here throws.
 */
interface HealthDataSource {
    fun availability(): HealthAvailability

    /** The permissions granted right now. */
    suspend fun access(): HealthAccess

    /** A Changes API token for the six record types. */
    suspend fun newChangesToken(): Result<String>

    /** Records of the six types with `from <= time <= to`; only the metrics with read access are read. */
    suspend fun readRange(from: Instant, to: Instant): Result<RawHealthRecords>

    /**
     * The watch data of `from..to`, already shaped: daily totals per local day of [zone], sleep sessions with stages
     * and minutes, one resting heart rate per day. Own-origin records are left out; a type without read access (or
     * without data) gives an empty list.
     */
    suspend fun readWatchData(from: Instant, to: Instant, zone: TimeZone): Result<HealthActivityBatch>

    /** What changed since [token]; deletions are ignored. */
    suspend fun readChanges(token: String): Result<ChangesResult>

    /**
     * Writes (or updates, when [version] is higher than the stored one) the weight copy `<rowId>:w` and the fat copy
     * `<rowId>:f`; a null [bodyFatPct] deletes the fat copy.
     */
    suspend fun writeWeighIn(
        rowId: String,
        weightKg: Double,
        bodyFatPct: Double?,
        measuredAt: Instant,
        version: Long,
    ): Result<Unit>

    /** Deletes the weight and fat copies of [rowId]. */
    suspend fun deleteCopy(rowId: String): Result<Unit>
}
