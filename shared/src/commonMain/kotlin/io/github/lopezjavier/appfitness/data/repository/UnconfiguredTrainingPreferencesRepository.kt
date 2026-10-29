package io.github.lopezjavier.appfitness.data.repository

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.MyTraining
import io.github.lopezjavier.appfitness.domain.model.TrainingPreferences
import io.github.lopezjavier.appfitness.domain.repository.TrainingPreferencesRepository

/** Used when Supabase is not configured: the read fails. */
class UnconfiguredTrainingPreferencesRepository : TrainingPreferencesRepository {
    override suspend fun get(): Result<TrainingPreferences> = Result.failure(AppException(AppError.Unknown))

    override suspend fun saveMyTraining(training: MyTraining): Result<Unit> =
        Result.failure(AppException(AppError.Unknown))
}
