package io.github.lopezjavier.appfitness.data.repository

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.ExerciseOptions
import io.github.lopezjavier.appfitness.domain.repository.ExerciseOptionsRepository

/** Used when Supabase is not configured: every call fails. */
class UnconfiguredExerciseOptionsRepository : ExerciseOptionsRepository {
    override suspend fun get(): Result<ExerciseOptions> = Result.failure(AppException(AppError.Unknown))

    override suspend fun setDoubleDumbbell(exerciseId: String, enabled: Boolean): Result<Unit> =
        Result.failure(AppException(AppError.Unknown))
}
