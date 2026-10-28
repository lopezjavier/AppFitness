package io.github.lopezjavier.appfitness.feature.about

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeHealthDataSource
import io.github.lopezjavier.appfitness.app.FakeHealthHistoryProbe
import io.github.lopezjavier.appfitness.domain.model.HealthAvailability
import io.github.lopezjavier.appfitness.domain.model.HistoryProbeResult
import io.github.lopezjavier.appfitness.domain.model.HistoryWindows
import io.github.lopezjavier.appfitness.domain.model.WindowStats
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class HealthDiagnosticsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val source = FakeHealthDataSource()
    private val probe = FakeHealthHistoryProbe()
    private val savedState = SavedStateHandle()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(state: SavedStateHandle = savedState) = HealthDiagnosticsViewModel(source, probe, state)

    private fun HealthDiagnosticsViewModel.result() = assertIs<HealthDiagnosticsState.Result>(state.value).values

    @Test
    fun initialState_isIdle() {
        assertEquals(HealthDiagnosticsState.Idle, viewModel().state.value)
    }

    @Test
    fun check_requestsPermissionsAndRuns() = runTest(dispatcher) {
        val model = viewModel()

        model.onEvent(HealthDiagnosticsEvent.Check)
        advanceUntilIdle()

        assertEquals(HealthDiagnosticsState.Running, model.state.value)
        assertEquals(HealthDiagnosticsEffect.RequestPermissions, model.effects.first())
        assertEquals(0, probe.calls)
    }

    @Test
    fun permissionResult_showsTheProbeValues() = runTest(dispatcher) {
        val model = viewModel()

        model.onEvent(HealthDiagnosticsEvent.Check)
        model.onEvent(HealthDiagnosticsEvent.PermissionResult)
        advanceUntilIdle()

        assertEquals(probe.result, model.result())
    }

    @Test
    fun historyNotGranted_isReportedWithTheCountsReturned() = runTest(dispatcher) {
        probe.result = probe.result.copy(historyGranted = false)
        val model = viewModel()

        model.onEvent(HealthDiagnosticsEvent.Check)
        model.onEvent(HealthDiagnosticsEvent.PermissionResult)
        advanceUntilIdle()

        assertEquals(false, model.result().historyGranted)
        assertEquals(10, model.result().steps.recent.recordCount)
    }

    @Test
    fun featureUnavailable_isReportedWithTheCounts() = runTest(dispatcher) {
        probe.result = probe.result.copy(featureAvailable = false)
        val model = viewModel()

        model.onEvent(HealthDiagnosticsEvent.Check)
        model.onEvent(HealthDiagnosticsEvent.PermissionResult)
        advanceUntilIdle()

        assertEquals(false, model.result().featureAvailable)
        assertEquals(5, model.result().steps.old.recordCount)
    }

    @Test
    fun probeFailure_showsErrorWithoutCrashing() = runTest(dispatcher) {
        probe.failure = IllegalStateException("boom")
        val model = viewModel()

        model.onEvent(HealthDiagnosticsEvent.Check)
        model.onEvent(HealthDiagnosticsEvent.PermissionResult)
        advanceUntilIdle()

        assertEquals(HealthDiagnosticsState.Error, model.state.value)
    }

    @Test
    fun healthConnectMissing_showsUnavailableAndNeverProbes() = runTest(dispatcher) {
        for (availability in listOf(HealthAvailability.NotInstalled, HealthAvailability.UpdateRequired)) {
            source.availability = availability
            val model = viewModel(SavedStateHandle())

            model.onEvent(HealthDiagnosticsEvent.Check)
            advanceUntilIdle()

            assertEquals(HealthDiagnosticsState.Unavailable(availability), model.state.value)
        }
        assertEquals(0, probe.calls)
    }

    @Test
    fun permissionsDenied_probeReportsZeroCountsWithoutError() = runTest(dispatcher) {
        val empty = HistoryWindows(WindowStats.EMPTY, WindowStats.EMPTY)
        probe.result = HistoryProbeResult(true, false, empty, empty)
        val model = viewModel()

        model.onEvent(HealthDiagnosticsEvent.Check)
        model.onEvent(HealthDiagnosticsEvent.PermissionResult)
        advanceUntilIdle()

        assertEquals(probe.result, model.result())
    }

    @Test
    fun twoQuickTaps_runOneProbeAtATime() = runTest(dispatcher) {
        probe.gate = CompletableDeferred()
        val model = viewModel()

        model.onEvent(HealthDiagnosticsEvent.Check)
        model.onEvent(HealthDiagnosticsEvent.Check)
        model.onEvent(HealthDiagnosticsEvent.PermissionResult)
        advanceUntilIdle()
        model.onEvent(HealthDiagnosticsEvent.Check)
        model.onEvent(HealthDiagnosticsEvent.PermissionResult)
        probe.gate?.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, probe.calls)
        assertIs<HealthDiagnosticsState.Result>(model.state.value)
    }

    @Test
    fun noRecords_hasNullOldest() = runTest(dispatcher) {
        val model = viewModel()

        model.onEvent(HealthDiagnosticsEvent.Check)
        model.onEvent(HealthDiagnosticsEvent.PermissionResult)
        advanceUntilIdle()

        assertNull(model.result().weight.recent.oldest)
        assertEquals(Instant.parse("2026-09-01T00:00:00Z"), model.result().steps.recent.oldest)
    }

    @Test
    fun result_survivesRecreationFromTheSameHandle() = runTest(dispatcher) {
        val first = viewModel()
        first.onEvent(HealthDiagnosticsEvent.Check)
        first.onEvent(HealthDiagnosticsEvent.PermissionResult)
        advanceUntilIdle()

        val second = viewModel(savedState)

        assertEquals(probe.result, second.result())
    }

    @Test
    fun runningState_restoresAsIdle() = runTest(dispatcher) {
        probe.gate = CompletableDeferred()
        val first = viewModel()
        first.onEvent(HealthDiagnosticsEvent.Check)
        first.onEvent(HealthDiagnosticsEvent.PermissionResult)
        advanceUntilIdle()

        val second = viewModel(savedState)

        assertEquals(HealthDiagnosticsState.Idle, second.state.value)
    }
}
