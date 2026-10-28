package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.HealthAccess
import io.github.lopezjavier.appfitness.domain.model.HealthAvailability
import io.github.lopezjavier.appfitness.domain.model.HealthSyncState
import io.github.lopezjavier.appfitness.domain.model.WatchMetric
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

private val NOW = Instant.parse("2026-10-22T09:00:00Z")
private val TODAY = LocalDate(2026, 10, 22)
private val ZONE = TimeZone.of("Europe/Madrid")

private class AutoSyncConnectivity(var online: Boolean = true) : ConnectivityObserver {
    override val isOnline: Flow<Boolean> get() = flowOf(online)
}

class AutoSyncHealthTest {
    private val source = FakeHealthDataSource().apply {
        access = HealthAccess(ALL_READ, canWrite = true, watchRead = WatchMetric.entries.toSet())
    }
    private val repository = FakeHealthSyncRepository(HealthSyncState(NOW, null, LocalDate(2026, 10, 21)))
    private val connectivity = AutoSyncConnectivity()
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
    private val auto = AutoSyncHealth(source, repository, sync, connectivity, clock, ZONE)

    @Test
    fun dueAutoSync_seedsTheGrantedWatchTypes() = runTest {
        auto()

        assertEquals(WatchMetric.entries.toSet(), seedStore.seeded)
    }

    @Test
    fun dueAndSuccessful_marksToday() = runTest {
        assertIs<AutoSyncResult.Done>(auto())

        assertEquals(listOf(TODAY), repository.autoSyncedDays)
    }

    @Test
    fun alreadySyncedToday_isSkippedWithoutReading() = runTest {
        repository.state = HealthSyncState(NOW, null, TODAY)

        assertEquals(AutoSyncResult.Skipped, auto())
        assertTrue(source.events.isEmpty())
    }

    @Test
    fun offline_isNotPossibleAndDoesNotMark() = runTest {
        connectivity.online = false

        assertEquals(AutoSyncResult.NotPossible, auto())
        assertTrue(source.events.isEmpty())
        assertTrue(repository.autoSyncedDays.isEmpty())
    }

    @Test
    fun failure_isNotPossibleAndDoesNotMark() = runTest {
        source.tokenFailure = IllegalStateException("weight")
        source.watchFailure = IllegalStateException("watch")

        assertEquals(AutoSyncResult.NotPossible, auto())
        assertTrue(repository.autoSyncedDays.isEmpty())
    }

    @Test
    fun partialFailure_isDoneButDoesNotMark() = runTest {
        source.watchFailure = IllegalStateException("watch")

        assertIs<AutoSyncResult.Done>(auto())
        assertTrue(repository.autoSyncedDays.isEmpty())
    }

    @Test
    fun markFailure_keepsDone() = runTest {
        repository.markFailure = IllegalStateException("rpc")

        assertIs<AutoSyncResult.Done>(auto())
    }

    @Test
    fun noPermission_needsPermissionWithZeroReads() = runTest {
        source.access = HealthAccess.NONE

        assertEquals(AutoSyncResult.PermissionNeeded, auto())
        assertTrue(source.events.isEmpty())
    }

    @Test
    fun unavailable_isReported() = runTest {
        source.availability = HealthAvailability.NotInstalled

        assertEquals(AutoSyncResult.Unavailable, auto())
    }
}
