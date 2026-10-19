package io.github.lopezjavier.appfitness.feature.workout

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkoutEntryViewModelTest {
    private val repository = FakeWorkoutSessionRepository()

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
        val vm = WorkoutEntryViewModel(repository)

        assertEquals(WorkoutEntryState.Checking, vm.state.value)
    }

    @Test
    fun anInProgressSession_opensTheLiveScreen() = runTest {
        repository.session = sessionOf(SessionExercise("e1", 0, null, listOf(pendingSet(1))))
        val vm = WorkoutEntryViewModel(repository)

        advanceUntilIdle()

        assertEquals(WorkoutEntryState.Live, vm.state.value)
    }

    @Test
    fun noSession_showsTheRoutines() = runTest {
        val vm = WorkoutEntryViewModel(repository)

        advanceUntilIdle()

        assertEquals(WorkoutEntryState.Routines, vm.state.value)
    }

    @Test
    fun aFailedLookup_fallsBackToTheRoutines() = runTest {
        repository.getFailure = AppError.Network
        val vm = WorkoutEntryViewModel(repository)

        advanceUntilIdle()

        assertEquals(WorkoutEntryState.Routines, vm.state.value)
    }

    @Test
    fun recheck_picksUpASessionStartedMeanwhile() = runTest {
        val vm = WorkoutEntryViewModel(repository)
        advanceUntilIdle()
        repository.session = sessionOf(SessionExercise("e1", 0, null, listOf(pendingSet(1))))

        vm.recheck()
        advanceUntilIdle()

        assertEquals(WorkoutEntryState.Live, vm.state.value)
    }
}
