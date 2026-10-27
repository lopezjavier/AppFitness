package io.github.lopezjavier.appfitness.feature.routines

import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.feature.workout.FakeWorkoutSessionRepository
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoutinesViewModelTest {
    private val repository =
        FakeRoutineRepository(
            listOf(routineOf("r1", "Torso", "a", "b"), routineOf("r2", "Pierna", "c")),
        )
    private val sessions = FakeWorkoutSessionRepository()
    private val connectivity = FakeConnectivityObserver()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = RoutinesViewModel(repository, sessions, connectivity)

    private fun RoutinesViewModel.content() = assertIs<RoutinesUiState.Content>(uiState.value)

    @Test
    fun startsLoading_thenListsTheRoutinesWithTheirExerciseCount() = runTest {
        val vm = viewModel()
        assertEquals(RoutinesUiState.Loading, vm.uiState.value)

        advanceUntilIdle()

        assertEquals(listOf("Torso" to 2, "Pierna" to 1), vm.content().routines.map { it.name to it.exerciseCount })
    }

    @Test
    fun noRoutines_isAnEmptyContent() = runTest {
        repository.routines.clear()
        val vm = viewModel()

        advanceUntilIdle()

        assertTrue(vm.content().isEmpty)
    }

    @Test
    fun loadFailure_showsTheError_andRetryRecovers() = runTest {
        repository.failure = AppError.Network
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(RoutinesUiState.Error, vm.uiState.value)

        repository.failure = null
        vm.onEvent(RoutinesEvent.Retry)
        advanceUntilIdle()

        assertEquals(2, vm.content().routines.size)
    }

    @Test
    fun refresh_reloadsWithoutShowingTheSkeleton() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        repository.routines += routineOf("r3", "Empuje", "a")

        vm.onEvent(RoutinesEvent.Refresh)

        assertIs<RoutinesUiState.Content>(vm.uiState.value)
        advanceUntilIdle()
        assertEquals(listOf("Torso", "Pierna", "Empuje"), vm.content().routines.map { it.name })
    }

    @Test
    fun refresh_whileTheFirstLoadIsRunning_doesNotLoadTwice() = runTest {
        val vm = viewModel()

        vm.onEvent(RoutinesEvent.Refresh)
        advanceUntilIdle()

        assertEquals(1, repository.listCalls)
    }

    @Test
    fun newRoutine_andEdit_openTheBuilder() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.effects.test {
            vm.onEvent(RoutinesEvent.NewRoutine)
            vm.onEvent(RoutinesEvent.Edit("r1"))

            assertEquals(RoutinesEffect.OpenBuilder(null), awaitItem())
            assertEquals(RoutinesEffect.OpenBuilder("r1"), awaitItem())
        }
    }

    @Test
    fun duplicate_callsTheRepository_andRefreshesTheList() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onEvent(RoutinesEvent.Duplicate("r1"))
        advanceUntilIdle()

        assertEquals(listOf("r1"), repository.duplicated)
        assertEquals(listOf("Torso", "Pierna", "Torso (copia)"), vm.content().routines.map { it.name })
        assertNull(vm.content().menuFor)
    }

    @Test
    fun aCreatedRoutine_appearsAtTheEnd() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        repository.routines += routineOf("r3", "Brazos", "a")

        vm.onEvent(RoutinesEvent.Refresh)
        advanceUntilIdle()

        assertEquals(listOf("Torso", "Pierna", "Brazos"), vm.content().routines.map { it.name })
    }

    @Test
    fun duplicateFailure_keepsTheList_andSendsAMessage() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        repository.saveFailure = AppError.Network

        vm.effects.test {
            vm.onEvent(RoutinesEvent.Duplicate("r1"))
            advanceUntilIdle()

            assertEquals(RoutinesEffect.ActionFailed, awaitItem())
        }
        assertEquals(2, vm.content().routines.size)
    }

    @Test
    fun delete_removesTheRoutineAtOnce_beforeTheServerAnswers() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onEvent(RoutinesEvent.Delete("r1"))

        assertEquals(listOf("Pierna"), vm.content().routines.map { it.name })
        assertTrue(repository.deletedIds.isEmpty())
        advanceUntilIdle()
        assertEquals(listOf("r1"), repository.deletedIds)
        assertEquals(listOf("Pierna"), vm.content().routines.map { it.name })
    }

    @Test
    fun delete_failure_restoresTheListAndSendsAMessage() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        repository.saveFailure = AppError.Network

        vm.effects.test {
            vm.onEvent(RoutinesEvent.Delete("r1"))
            advanceUntilIdle()

            assertEquals(RoutinesEffect.ActionFailed, awaitItem())
        }
        assertEquals(listOf("Torso", "Pierna"), vm.content().routines.map { it.name })
    }

    @Test
    fun delete_offline_isRefused_andKeepsTheRoutine() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        connectivity.state.value = false
        advanceUntilIdle()

        vm.effects.test {
            vm.onEvent(RoutinesEvent.Delete("r1"))
            advanceUntilIdle()

            assertEquals(RoutinesEffect.OfflineRefused, awaitItem())
        }
        assertTrue(repository.deletedIds.isEmpty())
        assertEquals(2, vm.content().routines.size)
    }

    @Test
    fun delete_closesTheOpenMenu() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEvent(RoutinesEvent.OpenMenu("r1"))

        vm.onEvent(RoutinesEvent.Delete("r1"))

        assertNull(vm.content().menuFor)
    }

    @Test
    fun openDetails_sendsTheDetailsEffect() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.effects.test {
            vm.onEvent(RoutinesEvent.OpenDetails("r2"))

            assertEquals(RoutinesEffect.OpenDetails("r2"), awaitItem())
        }
    }

    @Test
    fun menu_opensAndCloses() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onEvent(RoutinesEvent.OpenMenu("r2"))
        assertEquals("r2", vm.content().menuFor?.id)

        vm.onEvent(RoutinesEvent.DismissMenu)
        assertNull(vm.content().menuFor)
    }

    @Test
    fun offline_rejectsDuplicate_butStillAllowsEditing() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        connectivity.state.value = false
        advanceUntilIdle()
        assertEquals(false, vm.content().isOnline)

        vm.onEvent(RoutinesEvent.Duplicate("r1"))
        advanceUntilIdle()

        assertTrue(repository.duplicated.isEmpty())
        vm.effects.test {
            vm.onEvent(RoutinesEvent.Edit("r1"))
            assertEquals(RoutinesEffect.OpenBuilder("r1"), awaitItem())
        }
    }

    @Test
    fun startRoutine_createsTheSession_andOpensTheLiveScreen() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.effects.test {
            vm.onEvent(RoutinesEvent.StartRoutine("r1"))
            advanceUntilIdle()
            assertEquals(RoutinesEffect.OpenLiveSession, awaitItem())
        }

        assertEquals(listOf("r1"), sessions.startedRoutines)
        assertEquals(false, vm.content().starting)
    }

    @Test
    fun start_isIgnoredOffline() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        connectivity.state.value = false
        advanceUntilIdle()

        vm.onEvent(RoutinesEvent.StartRoutine("r1"))
        advanceUntilIdle()

        assertTrue(sessions.startedRoutines.isEmpty())
    }

    @Test
    fun startFailure_reportsAMessage_andAllowsTryingAgain() = runTest {
        sessions.startFailure = AppError.Network
        val vm = viewModel()
        advanceUntilIdle()

        vm.effects.test {
            vm.onEvent(RoutinesEvent.StartRoutine("r1"))
            advanceUntilIdle()
            assertEquals(RoutinesEffect.StartFailed, awaitItem())
        }

        assertEquals(false, vm.content().starting)
    }

    @Test
    fun startWhileASessionIsInProgress_reportsItInsteadOfAGenericFailure() = runTest {
        sessions.startFailure = AppError.SessionAlreadyInProgress
        val vm = viewModel()
        advanceUntilIdle()

        vm.effects.test {
            vm.onEvent(RoutinesEvent.StartRoutine("r1"))
            advanceUntilIdle()
            assertEquals(RoutinesEffect.SessionInProgress, awaitItem())
        }

        assertEquals(false, vm.content().starting)
    }

    @Test
    fun aSecondStartWhileOneRuns_isIgnored() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onEvent(RoutinesEvent.StartRoutine("r1"))
        vm.onEvent(RoutinesEvent.StartRoutine("r2"))
        advanceUntilIdle()

        assertEquals(listOf("r1"), sessions.startedRoutines)
    }
}
