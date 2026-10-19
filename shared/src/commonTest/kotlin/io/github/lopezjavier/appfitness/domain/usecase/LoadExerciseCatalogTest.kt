package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoadExerciseCatalogTest {
    private val library = FakeExerciseLibrary(mapOf("bench" to info("bench", "Press de banca")))

    @Test
    fun catalogRow_takesItsNameFromTheLibrary() = runTest {
        val repo = FakeExerciseRepository(listOf(catalogRow("1", "bench")))

        val items = LoadExerciseCatalog(repo, library)().getOrThrow()

        assertEquals(listOf("Press de banca"), items.map { it.name })
        assertTrue(items.single().hasMedia)
    }

    @Test
    fun customRow_keepsItsOwnName_withoutMedia() = runTest {
        val repo = FakeExerciseRepository(listOf(catalogRow("c1", null, name = "Press Hammer", custom = true)))

        val item = LoadExerciseCatalog(repo, library)().getOrThrow().single()

        assertEquals("Press Hammer", item.name)
        assertFalse(item.hasMedia)
    }

    @Test
    fun catalogRow_whoseKeyIsMissingFromTheLibrary_isDropped() = runTest {
        val repo = FakeExerciseRepository(listOf(catalogRow("1", "bench"), catalogRow("2", "unknown")))

        assertEquals(listOf("1"), LoadExerciseCatalog(repo, library)().getOrThrow().map { it.id })
    }

    @Test
    fun withoutTheBundle_onlyCustomExercisesAreListed() = runTest {
        val repo =
            FakeExerciseRepository(listOf(catalogRow("1", "bench"), catalogRow("c", null, name = "Mio", custom = true)))

        val items = LoadExerciseCatalog(repo, FakeExerciseLibrary())().getOrThrow()

        assertEquals(listOf("c"), items.map { it.id })
    }

    @Test
    fun repositoryError_isPropagated() = runTest {
        val repo = FakeExerciseRepository(failure = AppError.Network)

        assertEquals(AppError.Network, LoadExerciseCatalog(repo, library)().appErrorOrNull())
    }

    @Test
    fun detail_ofACatalogRow_carriesTheLibraryEntry() = runTest {
        val repo = FakeExerciseRepository(listOf(catalogRow("1", "bench")))

        val detail = LoadExerciseCatalog(repo, library).detail("1").getOrThrow()

        assertEquals("Press de banca", detail.name)
        assertEquals(listOf("Paso 1", "Paso 2"), detail.info?.instructions)
    }

    @Test
    fun detail_ofAKeyWithoutLibraryData_hasNoInfoAndBlankName() = runTest {
        val repo = FakeExerciseRepository(listOf(catalogRow("2", "unknown")))

        val detail = LoadExerciseCatalog(repo, library).detail("2").getOrThrow()

        assertNull(detail.info)
        assertEquals("", detail.name)
    }

    @Test
    fun detail_ofAMissingId_isNotFound() = runTest {
        assertEquals(
            AppError.NotFound,
            LoadExerciseCatalog(FakeExerciseRepository(), library).detail("zzz").appErrorOrNull(),
        )
    }
}
