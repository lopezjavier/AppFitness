package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.ChangesResult
import io.github.lopezjavier.appfitness.domain.model.HealthAccess
import io.github.lopezjavier.appfitness.domain.model.HealthAvailability
import io.github.lopezjavier.appfitness.domain.model.HealthMetric
import io.github.lopezjavier.appfitness.domain.model.HealthReading
import io.github.lopezjavier.appfitness.domain.model.HealthRecord
import io.github.lopezjavier.appfitness.domain.model.HealthSyncState
import io.github.lopezjavier.appfitness.domain.model.RawHealthRecords
import io.github.lopezjavier.appfitness.domain.model.SyncOutcome
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val NOW = Instant.parse("2026-10-22T09:00:00Z")
private val WEIGHED_AT = Instant.parse("2026-10-20T07:31:00Z")

class SyncHealthMeasurementsTest {
    private val source = FakeHealthDataSource()
    private val repository = FakeHealthSyncRepository()
    private val clock = FixedTestClock(NOW)
    private val sync = SyncHealthMeasurements(source, repository, clock)

    private fun withScaleReading() {
        source.records = RawHealthRecords(
            weights = listOf(HealthRecord("w1", WEIGHED_AT, 73.9)),
            fats = listOf(HealthRecord("f1", WEIGHED_AT, 15.1)),
        )
    }

    @Test
    fun firstSync_takesTheTokenBeforeReadingNinetyDays_andStoresIt() = runTest {
        withScaleReading()

        val outcome = sync().getOrThrow()

        assertEquals(listOf("token", "read"), source.events)
        assertEquals(listOf(NOW - 90.days to NOW), source.reads)
        assertEquals(SyncOutcome.Synced(1, NOW), outcome)
        assertEquals(listOf(HealthReading("w1", WEIGHED_AT, 73.9, bodyFatPct = 15.1)), repository.imports.single())
        assertEquals(HealthSyncState(NOW, "token-1"), repository.state)
    }

    @Test
    fun incrementalSync_readsOnlyTheChangedRangePlusTwoMinutes() = runTest {
        repository.state = HealthSyncState(NOW - 1.days, "stored")
        val changed = Instant.parse("2026-10-22T08:50:00Z")
        source.changes = Result.success(ChangesResult.Changed(changed, changed + 1.minutes, "ignored"))
        withScaleReading()

        sync().getOrThrow()

        assertEquals(listOf("stored"), source.changeReads)
        assertEquals(listOf(changed - 2.minutes to changed + 3.minutes), source.reads)
        assertEquals("token-1", repository.savedTokens.single())
    }

    @Test
    fun incrementalSync_withoutChanges_readsNothingButAdvancesTheState() = runTest {
        repository.state = HealthSyncState(NOW - 1.days, "stored")

        val outcome = sync().getOrThrow()

        assertTrue(source.reads.isEmpty())
        assertEquals(SyncOutcome.Synced(0, NOW), outcome)
        assertEquals(NOW, repository.state?.lastSyncedAt)
    }

    @Test
    fun syncingTheSameReadingsAgain_importsZero() = runTest {
        withScaleReading()

        sync().getOrThrow()
        repository.state = repository.state?.copy(changesToken = null)
        val second = sync().getOrThrow()

        assertEquals(SyncOutcome.Synced(0, NOW), second)
    }

    @Test
    fun expiredToken_fallsBackToTheNinetyDayRead() = runTest {
        repository.state = HealthSyncState(NOW - 40.days, "old")
        source.changes = Result.success(ChangesResult.TokenExpired)
        withScaleReading()

        val outcome = sync().getOrThrow()

        assertEquals(listOf(NOW - 90.days to NOW), source.reads)
        assertEquals(SyncOutcome.Synced(1, NOW), outcome)
    }

    @Test
    fun failedChangesRead_surfacesWithoutMovingTheState() = runTest {
        val before = HealthSyncState(NOW - 1.days, "stored")
        repository.state = before
        source.changes = Result.failure(AppException(AppError.Unknown))

        assertTrue(sync().isFailure)
        assertEquals(before, repository.state)
    }

    @Test
    fun withoutReadAccessToWeight_permissionIsNeeded_andNothingIsTouched() = runTest {
        source.access = HealthAccess(setOf(HealthMetric.BodyFat), canWrite = true)

        val outcome = sync().getOrThrow()

        assertEquals(SyncOutcome.PermissionNeeded, outcome)
        assertTrue(source.events.isEmpty())
        assertTrue(repository.imports.isEmpty())
    }

    @Test
    fun unavailableHealthConnect_isReported_andNothingIsTouched() = runTest {
        source.availability = HealthAvailability.UpdateRequired

        val outcome = sync().getOrThrow()

        assertEquals(SyncOutcome.Unavailable(HealthAvailability.UpdateRequired), outcome)
        assertTrue(source.events.isEmpty())
        assertTrue(repository.imports.isEmpty())
    }

    @Test
    fun repositoryFailure_surfacesWithoutMovingTheState() = runTest {
        withScaleReading()
        repository.importFailure = AppException(AppError.Network)

        val result = sync()

        assertEquals(AppError.Network, result.appErrorOrNull())
        assertEquals(null, repository.state)
    }

    @Test
    fun healthConnectReadFailure_surfaces_andImportsNothing() = runTest {
        source.readFailure = IllegalStateException("boom")

        assertTrue(sync().isFailure)
        assertTrue(repository.imports.isEmpty())
    }

    @Test
    fun stateReadFailure_surfaces() = runTest {
        repository.stateFailure = AppException(AppError.Network)

        assertEquals(AppError.Network, sync().appErrorOrNull())
        assertTrue(source.events.isEmpty())
    }
}
