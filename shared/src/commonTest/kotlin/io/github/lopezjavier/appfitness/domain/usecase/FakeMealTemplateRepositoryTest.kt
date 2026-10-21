package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.NewTemplateItem
import io.github.lopezjavier.appfitness.domain.model.NutritionPer100
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FakeMealTemplateRepositoryTest {
    private val eggs =
        Food("eggs", "Huevos", null, "g", NutritionPer100(150.0, 12.0, 1.0, 10.0), null, null, emptyList(), null)
    private val repo = FakeMealTemplateRepository(mutableMapOf("eggs" to eggs))
    private val items = listOf(NewTemplateItem("eggs", 120.0, null))

    @Test
    fun duplicateName_ignoresCaseAndSpaces() = runTest {
        repo.save(null, "Desayuno A", null, items).getOrThrow()

        assertEquals(AppError.NameAlreadyExists, repo.save(null, " desayuno a ", null, items).appErrorOrNull())
    }

    @Test
    fun overwrite_keepsTheIdAndAllowsItsOwnName() = runTest {
        val id = repo.save(null, "Desayuno A", null, items).getOrThrow()

        val again = repo.save(id, "Desayuno A", null, listOf(NewTemplateItem("eggs", 60.0, null))).getOrThrow()

        assertEquals(id, again)
        assertEquals(60.0, repo.list().getOrThrow().single().items.single().amount)
    }

    @Test
    fun renameToATakenName_isRejected() = runTest {
        repo.save(null, "A", null, items).getOrThrow()
        val b = repo.save(null, "B", null, items).getOrThrow()

        assertEquals(AppError.NameAlreadyExists, repo.rename(b, "a").appErrorOrNull())
    }

    @Test
    fun forcedFailure_isReturned() = runTest {
        repo.listFailure = AppError.Network

        assertEquals(AppError.Network, repo.list().appErrorOrNull())
    }
}
