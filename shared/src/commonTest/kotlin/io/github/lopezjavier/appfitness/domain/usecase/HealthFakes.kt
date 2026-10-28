package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.ChangesResult
import io.github.lopezjavier.appfitness.domain.model.HealthAccess
import io.github.lopezjavier.appfitness.domain.model.HealthActivityBatch
import io.github.lopezjavier.appfitness.domain.model.HealthActivityImportResult
import io.github.lopezjavier.appfitness.domain.model.HealthAvailability
import io.github.lopezjavier.appfitness.domain.model.HealthMetric
import io.github.lopezjavier.appfitness.domain.model.HealthReading
import io.github.lopezjavier.appfitness.domain.model.HealthSyncState
import io.github.lopezjavier.appfitness.domain.model.LatestComposition
import io.github.lopezjavier.appfitness.domain.model.ManualWeightSave
import io.github.lopezjavier.appfitness.domain.model.RawHealthRecords
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.port.HealthDataSource
import io.github.lopezjavier.appfitness.domain.repository.BodyMeasurementRepository
import io.github.lopezjavier.appfitness.domain.repository.HealthSyncRepository
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

internal val ALL_READ = HealthMetric.entries.toSet()

internal class FixedTestClock(var at: Instant) : Clock {
    override fun today(): LocalDate = LocalDate(2026, 10, 22)

    override fun now(): Instant = at
}

internal data class WeighInWrite(
    val rowId: String,
    val weightKg: Double,
    val bodyFatPct: Double?,
    val measuredAt: Instant,
    val version: Long,
)

/** Scriptable [HealthDataSource]: records every call so tests assert on behaviour. */
internal class FakeHealthDataSource : HealthDataSource {
    var availability = HealthAvailability.Available
    var access = HealthAccess(ALL_READ, canWrite = true)
    var records = RawHealthRecords()
    var changes: Result<ChangesResult> = Result.success(ChangesResult.Changed(null, null, "next"))
    var tokens = ArrayDeque(listOf("token-1", "token-2", "token-3"))
    var tokenFailure: Throwable? = null
    var readFailure: Throwable? = null
    var writeFailure: Throwable? = null
    var deleteFailure: Throwable? = null

    val events = mutableListOf<String>()
    val reads = mutableListOf<Pair<Instant, Instant>>()
    val changeReads = mutableListOf<String>()
    val writes = mutableListOf<WeighInWrite>()
    val deletedCopies = mutableListOf<String>()

    override fun availability() = availability

    override suspend fun access() = access

    override suspend fun newChangesToken(): Result<String> {
        events += "token"
        tokenFailure?.let { return Result.failure(it) }
        return Result.success(tokens.removeFirst())
    }

    override suspend fun readRange(from: Instant, to: Instant): Result<RawHealthRecords> {
        events += "read"
        reads += from to to
        readFailure?.let { return Result.failure(it) }
        return Result.success(records)
    }

    override suspend fun readChanges(token: String): Result<ChangesResult> {
        changeReads += token
        return changes
    }

    override suspend fun writeWeighIn(
        rowId: String,
        weightKg: Double,
        bodyFatPct: Double?,
        measuredAt: Instant,
        version: Long,
    ): Result<Unit> {
        writeFailure?.let { return Result.failure(it) }
        writes += WeighInWrite(rowId, weightKg, bodyFatPct, measuredAt, version)
        return Result.success(Unit)
    }

    override suspend fun deleteCopy(rowId: String): Result<Unit> {
        deleteFailure?.let { return Result.failure(it) }
        deletedCopies += rowId
        return Result.success(Unit)
    }
}

internal class FakeHealthSyncRepository(var state: HealthSyncState? = null) : HealthSyncRepository {
    var importFailure: Throwable? = null
    var stateFailure: Throwable? = null
    val imports = mutableListOf<List<HealthReading>>()
    val savedTokens = mutableListOf<String?>()
    val syncedAts = mutableListOf<Instant>()
    private val known = mutableSetOf<String>()

    override suspend fun getState(): Result<HealthSyncState?> =
        stateFailure?.let { Result.failure(it) } ?: Result.success(state)

    override suspend fun importReadings(
        rows: List<HealthReading>,
        syncedAt: Instant,
        changesToken: String?,
    ): Result<Int> {
        importFailure?.let { return Result.failure(it) }
        imports += rows
        savedTokens += changesToken
        syncedAts += syncedAt
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

/** Records the calls the write-back makes to the body measurement repository. */
internal class FakeMeasurementsRepository(var dayRows: List<BodyMeasurement> = emptyList()) :
    BodyMeasurementRepository {
    var hcRecordFailure: Throwable? = null
    val hcRecordIds = mutableListOf<Pair<String, String?>>()

    override suspend fun getOnboardingWeight() = Result.success<BodyMeasurement?>(null)

    override suspend fun saveOnboardingWeight(weightKg: Double, measuredAt: Instant) = Result.success(Unit)

    override suspend fun getLatestWeight() = Result.success<BodyMeasurement?>(null)

    override suspend fun getMeasurements(from: Instant, toExclusive: Instant) =
        Result.success(dayRows.filter { it.measuredAt >= from && it.measuredAt < toExclusive })

    override suspend fun getLatestComposition() = Result.success(LatestComposition())

    override suspend fun saveManualWeight(save: ManualWeightSave): Result<String> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun deleteMeasurement(id: String) = Result.success(Unit)

    override suspend fun setHcRecordId(id: String, hcRecordId: String?): Result<Unit> {
        hcRecordFailure?.let { return Result.failure(it) }
        hcRecordIds += id to hcRecordId
        return Result.success(Unit)
    }
}
