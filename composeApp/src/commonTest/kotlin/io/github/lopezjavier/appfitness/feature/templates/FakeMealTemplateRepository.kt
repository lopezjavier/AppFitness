package io.github.lopezjavier.appfitness.feature.templates

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.model.MealTemplateItem
import io.github.lopezjavier.appfitness.domain.model.NewTemplateItem
import io.github.lopezjavier.appfitness.domain.repository.MealTemplateRepository

/**
 * In-memory [MealTemplateRepository]: names are unique ignoring case and surrounding spaces (as in the database),
 * items resolve their foods from [foods], and each operation can be forced to fail with its `*Failure` property.
 */
internal class FakeMealTemplateRepository(val foods: MutableMap<String, Food> = mutableMapOf()) :
    MealTemplateRepository {
    val templates = mutableListOf<MealTemplate>()
    var listFailure: AppError? = null
    var saveFailure: AppError? = null

    /** How many times [save] was called (successful or not). */
    var saveCalls = 0
    var renameFailure: AppError? = null
    var deleteFailure: AppError? = null
    private var nextId = 1

    private fun key(name: String) = name.trim().lowercase()

    private fun taken(name: String, exceptId: String?) = templates.any {
        it.id != exceptId && key(it.name) == key(name)
    }

    private fun <T> failure(error: AppError): Result<T> = Result.failure(AppException(error))

    override suspend fun list(): Result<List<MealTemplate>> =
        listFailure?.let { failure(it) } ?: Result.success(templates.sortedBy { key(it.name) })

    private fun saveError(id: String?, name: String, items: List<NewTemplateItem>): AppError? = when {
        saveFailure != null -> saveFailure
        name.isBlank() || items.isEmpty() -> AppError.Validation
        taken(name, id) -> AppError.NameAlreadyExists
        id != null && templates.none { it.id == id } -> AppError.NotFound
        else -> null
    }

    override suspend fun save(
        id: String?,
        name: String,
        mealSlotId: String?,
        items: List<NewTemplateItem>,
    ): Result<String> {
        saveCalls++
        saveError(id, name, items)?.let { return failure(it) }
        val savedId = id ?: "template-${nextId++}"
        val resolved = items.map { MealTemplateItem(foods.getValue(it.foodId), it.amount, it.portionLabel) }
        val saved = MealTemplate(savedId, name.trim(), mealSlotId, resolved)
        val index = templates.indexOfFirst { it.id == savedId }
        if (index < 0) templates += saved else templates[index] = saved
        return Result.success(savedId)
    }

    private fun renameError(id: String, name: String): AppError? = when {
        renameFailure != null -> renameFailure
        templates.none { it.id == id } -> AppError.NotFound
        taken(name, id) -> AppError.NameAlreadyExists
        else -> null
    }

    override suspend fun rename(id: String, name: String): Result<Unit> {
        renameError(id, name)?.let { return failure(it) }
        val index = templates.indexOfFirst { it.id == id }
        templates[index] = templates[index].copy(name = name.trim())
        return Result.success(Unit)
    }

    override suspend fun delete(id: String): Result<Unit> {
        deleteFailure?.let { return failure(it) }
        templates.removeAll { it.id == id }
        return Result.success(Unit)
    }
}
