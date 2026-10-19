package io.github.lopezjavier.appfitness.feature.routines

import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
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
    private val connectivity = FakeConnectivityObserver()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = RoutinesViewModel(repository, connectivity)

    private fun RoutinesViewModel.content() = assertIs<RoutinesUiState.Content>(uiState.value)

    @Test
    fun startsLoading_thenListsTheRoutinesWithTheirExerciseCount() = runTest {
        val vm = viewModel()
        assertEquals(RoutinesUiState.Loading, vm.uiState.value)

        advanceUntilIdle()

        assertEquals(listOf("Pierna" to 1, "Torso" to 2), vm.content().routines.map { it.name to it.exerciseCount })
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
        assertEquals("Empuje", vm.content().routines.first().name)
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
        assertEquals("Torso (copia)", vm.content().routines.first().name)
        assertNull(vm.content().menuFor)
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
    fun archive_asksForConfirmation_thenRemovesTheRoutine() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onEvent(RoutinesEvent.RequestArchive("r1"))
        assertEquals("r1", vm.content().archiveFor?.id)
        assertTrue(repository.archivedIds.isEmpty())

        vm.onEvent(RoutinesEvent.ConfirmArchive)
        advanceUntilIdle()

        assertEquals(listOf("r1"), repository.archivedIds)
        assertEquals(listOf("Pierna"), vm.content().routines.map { it.name })
        assertNull(vm.content().archiveFor)
    }

    @Test
    fun dismissingTheArchiveConfirmation_archivesNothing() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onEvent(RoutinesEvent.RequestArchive("r1"))
        vm.onEvent(RoutinesEvent.DismissArchive)
        advanceUntilIdle()

        assertTrue(repository.archivedIds.isEmpty())
        assertNull(vm.content().archiveFor)
    }

    @Test
    fun archiveFailure_keepsTheRoutine_andSendsAMessage() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        repository.saveFailure = AppError.Network
        vm.onEvent(RoutinesEvent.RequestArchive("r1"))

        vm.effects.test {
            vm.onEvent(RoutinesEvent.ConfirmArchive)
            advanceUntilIdle()

            assertEquals(RoutinesEffect.ActionFailed, awaitItem())
        }
        assertEquals(2, vm.content().routines.size)
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
    fun offline_rejectsDuplicateAndArchive_butStillAllowsEditing() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        connectivity.state.value = false
        advanceUntilIdle()
        assertEquals(false, vm.content().isOnline)

        vm.onEvent(RoutinesEvent.Duplicate("r1"))
        vm.onEvent(RoutinesEvent.RequestArchive("r1"))
        advanceUntilIdle()

        assertTrue(repository.duplicated.isEmpty())
        assertNull(vm.content().archiveFor)
        vm.effects.test {
            vm.onEvent(RoutinesEvent.Edit("r1"))
            assertEquals(RoutinesEffect.OpenBuilder("r1"), awaitItem())
        }
    }
}
