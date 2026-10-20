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
     * Saves the whole structure atomically; [slots] must contain every existing meal in final order. An invalid
     * shape fails with `AppError.Validation`.
     */
    suspend fun save(slots: List<MealSlotDraft>): Result<Unit>

    /**
     * Deletes the meal [id] and re-packs the positions. Fails with `AppError.Validation` when it is the last meal or
     * has logged entries, and with `AppError.NotFound` for an unknown id.
     */
    suspend fun delete(id: String): Result<Unit>
}
