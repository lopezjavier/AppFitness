package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals

/** The signed-in user's `nutrition_goals` row. A failure carries an [AppException]. */
interface NutritionGoalsRepository {
    /** The saved goals, or null when the user has no row yet. */
    suspend fun getOwn(): Result<NutritionGoals?>

    /** Creates or replaces the goals row. Idempotent. */
    suspend fun upsert(goals: NutritionGoals): Result<Unit>
}
