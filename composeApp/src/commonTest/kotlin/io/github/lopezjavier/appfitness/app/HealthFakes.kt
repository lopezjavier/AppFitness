package io.github.lopezjavier.appfitness.app

import io.github.lopezjavier.appfitness.domain.model.ChangesResult
import io.github.lopezjavier.appfitness.domain.model.HealthAccess
import io.github.lopezjavier.appfitness.domain.model.HealthActivityBatch
import io.github.lopezjavier.appfitness.domain.model.HealthActivityImportResult
import io.github.lopezjavier.appfitness.domain.model.HealthAvailability
import io.github.lopezjavier.appfitness.domain.model.HealthMetric
import io.github.lopezjavier.appfitness.domain.model.HealthReading
import io.github.lopezjavier.appfitness.domain.model.HealthSyncState
import io.github.lopezjavier.appfitness.domain.model.HistoryProbeResult
import io.github.lopezjavier.appfitness.domain.model.HistoryWindows
import io.github.lopezjavier.appfitness.domain.model.RawHealthRecords
import io.github.lopezjavier.appfitness.domain.model.WindowStats
import io.github.lopezjavier.appfitness.domain.port.HealthDataSource
import io.github.lopezjavier.appfitness.domain.port.HealthHistoryProbe
import io.github.lopezjavier.appfitness.domain.repository.HealthSyncRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

internal data class RecordedWeighIn(
    val rowId: String,
    val weightKg: Double,
    val bodyFatPct: Double?,
    val version: Long,
)

/** Scriptable [HealthDataSource] that records the write-back calls. */
internal class FakeHealthDataSource : HealthDataSource {
    var availability = HealthAvailability.Available
    var access = HealthAccess(HealthMetric.entries.toSet(), canWrite = true)
    var writeFailure: Throwable? = null
    var deleteFailure: Throwable? = null
    var readFailure: Throwable? = null
    var records = RawHealthRecords()
    val writes = mutableListOf<RecordedWeighIn>()
    val deletedCopies = mutableListOf<String>()

    override fun availability() = availability

    override suspend fun access() = access

    override suspend fun newChangesToken(): Result<String> = Result.success("token")

    override suspend fun readRange(from: Instant, to: Instant): Result<RawHealthRecords> {
        readFailure?.let { return Result.failure(it) }
        return Result.success(records)
    }

    override suspend fun readChanges(token: String): Result<ChangesResult> =
        Result.success(ChangesResult.Changed(null, null, token))

    override suspend fun writeWeighIn(
        rowId: String,
        weightKg: Double,
        bodyFatPct: Double?,
        measuredAt: Instant,
        version: Long,
    ): Result<Unit> {
        writeFailure?.let { return Result.failure(it) }
        writes += RecordedWeighIn(rowId, weightKg, bodyFatPct, version)
        return Result.success(Unit)
    }

    override suspend fun deleteCopy(rowId: String): Result<Unit> {
        deleteFailure?.let { return Result.failure(it) }
        deletedCopies += rowId
        return Result.success(Unit)
    }
}

/** Scriptable [HealthHistoryProbe]: returns [result] (or [failure]) and counts the calls. */
internal class FakeHealthHistoryProbe : HealthHistoryProbe {
    var result = HistoryProbeResult(
        featureAvailable = true,
        historyGranted = true,
        steps = HistoryWindows(WindowStats(10, Instant.parse("2026-09-01T00:00:00Z")), WindowStats(5, null)),
        weight = HistoryWindows(WindowStats.EMPTY, WindowStats.EMPTY),
    )
    var failure: Throwable? = null

    /** When set, a probe waits for it: lets a test observe the running state. */
    var gate: CompletableDeferred<Unit>? = null
    var calls = 0

    override suspend fun probe(): Result<HistoryProbeResult> {
        calls++
        gate?.await()
        failure?.let { return Result.failure(it) }
        return Result.success(result)
    }
}

/** In-memory [HealthSyncRepository]: stores the state of the last import and counts the new rows. */
internal class FakeHealthSyncRepository(var state: HealthSyncState? = null) : HealthSyncRepository {
    var stateFailure: Throwable? = null
    var importFailure: Throwable? = null

    /** When set, an import waits for it: lets a test observe the syncing state. */
    var importGate: CompletableDeferred<Unit>? = null
    val imports = mutableListOf<List<HealthReading>>()
    private val known = mutableSetOf<String>()

    override suspend fun getState(): Result<HealthSyncState?> =
        stateFailure?.let { Result.failure(it) } ?: Result.success(state)

    override suspend fun importReadings(
        rows: List<HealthReading>,
        syncedAt: Instant,
        changesToken: String?,
    ): Result<Int> {
        importGate?.await()
        importFailure?.let { return Result.failure(it) }
        imports += rows
        state = HealthSyncState(syncedAt, changesToken)
        return Result.success(rows.count { known.add(it.externalId) })
    }

    var activityFailure: Throwable? = null
    val activityImports = mutableListOf<HealthActivityBatch>()
    val autoSyncedDays = mutableListOf<LocalDate>()

    override suspend fun importActivity(batch: HealthActivityBatch): Result<HealthActivityImportResult> {
        activityFailure?.let { return Result.failure(it) }
        activityImports += batch
        return Result.success(
            HealthActivityImportResult(batch.daily.size, batch.sleep.size, batch.restingHeartRate.size),
        )
    }

    override suspend fun markAutoSynced(day: LocalDate): Result<Unit> {
        activityFailure?.let { return Result.failure(it) }
        autoSyncedDays += day
        state = state?.copy(lastAutoSyncDate = day)
        return Result.success(Unit)
    }
}
