package io.github.lopezjavier.appfitness.feature.weight

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeHealthDataSource
import io.github.lopezjavier.appfitness.app.FakeHealthSyncRepository
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.HealthAccess
import io.github.lopezjavier.appfitness.domain.model.HealthAvailability
import io.github.lopezjavier.appfitness.domain.model.HealthMetric
import io.github.lopezjavier.appfitness.domain.model.HealthRecord
import io.github.lopezjavier.appfitness.domain.model.HealthSyncState
import io.github.lopezjavier.appfitness.domain.model.RawHealthRecords
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.usecase.SyncHealthMeasurements
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // One method per Gherkin scenario and rule.
class HealthSyncViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val start = Instant.parse("2026-10-22T10:00:00Z")
    private val clock = object : Clock {
        var at = start

        override fun today(): LocalDate = LocalDate(2026, 10, 22)

        override fun now(): Instant = at
    }
    private val source = FakeHealthDataSource()
    private val repository = FakeHealthSyncRepository()
    private val connectivity = FakeConnectivityObserver()
    private val savedState = SavedStateHandle()
    private val ticks = Channel<Unit>()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(state: SavedStateHandle = savedState) = HealthSyncViewModel(
        source = source,
        syncHealthMeasurements = SyncHealthMeasurements(source, repository, clock),
        repository = repository,
        connectivity = connectivity,
        clock = clock,
        savedState = state,
        tick = { ticks.receive() },
    )

    private fun HealthSyncViewModel.status() = uiState.value.status

    private fun withoutAccess() {
        source.access = HealthAccess.NONE
    }

    private fun aWeighIn() {
        source.records = RawHealthRecords(weights = listOf(HealthRecord("w1", start - 5.minutes, 73.9)))
    }

    // -- First sync and permissions -------------------------------------------------------------------------------

    @Test
    fun neverSynced_showsNeverSyncedStatus() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        assertEquals(HealthSyncStatus.NeverSynced, model.status())
    }

    @Test
    fun tapWithoutReadAccess_asksForPermissions() = runTest(dispatcher) {
        withoutAccess()
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(HealthSyncEvent.Tap)
            assertEquals(HealthSyncEffect.RequestPermissions, awaitItem())
        }
        assertTrue(repository.imports.isEmpty())
    }

    @Test
    fun permissionGranted_syncsAndShowsSyncedNow() = runTest(dispatcher) {
        withoutAccess()
        aWeighIn()
        val model = viewModel()
        advanceUntilIdle()
        source.access = HealthAccess(HealthMetric.entries.toSet(), canWrite = true)

        model.effects.test {
            model.onEvent(HealthSyncEvent.PermissionResult(granted = true))
            advanceUntilIdle()
            assertEquals(HealthSyncEffect.Synced(imported = 1), awaitItem())
        }
        assertEquals(HealthSyncStatus.Synced(start, SyncAge.Now), model.status())
    }

    @Test
    fun syncingState_isShownWhileTheImportRuns() = runTest(dispatcher) {
        aWeighIn()
        val model = viewModel()
        advanceUntilIdle()

        val gate = CompletableDeferred<Unit>()
        repository.importGate = gate

        model.onEvent(HealthSyncEvent.Tap)
        advanceUntilIdle()
        assertEquals(HealthSyncStatus.Syncing, model.status())

        gate.complete(Unit)
        advanceUntilIdle()
        assertIs<HealthSyncStatus.Synced>(model.status())
    }

    // -- Permissions denied -----------------------------------------------------------------------------------------

    @Test
    fun permissionDenied_showsPermissionNeededAndImportsNothing() = runTest(dispatcher) {
        withoutAccess()
        aWeighIn()
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(HealthSyncEvent.PermissionResult(granted = false))
        advanceUntilIdle()

        assertEquals(HealthSyncStatus.PermissionNeeded, model.status())
        assertTrue(repository.imports.isEmpty())
    }

    @Test
    fun permissionDenied_isKeptWhenTheViewModelIsRecreated() = runTest(dispatcher) {
        withoutAccess()
        val first = viewModel()
        advanceUntilIdle()
        first.onEvent(HealthSyncEvent.PermissionResult(granted = false))
        advanceUntilIdle()

        val recreated = viewModel(savedState)
        advanceUntilIdle()

        assertEquals(HealthSyncStatus.PermissionNeeded, recreated.status())
    }

    @Test
    fun previousSyncWithReadAccessGone_showsPermissionNeeded() = runTest(dispatcher) {
        repository.state = HealthSyncState(start - 3.minutes, "token")
        withoutAccess()
        val model = viewModel()
        advanceUntilIdle()

        assertEquals(HealthSyncStatus.PermissionNeeded, model.status())
    }

    @Test
    fun grantingTheAccessLater_clearsThePermissionNeededState() = runTest(dispatcher) {
        withoutAccess()
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(HealthSyncEvent.PermissionResult(granted = false))
        advanceUntilIdle()
        source.access = HealthAccess(HealthMetric.entries.toSet(), canWrite = true)

        model.onEvent(HealthSyncEvent.Refresh)
        advanceUntilIdle()

        assertEquals(HealthSyncStatus.NeverSynced, model.status())
    }

    // -- Health Connect unavailable ---------------------------------------------------------------------------------

    @Test
    fun notInstalled_offersTheInstallOnTap() = runTest(dispatcher) {
        source.availability = HealthAvailability.NotInstalled
        val model = viewModel()
        advanceUntilIdle()

        assertEquals(HealthSyncStatus.Unavailable(HealthAvailability.NotInstalled), model.status())
        model.effects.test {
            model.onEvent(HealthSyncEvent.Tap)
            assertEquals(HealthSyncEffect.OpenInstall, awaitItem())
        }
    }

    @Test
    fun updateRequired_offersTheUpdateOnTap() = runTest(dispatcher) {
        source.availability = HealthAvailability.UpdateRequired
        val model = viewModel()
        advanceUntilIdle()

        assertEquals(HealthSyncStatus.Unavailable(HealthAvailability.UpdateRequired), model.status())
        model.effects.test {
            model.onEvent(HealthSyncEvent.Tap)
            assertEquals(HealthSyncEffect.OpenInstall, awaitItem())
        }
    }

    // -- Last sync status -------------------------------------------------------------------------------------------

    @Test
    fun lastSyncTwelveMinutesAgo_showsMinutes() = runTest(dispatcher) {
        repository.state = HealthSyncState(start - 12.minutes, "token")
        val model = viewModel()
        advanceUntilIdle()

        assertEquals(SyncAge.Minutes(12), (model.status() as HealthSyncStatus.Synced).age)
    }

    @Test
    fun ticker_movesNowToOneMinute() = runTest(dispatcher) {
        repository.state = HealthSyncState(start, "token")
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(SyncAge.Now, (model.status() as HealthSyncStatus.Synced).age)

        clock.at = start + 1.minutes
        ticks.send(Unit)
        advanceUntilIdle()

        assertEquals(SyncAge.Minutes(1), (model.status() as HealthSyncStatus.Synced).age)
    }

    // -- Offline and failures ---------------------------------------------------------------------------------------

    @Test
    fun offline_tapDoesNothing() = runTest(dispatcher) {
        aWeighIn()
        val model = viewModel()
        advanceUntilIdle()
        connectivity.state.value = false
        advanceUntilIdle()

        model.onEvent(HealthSyncEvent.Tap)
        advanceUntilIdle()

        assertEquals(false, model.uiState.value.isOnline)
        assertEquals(HealthSyncStatus.NeverSynced, model.status())
        assertTrue(repository.imports.isEmpty())
    }

    @Test
    fun failedImport_showsFailedAndRetryingSucceeds() = runTest(dispatcher) {
        aWeighIn()
        repository.importFailure = AppException(AppError.Network)
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(HealthSyncEvent.Tap)
        advanceUntilIdle()
        assertEquals(HealthSyncStatus.Failed, model.status())

        repository.importFailure = null
        model.onEvent(HealthSyncEvent.Tap)
        advanceUntilIdle()
        assertIs<HealthSyncStatus.Synced>(model.status())
    }

    @Test
    fun unreadableStoredState_showsFailed() = runTest(dispatcher) {
        repository.stateFailure = AppException(AppError.Network)
        val model = viewModel()
        advanceUntilIdle()

        assertEquals(HealthSyncStatus.Failed, model.status())
    }
}
