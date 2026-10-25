package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.MealDefault
import io.github.lopezjavier.appfitness.domain.model.MealDefaultAssignment
import kotlinx.datetime.LocalDate

/** The signed-in user's default comidas per meal and weekday. */
interface MealDefaultsRepository {
    suspend fun list(): Result<List<MealDefault>>

    /** Replaces every default atomically; an empty list clears them. */
    suspend fun save(assignments: List<MealDefaultAssignment>): Result<Unit>

    /** Fills `date` (the client's own today) with the defaults of its weekday; returns the entries added. */
    suspend fun applyForDate(date: LocalDate): Result<Int>
}
