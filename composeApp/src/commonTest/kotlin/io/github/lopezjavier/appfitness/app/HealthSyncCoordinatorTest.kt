package io.github.lopezjavier.appfitness.app

import app.cash.turbine.test
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.HealthAccess
import io.github.lopezjavier.appfitness.domain.model.HealthRecord
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.model.RawHealthRecords
import io.github.lopezjavier.appfitness.domain.model.SyncOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

private val DAY = LocalDate(2026, 10, 28)

@OptIn(ExperimentalCoroutinesApi::class)
class HealthSyncCoordinatorTest {
    private val source = FakeHealthDataSource()
    private val repository = FakeHealthSyncRepository()
    private val connectivity = FakeConnectivityObserver()
    private val clock = ReminderClock(DAY)

    private fun TestScope.coordinator(reminders: ReminderCoordinator = idleReminderCoordinator()) =
        healthSyncCoordinator(backgroundScope, source, repository, connectivity, clock, reminders)

    private fun aWeighIn() {
        source.records =
            RawHealthRecords(weights = listOf(HealthRecord("w1", Instant.parse("2026-10-27T08:00:00Z"), 74.0)))
    }

    private fun readsOf(source: FakeHealthDataSource) = source.watchReads + source.watchReads

    @Test
    fun coldStart_syncsAndStoresTheDay() = runTest {
        val sync = coordinator()

        sync.onAppStart()
        runCurrent()

        assertEquals(1, source.watchReads)
        assertEquals(listOf(DAY), repository.autoSyncedDays)
        assertEquals(AutoState.Idle, sync.lastAuto.value)
    }

    @Test
    fun secondStartTheSameDay_readsNothing() = runTest {
        val sync = coordinator()
        sync.onAppStart()
        runCurrent()
        val before = readsOf(source)

        sync.onAppStart()
        runCurrent()

        assertEquals(before, readsOf(source))
    }

    @Test
    fun startOnANewDay_syncsAgain() = runTest {
        val sync = coordinator()
        sync.onAppStart()
        runCurrent()

        clock.day = LocalDate(2026, 10, 29)
        sync.onAppStart()
        runCurrent()

        assertEquals(2, source.watchReads)
        assertEquals(listOf(DAY, LocalDate(2026, 10, 29)), repository.autoSyncedDays)
    }

    @Test
    fun failure_retriesOnTheNextOpenAndShowsFailedWhileOnline() = runTest {
        repository.importFailure = AppException(AppError.Network)
        repository.activityFailure = AppException(AppError.Network)
        val sync = coordinator()

        sync.onAppStart()
        runCurrent()
        assertEquals(AutoState.Failed, sync.lastAuto.value)
        assertTrue(repository.autoSyncedDays.isEmpty())

        repository.importFailure = null
        repository.activityFailure = null
        sync.onAppStart()
        runCurrent()
        assertEquals(AutoState.Idle, sync.lastAuto.value)
        assertEquals(listOf(DAY), repository.autoSyncedDays)
    }

    @Test
    fun offline_isNotPossibleAndReadsNothing() = runTest {
        connectivity.state.value = false
        val sync = coordinator()

        sync.onAppStart()
        runCurrent()

        assertEquals(AutoState.NotPossible, sync.lastAuto.value)
        assertEquals(0, readsOf(source))
    }

    @Test
    fun noPermission_isPassiveAndReadsNothing() = runTest {
        source.access = HealthAccess.NONE
        val sync = coordinator()

        sync.onAppStart()
        runCurrent()

        assertEquals(AutoState.PermissionNeeded, sync.lastAuto.value)
        assertEquals(0, readsOf(source))
    }

    @Test
    fun manualRunAfterTodaysAutoSync_readsAgain() = runTest {
        val sync = coordinator()
        sync.onAppStart()
        runCurrent()
        val before = source.watchReads

        val result = sync.run()

        assertIs<SyncOutcome.Synced>(result.getOrThrow())
        assertEquals(before + 1, source.watchReads)
    }

    @Test
    fun manualRunWhileTheAutoSyncRuns_joinsItWithASingleRead() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.importGate = gate
        val sync = coordinator()
        sync.onAppStart()
        runCurrent()
        assertTrue(sync.running.value)

        val manual = backgroundScope.async { sync.run() }
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        assertIs<SyncOutcome.Synced>(manual.await().getOrThrow())
        assertEquals(1, source.watchReads)
        assertEquals(1, source.watchReads)
        assertTrue(!sync.running.value)
    }

    @Test
    fun results_emitTheFinishedSync() = runTest {
        aWeighIn()
        val sync = coordinator()

        sync.results.test {
            sync.onAppStart()
            runCurrent()
            assertEquals(1, awaitItem().imported)
        }
    }

    private fun TestScope.reminderCoordinator(): ReminderCoordinator {
        val env = ReminderEnv(DAY).apply {
            weight.rows += BodyMeasurement(
                "w",
                LocalDate(2026, 10, 24).atStartOfDayIn(TimeZone.UTC),
                80.0,
                MeasurementSource.Manual,
            )
        }
        return ReminderCoordinator(env.load, backgroundScope)
    }

    @Test
    fun reminderRefresh_runsAfterAWeightImport() = runTest {
        aWeighIn()
        val reminders = reminderCoordinator()
        val sync = coordinator(reminders)

        sync.onAppStart()
        runCurrent()

        assertEquals(1, reminders.reminders.value.size)
    }

    @Test
    fun reminderRefresh_doesNotRunWhenNothingWasImported() = runTest {
        val reminders = reminderCoordinator()
        val sync = coordinator(reminders)

        sync.onAppStart()
        runCurrent()

        assertTrue(reminders.reminders.value.isEmpty())
    }

    @Test
    fun newInstance_afterProcessDeath_triggersAgainOnStart() = runTest {
        coordinator().apply {
            onAppStart()
            runCurrent()
        }
        // Same stored state but a fresh process: the stored day is today, so it is skipped, not re-run blindly.
        val reads = readsOf(source)
        coordinator().onAppStart()
        runCurrent()
        assertEquals(reads, readsOf(source))

        clock.day = LocalDate(2026, 10, 29)
        coordinator().onAppStart()
        runCurrent()
        assertEquals(2, source.watchReads)
    }
}
