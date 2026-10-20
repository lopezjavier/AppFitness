package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.MealSlot
import io.github.lopezjavier.appfitness.domain.model.MealSlotDraft
import kotlinx.datetime.LocalDate

/** The signed-in user's meals. A failure carries an [AppException]. */
interface MealSlotRepository {
    /** The enabled meal slots ordered by position. */
    suspend fun listEnabled(): Result<List<MealSlot>>

    /** Every meal slot, enabled or not, ordered by position. */
    suspend fun listAll(): Result<List<MealSlot>>

    /**
     * Saves the whole structure atomically; [slots] must contain every existing meal (disabled ones too) in final
     * order. Disabling a meal that has entries on [viewedDay] fails with `AppError.Validation`, as does any other
     * invalid shape.
     */
    suspend fun save(slots: List<MealSlotDraft>, viewedDay: LocalDate): Result<Unit>
}
