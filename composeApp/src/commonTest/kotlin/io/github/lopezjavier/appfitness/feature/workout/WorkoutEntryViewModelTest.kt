package io.github.lopezjavier.appfitness.feature.workout

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkoutEntryViewModelTest {
    private val repository = FakeWorkoutSessionRepository()
    private val connectivity = FakeConnectivityObserver()

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) =
        WorkoutEntryViewModel(repository, connectivity, savedState)

    private fun inProgress() = sessionOf(SessionExercise("e1", 0, null, listOf(pendingSet(1))))

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun startsCheckingBeforeTheLookupFinishes() = runTest {
        val vm = viewModel()

        assertEquals(WorkoutEntryState.Checking, vm.state.value)
    }

    @Test
    fun anInProgressSession_opensTheLiveScreen() = runTest {
        repository.session = sessionOf(SessionExercise("e1", 0, null, listOf(pendingSet(1))))
        val vm = viewModel()

        advanceUntilIdle()

        assertEquals(WorkoutEntryState.Live, vm.state.value)
    }

    @Test
    fun noSession_showsTheRoutines() = runTest {
        val vm = viewModel()

        advanceUntilIdle()

        assertEquals(WorkoutEntryState.Routines, vm.state.value)
    }

    @Test
    fun aFailedLookup_fallsBackToTheRoutines() = runTest {
        repository.getFailure = AppError.Network
        val vm = viewModel()

        advanceUntilIdle()

        assertEquals(WorkoutEntryState.Routines, vm.state.value)
    }

    @Test
    fun recheck_picksUpASessionStartedMeanwhile() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        repository.session = sessionOf(SessionExercise("e1", 0, null, listOf(pendingSet(1))))

        vm.recheck()
        advanceUntilIdle()

        assertEquals(WorkoutEntryState.Live, vm.state.value)
    }

    private suspend fun TestScope.liveViewModel(
        savedState: SavedStateHandle = SavedStateHandle(),
    ): WorkoutEntryViewModel {
        repository.session = inProgress()
        val vm = viewModel(savedState)
        advanceUntilIdle()
        return vm
    }

    @Test
    fun requestDiscard_asksForConfirmation_andDismissClosesIt() = runTest {
        val vm = liveViewModel()

        vm.onEvent(WorkoutEntryEvent.RequestDiscard)
        assertTrue(vm.discardAsked.value)
        vm.onEvent(WorkoutEntryEvent.DismissDiscard)

        assertFalse(vm.discardAsked.value)
        assertTrue(repository.discarded.isEmpty())
    }

    @Test
    fun requestDiscard_isIgnoredOffline() = runTest {
        val vm = liveViewModel()
        connectivity.state.value = false
        advanceUntilIdle()

        vm.onEvent(WorkoutEntryEvent.RequestDiscard)

        assertFalse(vm.discardAsked.value)
        assertFalse(vm.isOnline.value)
    }

    @Test
    fun confirmDiscard_discardsTheInProgressSession_andShowsTheRoutines() = runTest {
        val vm = liveViewModel()

        vm.onEvent(WorkoutEntryEvent.RequestDiscard)
        vm.onEvent(WorkoutEntryEvent.ConfirmDiscard)
        advanceUntilIdle()

        assertEquals(listOf(repository.session!!.id), repository.discarded)
        assertEquals(WorkoutEntryState.Routines, vm.state.value)
        assertFalse(vm.discardAsked.value)
        assertFalse(vm.discarding.value)
    }

    @Test
    fun confirmDiscard_failure_keepsTheCard_andReportsIt() = runTest {
        val vm = liveViewModel()
        repository.discardFailure = AppError.Network

        vm.effects.test {
            vm.onEvent(WorkoutEntryEvent.RequestDiscard)
            vm.onEvent(WorkoutEntryEvent.ConfirmDiscard)
            advanceUntilIdle()
            assertEquals(WorkoutEntryEffect.DiscardFailed, awaitItem())
        }

        assertEquals(WorkoutEntryState.Live, vm.state.value)
        assertFalse(vm.discardAsked.value)
        assertFalse(vm.discarding.value)
    }

    @Test
    fun confirmDiscard_isIgnoredOffline() = runTest {
        val vm = liveViewModel()
        vm.onEvent(WorkoutEntryEvent.RequestDiscard)
        connectivity.state.value = false
        advanceUntilIdle()

        vm.onEvent(WorkoutEntryEvent.ConfirmDiscard)
        advanceUntilIdle()

        assertTrue(repository.discarded.isEmpty())
        assertEquals(WorkoutEntryState.Live, vm.state.value)
    }

    @Test
    fun aDoubleConfirm_discardsOnce() = runTest {
        val vm = liveViewModel()
        vm.onEvent(WorkoutEntryEvent.RequestDiscard)

        vm.onEvent(WorkoutEntryEvent.ConfirmDiscard)
        vm.onEvent(WorkoutEntryEvent.ConfirmDiscard)
        advanceUntilIdle()

        assertEquals(1, repository.discarded.size)
    }

    @Test
    fun theOpenConfirmation_survivesRecreationFromTheSameSavedState() = runTest {
        val handle = SavedStateHandle()
        liveViewModel(handle).onEvent(WorkoutEntryEvent.RequestDiscard)

        val recreated = viewModel(handle)

        assertTrue(recreated.discardAsked.value)
    }
}
