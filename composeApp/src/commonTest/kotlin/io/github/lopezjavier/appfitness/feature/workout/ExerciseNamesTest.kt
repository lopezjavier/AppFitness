package io.github.lopezjavier.appfitness.feature.workout

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExerciseNamesTest {
    private val library = FakeExerciseLibrary(mapOf("bench" to exerciseInfo("bench", "Press de banca")))

    @Test
    fun aCustomExercise_usesItsOwnName() = runTest {
        val repository = FakeExerciseRepository(listOf(exerciseRow("c1", null, name = "Mi ejercicio", custom = true)))

        assertEquals(mapOf("c1" to "Mi ejercicio"), loadExerciseNames(repository, library))
    }

    @Test
    fun aCatalogExercise_usesTheLibraryName() = runTest {
        val repository = FakeExerciseRepository(listOf(exerciseRow("a", "bench")))

        assertEquals(mapOf("a" to "Press de banca"), loadExerciseNames(repository, library))
    }

    @Test
    fun aCustomExerciseWithoutName_isAbsent() = runTest {
        val repository = FakeExerciseRepository(listOf(exerciseRow("c1", null, name = null, custom = true)))

        assertEquals(emptyMap(), loadExerciseNames(repository, library))
    }

    @Test
    fun aFailingRepository_returnsNull() = runTest {
        val repository = FakeExerciseRepository(failure = AppError.Network)

        assertNull(loadExerciseNames(repository, library))
    }
}
