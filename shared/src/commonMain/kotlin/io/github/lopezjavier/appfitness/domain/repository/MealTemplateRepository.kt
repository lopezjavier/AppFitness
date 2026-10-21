package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.model.NewTemplateItem

/** The signed-in user's saved meals. A failure carries an [AppException]. */
interface MealTemplateRepository {
    /** All templates ordered by name, items ordered by position with their foods embedded. */
    suspend fun list(): Result<List<MealTemplate>>

    /**
     * Creates a template ([id] null) or overwrites template [id] (name, meal and items) atomically; returns its id.
     * Fails with `AppError.NameAlreadyExists` for a taken name, `Validation` for a bad name or items and `NotFound`
     * for an unknown [id].
     */
    suspend fun save(id: String?, name: String, mealSlotId: String?, items: List<NewTemplateItem>): Result<String>

    /** Renames template [id]; fails with `AppError.NameAlreadyExists` for a taken name. */
    suspend fun rename(id: String, name: String): Result<Unit>

    /** Deletes template [id] and its items. */
    suspend fun delete(id: String): Result<Unit>
}
