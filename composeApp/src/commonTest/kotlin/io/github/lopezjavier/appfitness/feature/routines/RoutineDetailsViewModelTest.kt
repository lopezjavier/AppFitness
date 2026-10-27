package io.github.lopezjavier.appfitness.feature.routines

import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.PlannedSet
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.usecase.LoadExerciseCatalog
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
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
import kotlin.time.Instant

class RoutineDetailsViewModelTest {
    private val exercises = FakeExerciseRepository(
        listOf(
            exerciseRow("a", "bench"),
            exerciseRow("b", "row"),
            exerciseRow("custom", null, name = "Mi ejercicio", custom = true),
        ),
    )
    private val library = FakeExerciseLibrary(
        mapOf("bench" to exerciseInfo("bench", "Press de banca"), "row" to exerciseInfo("row", "Remo con barra")),
    )
    private val repository = FakeRoutineRepository(listOf(routineOf("r1", "Torso", "a", "b")))
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

    private fun viewModel(routineId: String = "r1") = RoutineDetailsViewModel(
        routineId,
        repository,
        sessions,
        LoadExerciseCatalog(exercises, library),
        connectivity,
    )

    private fun RoutineDetailsViewModel.content() = assertIs<RoutineDetailsUiState.Content>(uiState.value)

    @Test
    fun startsLoading_thenShowsTheNameAndTheExercisesWithNamesAndImages() = runTest {
        val vm = viewModel()
        assertEquals(RoutineDetailsUiState.Loading, vm.uiState.value)

        advanceUntilIdle()

        val content = vm.content()
        assertEquals("Torso", content.name)
        assertEquals(listOf("a", "b"), content.exercises.map { it.exerciseId })
        assertEquals(listOf("Press de banca", "Remo con barra"), content.exercises.map { it.name })
        assertEquals(listOf("bench", "row"), content.exercises.map { it.imageKey })
        assertEquals(List(3) { PlannedSet(null, 8, SetType.Working) }, content.exercises.first().plannedSets)
    }

    @Test
    fun aCustomExercise_hasItsOwnNameAndNoImage() = runTest {
        repository.routines += routineOf("r2", "Brazos", "custom")
        val vm = viewModel("r2")

        advanceUntilIdle()

        val row = vm.content().exercises.single()
        assertEquals("Mi ejercicio", row.name)
        assertNull(row.imageKey)
    }

    @Test
    fun aRoutineThatDoesNotExist_isAnError() = runTest {
        val vm = viewModel("zzz")

        advanceUntilIdle()

        assertEquals(RoutineDetailsUiState.Error, vm.uiState.value)
    }

    @Test
    fun loadFailure_showsTheError_andRetryRecovers() = runTest {
        repository.failure = AppError.Network
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(RoutineDetailsUiState.Error, vm.uiState.value)

        repository.failure = null
        vm.onEvent(RoutineDetailsEvent.Retry)
        assertEquals(RoutineDetailsUiState.Loading, vm.uiState.value)
        advanceUntilIdle()

        assertEquals("Torso", vm.content().name)
    }

    @Test
    fun lastDone_isNullWhenTheRoutineWasNeverDone() = runTest {
        val vm = viewModel()

        advanceUntilIdle()

        assertNull(vm.content().lastDone)
        assertEquals(listOf("r1"), sessions.lastCompletedCalls)
    }

    @Test
    fun lastDone_isTheStartOfTheLastCompletedSession() = runTest {
        sessions.lastCompleted = Instant.parse("2026-10-20T10:00:00Z")
        val vm = viewModel()

        advanceUntilIdle()

        assertEquals(Instant.parse("2026-10-20T10:00:00Z"), vm.content().lastDone)
    }

    @Test
    fun aFailedLastDoneRead_doesNotBlockTheScreen() = runTest {
        sessions.lastCompletedFailure = AppError.Network
        val vm = viewModel()

        advanceUntilIdle()

        assertNull(vm.content().lastDone)
        assertEquals("Torso", vm.content().name)
    }

    @Test
    fun refresh_reloadsWithoutTheSkeleton_afterTheBuilderChangedTheRoutine() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        repository.routines[0] = repository.routines[0].copy(name = "Torso fuerza")

        vm.onEvent(RoutineDetailsEvent.Refresh)
        assertIs<RoutineDetailsUiState.Content>(vm.uiState.value)
        advanceUntilIdle()

        assertEquals("Torso fuerza", vm.content().name)
    }

    @Test
    fun aRefreshThatFails_keepsTheContentOnScreen() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        repository.failure = AppError.Network

        vm.onEvent(RoutineDetailsEvent.Refresh)
        advanceUntilIdle()

        assertEquals("Torso", vm.content().name)
    }

    @Test
    fun connectivity_isReflectedInTheContent() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        connectivity.state.value = false
        advanceUntilIdle()

        assertEquals(false, vm.content().isOnline)
    }

    @Test
    fun start_createsTheSession_andOpensTheLiveScreen() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.effects.test {
            vm.onEvent(RoutineDetailsEvent.Start)
            advanceUntilIdle()

            assertEquals(RoutineDetailsEffect.OpenLiveSession, awaitItem())
        }
        assertEquals(listOf("r1"), sessions.startedRoutines)
        assertEquals(false, vm.content().busy)
    }

    @Test
    fun start_whileASessionIsInProgress_reportsIt() = runTest {
        sessions.startFailure = AppError.SessionAlreadyInProgress
        val vm = viewModel()
        advanceUntilIdle()

        vm.effects.test {
            vm.onEvent(RoutineDetailsEvent.Start)
            advanceUntilIdle()

            assertEquals(RoutineDetailsEffect.SessionInProgress, awaitItem())
        }
        assertEquals(false, vm.content().busy)
    }

    @Test
    fun start_failure_reportsStartFailed() = runTest {
        sessions.startFailure = AppError.Network
        val vm = viewModel()
        advanceUntilIdle()

        vm.effects.test {
            vm.onEvent(RoutineDetailsEvent.Start)
            advanceUntilIdle()

            assertEquals(RoutineDetailsEffect.StartFailed, awaitItem())
        }
    }

    @Test
    fun start_offline_isRefused_andStartsNothing() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        connectivity.state.value = false
        advanceUntilIdle()

        vm.effects.test {
            vm.onEvent(RoutineDetailsEvent.Start)
            advanceUntilIdle()

            assertEquals(RoutineDetailsEffect.OfflineRefused, awaitItem())
        }
        assertTrue(sessions.startedRoutines.isEmpty())
    }

    @Test
    fun aSecondStartWhileOneRuns_isIgnored() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onEvent(RoutineDetailsEvent.Start)
        vm.onEvent(RoutineDetailsEvent.Start)
        advanceUntilIdle()

        assertEquals(listOf("r1"), sessions.startedRoutines)
    }

    @Test
    fun delete_removesTheRoutine_andSendsDeleted() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.effects.test {
            vm.onEvent(RoutineDetailsEvent.Delete)
            advanceUntilIdle()

            assertEquals(RoutineDetailsEffect.Deleted, awaitItem())
        }
        assertEquals(listOf("r1"), repository.deletedIds)
    }

    @Test
    fun delete_failure_sendsActionFailed_andKeepsTheScreen() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        repository.saveFailure = AppError.Network

        vm.effects.test {
            vm.onEvent(RoutineDetailsEvent.Delete)
            advanceUntilIdle()

            assertEquals(RoutineDetailsEffect.ActionFailed, awaitItem())
        }
        assertEquals(false, vm.content().busy)
        assertEquals("Torso", vm.content().name)
    }

    @Test
    fun delete_offline_isRefused_andDeletesNothing() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        connectivity.state.value = false
        advanceUntilIdle()

        vm.effects.test {
            vm.onEvent(RoutineDetailsEvent.Delete)
            advanceUntilIdle()

            assertEquals(RoutineDetailsEffect.OfflineRefused, awaitItem())
        }
        assertTrue(repository.deletedIds.isEmpty())
    }

    @Test
    fun aViewModelRecreatedWithTheSameRoutineId_reloadsTheSameContent() = runTest {
        val first = viewModel()
        advanceUntilIdle()

        val second = viewModel()
        advanceUntilIdle()

        assertEquals(first.content(), second.content())
    }
}
