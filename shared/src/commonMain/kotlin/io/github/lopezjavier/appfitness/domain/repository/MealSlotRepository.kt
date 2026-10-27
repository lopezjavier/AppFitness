package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.MealSlot
import io.github.lopezjavier.appfitness.domain.model.MealSlotDraft

/** The signed-in user's meals. A failure carries an [AppException]. */
interface MealSlotRepository {
    /** The enabled meal slots ordered by position. */
    suspend fun listEnabled(): Result<List<MealSlot>>

    /** Every meal slot, enabled or not, ordered by position. */
    suspend fun listAll(): Result<List<MealSlot>>

    /**
     * Saves the whole structure atomically: [slots] is every kept meal in final order (a null id is a new meal) and
     * [removedIds] the meals to remove. A refused structure (duplicate name, bad shape, last meal, meal with logged
     * entries) fails with `AppError.Validation`, and a foreign id with `AppError.NotFound`.
     */
    suspend fun saveStructure(slots: List<MealSlotDraft>, removedIds: List<String>): Result<Unit>

    /** Ids of the meals that have logged entries on any day; those cannot be removed. */
    suspend fun listInUse(): Result<Set<String>>
}
