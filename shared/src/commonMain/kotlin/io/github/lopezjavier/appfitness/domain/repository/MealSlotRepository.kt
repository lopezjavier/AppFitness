package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.MealSlot

/** The signed-in user's meals. A failure carries an [AppException]. */
interface MealSlotRepository {
    /** The enabled meal slots ordered by position. */
    suspend fun listEnabled(): Result<List<MealSlot>>
}
