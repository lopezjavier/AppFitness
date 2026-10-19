package io.github.lopezjavier.appfitness.feature.exercises

import io.github.lopezjavier.appfitness.domain.usecase.LoadExerciseCatalog
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

class ExerciseDetailViewModelTest {
    private val repository = FakeExerciseRepository(listOf(exerciseRow("1", "bench")))
    private val library = FakeExerciseLibrary(mapOf("bench" to exerciseInfo("bench", "Press de banca")))

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadsTheExerciseWithItsLibraryEntry() = runTest {
        val vm = ExerciseDetailViewModel("1", LoadExerciseCatalog(repository, library))
        assertEquals(ExerciseDetailUiState.Loading, vm.uiState.value)

        advanceUntilIdle()

        val loaded = assertIs<ExerciseDetailUiState.Loaded>(vm.uiState.value)
        assertEquals("Press de banca", loaded.detail.name)
    }

    @Test
    fun unknownId_isAnError_andRetryReloads() = runTest {
        val vm = ExerciseDetailViewModel("zzz", LoadExerciseCatalog(repository, library))
        advanceUntilIdle()
        assertEquals(ExerciseDetailUiState.Error, vm.uiState.value)

        vm.retry()
        advanceUntilIdle()

        assertEquals(ExerciseDetailUiState.Error, vm.uiState.value)
    }
}
