package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.DailyActivity
import io.github.lopezjavier.appfitness.domain.model.HealthAccess
import io.github.lopezjavier.appfitness.domain.model.HealthActivityBatch
import io.github.lopezjavier.appfitness.domain.model.HealthActivityImportResult
import io.github.lopezjavier.appfitness.domain.model.HealthAvailability
import io.github.lopezjavier.appfitness.domain.model.HealthMetric
import io.github.lopezjavier.appfitness.domain.model.HealthRecord
import io.github.lopezjavier.appfitness.domain.model.HealthSyncState
import io.github.lopezjavier.appfitness.domain.model.RawHealthRecords
import io.github.lopezjavier.appfitness.domain.model.SleepSession
import io.github.lopezjavier.appfitness.domain.model.SyncOutcome
import io.github.lopezjavier.appfitness.domain.model.SyncPart
import io.github.lopezjavier.appfitness.domain.model.WatchMetric
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

private val NOW = Instant.parse("2026-10-22T09:00:00Z")
private val ALL_WATCH = WatchMetric.entries.toSet()
private val ZONE = TimeZone.of("Europe/Madrid")

class SyncHealthDataTest {
    private val source = FakeHealthDataSource()
    private val repository = FakeHealthSyncRepository()
    private val clock = FixedTestClock(NOW)
    private val seedStore = InMemoryWatchSeedStore()
    private val sync = SyncHealthData(
        source,
        repository,
        SyncHealthMeasurements(source, repository, clock),
        seedStore,
        clock,
        ZONE,
    )

    private val steps = DailyActivity(LocalDate(2026, 10, 21), 8500, null, null)
    private val sleep = SleepSession(
        "s1",
        Instant.parse("2026-10-21T21:00:00Z"),
        Instant.parse("2026-10-22T05:00:00Z"),
        435,
        emptyList(),
    )

    private fun grantAll() {
        source.access = HealthAccess(ALL_READ, canWrite = true, watchRead = ALL_WATCH)
    }

    @Test
    fun allTypesGranted_importsWeightAndWatchData() = runTest {
        grantAll()
        source.records = RawHealthRecords(weights = listOf(HealthRecord("w1", NOW - 1.days, 73.9)))
        source.watchBatch = HealthActivityBatch(listOf(steps), listOf(sleep), emptyList())

        val outcome = sync().getOrThrow()

        assertEquals(SyncOutcome.Synced(1, NOW, HealthActivityImportResult(1, 1, 0)), outcome)
        assertEquals(listOf(source.watchBatch), repository.activityImports)
    }

    @Test
    fun partialPermission_weightMissingButStepsGranted_stillImportsWatchData() = runTest {
        source.access = HealthAccess(emptySet(), canWrite = false, watchRead = setOf(WatchMetric.Steps))
        source.watchBatch = HealthActivityBatch(listOf(steps), emptyList(), emptyList())

        val outcome = sync().getOrThrow()

        assertEquals(SyncOutcome.Synced(0, NOW, HealthActivityImportResult(1, 0, 0)), outcome)
        assertTrue(source.reads.isEmpty())
    }

    @Test
    fun onlyWeightGranted_neverReadsWatchData() = runTest {
        source.access = HealthAccess(setOf(HealthMetric.Weight), canWrite = true)

        val outcome = sync().getOrThrow()

        assertEquals(SyncOutcome.Synced(0, NOW), outcome)
        assertTrue(source.watchReads.isEmpty())
        assertTrue(repository.activityImports.isEmpty())
    }

    @Test
    fun nothingGranted_needsPermission() = runTest {
        source.access = HealthAccess.NONE

        assertEquals(SyncOutcome.PermissionNeeded, sync().getOrThrow())
        assertTrue(source.events.isEmpty())
    }

    @Test
    fun unavailable_isReportedWithoutReading() = runTest {
        source.availability = HealthAvailability.NotInstalled

        assertEquals(SyncOutcome.Unavailable(HealthAvailability.NotInstalled), sync().getOrThrow())
        assertTrue(source.events.isEmpty())
    }

    @Test
    fun typeTheWatchDoesNotProvide_sendsEmptyArrays_withoutError() = runTest {
        grantAll()
        source.watchBatch = HealthActivityBatch(listOf(steps), emptyList(), emptyList())

        sync().getOrThrow()

        val sent = repository.activityImports.single()
        assertTrue(sent.sleep.isEmpty() && sent.restingHeartRate.isEmpty())
    }

    @Test
    fun secondSyncWithTheSameData_sendsTheSameBatch() = runTest {
        grantAll()
        source.watchBatch = HealthActivityBatch(listOf(steps), listOf(sleep), emptyList())

        sync().getOrThrow()
        sync().getOrThrow()

        assertEquals(2, repository.activityImports.size)
        assertEquals(repository.activityImports[0], repository.activityImports[1])
    }

    @Test
    fun outOfRangeValues_areSanitizedBeforeSending() = runTest {
        grantAll()
        source.watchBatch = HealthActivityBatch(listOf(steps.copy(steps = 2_000_000)), emptyList(), emptyList())

        sync().getOrThrow()

        assertEquals(1_000_000, repository.activityImports.single().daily.single().steps)
    }

    @Test
    fun firstSync_reads90Days_whenNothingIsSeeded() = runTest {
        grantAll()

        sync().getOrThrow()

        assertEquals(listOf(NOW - 90.days to NOW), source.watchReads)
    }

    @Test
    fun laterSync_reads7Days_whenEveryGrantedTypeIsSeeded() = runTest {
        grantAll()
        val seeded = InMemoryWatchSeedStore(ALL_WATCH)

        newSync(seeded)().getOrThrow()

        assertEquals(listOf(NOW - 7.days to NOW), source.watchReads)
    }

    @Test
    fun typeGrantedAfterTheAutoSyncDayWasStored_reads90DaysWhileSeededTypesRead7() = runTest {
        // Weight and steps were granted first and an automatic sync stored the day; sleep was granted later.
        source.access = HealthAccess(
            ALL_READ,
            canWrite = true,
            watchRead = setOf(WatchMetric.Steps, WatchMetric.Sleep),
        )
        repository.state = HealthSyncState(NOW - 1.days, null, LocalDate(2026, 10, 21))
        val seeded = InMemoryWatchSeedStore(setOf(WatchMetric.Steps))

        newSync(seeded)().getOrThrow()

        assertEquals(
            mapOf(WatchMetric.Steps to NOW - 7.days, WatchMetric.Sleep to NOW - 90.days),
            source.watchPlans.single(),
        )
    }

    @Test
    fun weightOnlyAutoSyncDay_thenSleepGranted_readsSleep90Days() = runTest {
        source.access = HealthAccess(setOf(HealthMetric.Weight), canWrite = true)
        repository.state = HealthSyncState(NOW - 1.days, null, LocalDate(2026, 10, 21))
        sync().getOrThrow()
        source.access = HealthAccess(ALL_READ, canWrite = true, watchRead = setOf(WatchMetric.Sleep))

        sync().getOrThrow()

        assertEquals(mapOf(WatchMetric.Sleep to NOW - 90.days), source.watchPlans.single())
    }

    @Test
    fun successfulImport_seedsExactlyTheGrantedTypes() = runTest {
        source.access = HealthAccess(ALL_READ, canWrite = true, watchRead = setOf(WatchMetric.Steps))

        sync().getOrThrow()

        assertEquals(setOf(WatchMetric.Steps), seedStore.seeded)
    }

    @Test
    fun failedImport_doesNotSeed_soTheNextSyncReads90DaysAgain() = runTest {
        grantAll()
        repository.activityFailure = AppException(AppError.Network)

        sync()
        repository.activityFailure = null
        sync().getOrThrow()

        assertEquals(listOf(NOW - 90.days to NOW, NOW - 90.days to NOW), source.watchReads)
        assertEquals(ALL_WATCH, seedStore.seeded)
    }

    @Test
    fun failedRead_doesNotSeed() = runTest {
        grantAll()
        source.watchFailure = IllegalStateException("hc")

        sync()

        assertTrue(seedStore.seeded.isEmpty())
    }

    @Test
    fun fallbackTo30Days_doesNotSeed_soTheNextSyncTries90DaysAgain() = runTest {
        grantAll()
        source.failFirstWatchReads = 1

        sync().getOrThrow()

        assertTrue(seedStore.seeded.isEmpty())
    }

    @Test
    fun secondSync_afterSeeding_reads7Days() = runTest {
        grantAll()

        sync().getOrThrow()
        sync().getOrThrow()

        assertEquals(listOf(NOW - 90.days to NOW, NOW - 7.days to NOW), source.watchReads)
    }

    private fun newSync(store: InMemoryWatchSeedStore) = SyncHealthData(
        source,
        repository,
        SyncHealthMeasurements(source, repository, clock),
        store,
        clock,
        ZONE,
    )

    @Test
    fun weightFailure_doesNotBlockTheWatchImport() = runTest {
        grantAll()
        source.tokenFailure = IllegalStateException("hc")
        source.watchBatch = HealthActivityBatch(listOf(steps), emptyList(), emptyList())

        val outcome = sync().getOrThrow()

        assertEquals(
            SyncOutcome.Synced(0, NOW, HealthActivityImportResult(1, 0, 0), SyncPart.Weight),
            outcome,
        )
    }

    @Test
    fun watchFailure_doesNotBlockTheWeightImport() = runTest {
        grantAll()
        source.records = RawHealthRecords(weights = listOf(HealthRecord("w1", NOW - 1.days, 73.9)))
        source.watchFailure = IllegalStateException("hc")

        val outcome = sync().getOrThrow()

        assertEquals(SyncOutcome.Synced(1, NOW, null, SyncPart.Watch), outcome)
    }

    @Test
    fun firstWindowReadThrowing_retriesOnceWith30Days() = runTest {
        grantAll()
        source.failFirstWatchReads = 1
        source.watchBatch = HealthActivityBatch(listOf(steps), emptyList(), emptyList())

        val outcome = sync().getOrThrow()

        assertEquals(listOf(NOW - 90.days to NOW, NOW - 30.days to NOW), source.watchReads)
        assertEquals(null, (outcome as SyncOutcome.Synced).failedPart)
        assertEquals(1, repository.activityImports.size)
    }

    @Test
    fun bothFirstWindowReadsThrowing_failTheWatchPart() = runTest {
        grantAll()
        source.failFirstWatchReads = 2
        source.records = RawHealthRecords(weights = listOf(HealthRecord("w1", NOW - 1.days, 73.9)))

        val outcome = sync().getOrThrow()

        assertEquals(2, source.watchReads.size)
        assertEquals(SyncOutcome.Synced(1, NOW, null, SyncPart.Watch), outcome)
    }

    @Test
    fun laterWindowReadThrowing_hasNoFallback() = runTest {
        grantAll()
        source.failFirstWatchReads = 1

        newSync(InMemoryWatchSeedStore(ALL_WATCH))()

        assertEquals(listOf(NOW - 7.days to NOW), source.watchReads)
    }

    @Test
    fun bothPartsFailing_returnsTheFirstFailure() = runTest {
        grantAll()
        source.tokenFailure = IllegalStateException("weight")
        source.watchFailure = IllegalStateException("watch")

        val result = sync()

        assertEquals("weight", result.exceptionOrNull()?.message)
    }

    @Test
    fun repositoryFailureOnWatchImport_surfacesWhenNothingElseSynced() = runTest {
        source.access = HealthAccess(emptySet(), canWrite = false, watchRead = ALL_WATCH)
        repository.activityFailure = AppException(AppError.Network)

        val result = sync()

        assertEquals(AppError.Network, result.appErrorOrNull())
    }
}
