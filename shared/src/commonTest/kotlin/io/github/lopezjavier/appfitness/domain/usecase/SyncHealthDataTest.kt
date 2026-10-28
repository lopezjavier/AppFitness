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
    private val sync =
        SyncHealthData(source, repository, SyncHealthMeasurements(source, repository, clock), clock, ZONE)

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
    fun firstSync_reads90Days_whenNoAutoSyncDayIsStored() = runTest {
        grantAll()

        sync().getOrThrow()

        assertEquals(listOf(NOW - 90.days to NOW), source.watchReads)
    }

    @Test
    fun laterSync_reads7Days_whenAnAutoSyncDayIsStored() = runTest {
        grantAll()
        repository.state = HealthSyncState(NOW - 1.days, null, LocalDate(2026, 10, 21))

        sync().getOrThrow()

        assertEquals(listOf(NOW - 7.days to NOW), source.watchReads)
    }

    @Test
    fun weightFailure_doesNotBlockTheWatchImport() = runTest {
        grantAll()
        source.tokenFailure = IllegalStateException("hc")
        source.watchBatch = HealthActivityBatch(listOf(steps), emptyList(), emptyList())

        val outcome = sync().getOrThrow()

        assertEquals(SyncOutcome.Synced(0, NOW, HealthActivityImportResult(1, 0, 0)), outcome)
    }

    @Test
    fun watchFailure_doesNotBlockTheWeightImport() = runTest {
        grantAll()
        source.records = RawHealthRecords(weights = listOf(HealthRecord("w1", NOW - 1.days, 73.9)))
        source.watchFailure = IllegalStateException("hc")

        val outcome = sync().getOrThrow()

        assertEquals(SyncOutcome.Synced(1, NOW), outcome)
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
