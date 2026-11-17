package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.ExerciseNames
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class ExerciseNameResolverTest {
    private val library = FakeExerciseLibrary(mapOf("bench" to info("bench", "Press de banca")))

    @Test
    fun catalogueRow_isNamedFromTheLibrary() = runTest {
        val resolver = ExerciseNameResolver(FakeExerciseRepository(listOf(catalogRow("1", "bench"))), library)

        assertEquals("Press de banca", resolver.resolve().getOrThrow()["1"])
    }

    @Test
    fun customRow_isNamedFromTheRow() = runTest {
        val repo = FakeExerciseRepository(listOf(catalogRow("c", null, name = "Mi ejercicio", custom = true)))

        assertEquals("Mi ejercicio", ExerciseNameResolver(repo, library).resolve().getOrThrow()["c"])
    }

    @Test
    fun catalogueRow_withAKeyMissingFromTheLibrary_hasNoEntry() = runTest {
        val resolver = ExerciseNameResolver(FakeExerciseRepository(listOf(catalogRow("2", "unknown"))), library)

        assertNull(resolver.resolve().getOrThrow()["2"])
    }

    @Test
    fun customRow_withABlankName_hasNoEntry() = runTest {
        val repo = FakeExerciseRepository(listOf(catalogRow("c", null, name = "  ", custom = true)))

        assertNull(ExerciseNameResolver(repo, library).resolve().getOrThrow()["c"])
    }

    @Test
    fun repositoryFailure_isAFailure() = runTest {
        val resolver = ExerciseNameResolver(FakeExerciseRepository(failure = AppError.Network), library)

        assertEquals(AppError.Network, resolver.resolve().appErrorOrNull())
    }

    @Test
    fun observe_emitsNowAndAfterCreateRenameAndDelete() = runTest {
        val repo = FakeExerciseRepository(listOf(catalogRow("1", "bench")))
        val seen = mutableListOf<ExerciseNames>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            ExerciseNameResolver(repo, library).observe().toList(seen)
        }

        val created = repo.createCustom("Hammer", MuscleGroup.Chest, Equipment.Machine).getOrThrow()
        repo.updateCustom(created.id, "Hammer 2", MuscleGroup.Chest, Equipment.Machine, false, null).getOrThrow()
        repo.deleteCustom(created.id).getOrThrow()

        assertEquals(
            listOf(
                mapOf("1" to "Press de banca"),
                mapOf("1" to "Press de banca", created.id to "Hammer"),
                mapOf("1" to "Press de banca", created.id to "Hammer 2"),
                mapOf("1" to "Press de banca"),
            ),
            seen.map { it.byId },
        )
    }

    @Test
    fun catalogueRow_carriesItsKeyAsImageKey() = runTest {
        val resolver = ExerciseNameResolver(FakeExerciseRepository(listOf(catalogRow("1", "bench"))), library)

        assertEquals("bench", resolver.resolve().getOrThrow().imageKeyOf("1"))
    }

    @Test
    fun customRow_hasNoImageKey() = runTest {
        val repo = FakeExerciseRepository(listOf(catalogRow("c", null, name = "Mi ejercicio", custom = true)))

        assertNull(ExerciseNameResolver(repo, library).resolve().getOrThrow().imageKeyOf("c"))
    }

    @Test
    fun catalogueRow_withAKeyMissingFromTheLibrary_hasNoNameAndNoImageKey() = runTest {
        val resolver = ExerciseNameResolver(FakeExerciseRepository(listOf(catalogRow("2", "unknown"))), library)
        val names = resolver.resolve().getOrThrow()

        assertNull(names["2"])
        assertNull(names.imageKeyOf("2"))
    }

    @Test
    fun observe_emitsTheImageKeyAfterACustomCreate_andNoneForTheCustomRow() = runTest {
        val repo = FakeExerciseRepository(listOf(catalogRow("1", "bench")))
        val seen = mutableListOf<ExerciseNames>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            ExerciseNameResolver(repo, library).observe().toList(seen)
        }

        val created = repo.createCustom("Hammer", MuscleGroup.Chest, Equipment.Machine).getOrThrow()

        assertEquals("bench", seen.last().imageKeyOf("1"))
        assertNull(seen.last().imageKeyOf(created.id))
    }

    @Test
    fun namesWithEqualNamesAndDifferentImageKeys_areNotEqual() {
        val byId = mapOf("1" to "Press de banca")

        assertNotEquals(ExerciseNames(byId, mapOf("1" to "bench")), ExerciseNames(byId))
        assertEquals(ExerciseNames(byId, mapOf("1" to "bench")), ExerciseNames(byId, mapOf("1" to "bench")))
    }

    @Test
    fun observe_keepsThePreviousValueWhenAReReadFails() = runTest {
        val repo = FakeExerciseRepository(listOf(catalogRow("1", "bench")))
        val seen = mutableListOf<ExerciseNames>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            ExerciseNameResolver(repo, library).observe().toList(seen)
        }

        repo.failure = AppError.Network
        repo.createCustom("Hammer", MuscleGroup.Chest, Equipment.Machine).getOrThrow()

        assertEquals(listOf(mapOf("1" to "Press de banca")), seen.map { it.byId })
    }
}
