package io.github.lopezjavier.appfitness.app

import io.github.lopezjavier.appfitness.domain.model.ChangesResult
import io.github.lopezjavier.appfitness.domain.model.HealthAccess
import io.github.lopezjavier.appfitness.domain.model.HealthActivityBatch
import io.github.lopezjavier.appfitness.domain.model.HealthActivityDay
import io.github.lopezjavier.appfitness.domain.model.HealthActivityHistory
import io.github.lopezjavier.appfitness.domain.model.HealthActivityImportResult
import io.github.lopezjavier.appfitness.domain.model.HealthAvailability
import io.github.lopezjavier.appfitness.domain.model.HealthMetric
import io.github.lopezjavier.appfitness.domain.model.HealthReading
import io.github.lopezjavier.appfitness.domain.model.HealthSyncState
import io.github.lopezjavier.appfitness.domain.model.RawHealthRecords
import io.github.lopezjavier.appfitness.domain.model.WatchMetric
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.port.HealthDataSource
import io.github.lopezjavier.appfitness.domain.repository.HealthActivityRepository
import io.github.lopezjavier.appfitness.domain.repository.HealthSyncRepository
import io.github.lopezjavier.appfitness.domain.usecase.ActivityDayValues
import io.github.lopezjavier.appfitness.domain.usecase.AutoSyncHealth
import io.github.lopezjavier.appfitness.domain.usecase.SyncHealthData
import io.github.lopezjavier.appfitness.domain.usecase.SyncHealthMeasurements
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
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
    var access = HealthAccess(HealthMetric.entries.toSet(), canWrite = true, watchRead = WatchMetric.entries.toSet())
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
        rangeReads++
        readFailure?.let { return Result.failure(it) }
        return Result.success(records)
    }

    var watchBatch = HealthActivityBatch(emptyList(), emptyList(), emptyList())
    var watchFailure: Throwable? = null
    var rangeReads = 0
    var watchReads = 0

    override suspend fun readWatchData(from: Instant, to: Instant, zone: TimeZone): Result<HealthActivityBatch> {
        watchReads++
        watchFailure?.let { return Result.failure(it) }
        return Result.success(watchBatch)
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
        state = HealthSyncState(syncedAt, changesToken, state?.lastAutoSyncDate)
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

/** Scriptable [HealthActivityRepository]: answers [history], or [failure] when set. */
internal class FakeHealthActivityRepository : HealthActivityRepository {
    var failure: Throwable? = null

    /** Answered by [history]; [day] is derived from it. [dayFailure] fails only [day]. */
    var history: HealthActivityHistory = HealthActivityHistory(emptyList(), emptyList(), emptyList())
    var dayFailure: Throwable? = null
    var zone: TimeZone = TimeZone.UTC
    var historyCalls = 0
        private set
    val dayCalls = mutableListOf<LocalDate>()

    override suspend fun history(from: LocalDate, toExclusive: LocalDate): Result<HealthActivityHistory> {
        historyCalls++
        return failure?.let { Result.failure(it) } ?: Result.success(history)
    }

    override suspend fun day(day: LocalDate): Result<HealthActivityDay> {
        dayCalls += day
        return (dayFailure ?: failure)?.let { Result.failure(it) }
            ?: Result.success(ActivityDayValues.of(history, day, zone))
    }
}

/** A coordinator over fake Health Connect data, for tests that only need the constructor argument. */
internal fun idleHealthSyncCoordinator(): HealthSyncCoordinator =
    healthSyncCoordinator(scope = CoroutineScope(SupervisorJob()))

/** Wires the real use cases over the fakes; [scope] runs the (single-flight) syncs. */
internal fun healthSyncCoordinator(
    scope: CoroutineScope,
    source: FakeHealthDataSource = FakeHealthDataSource(),
    repository: FakeHealthSyncRepository = FakeHealthSyncRepository(),
    connectivity: FakeConnectivityObserver = FakeConnectivityObserver(),
    clock: Clock = ReminderClock(LocalDate(2026, 10, 28)),
    reminders: ReminderCoordinator = idleReminderCoordinator(),
): HealthSyncCoordinator {
    val sync =
        SyncHealthData(source, repository, SyncHealthMeasurements(source, repository, clock), clock, TimeZone.UTC)
    val auto = AutoSyncHealth(source, repository, sync, connectivity, clock, TimeZone.UTC)
    return HealthSyncCoordinator(auto, sync, connectivity, reminders, scope)
}
