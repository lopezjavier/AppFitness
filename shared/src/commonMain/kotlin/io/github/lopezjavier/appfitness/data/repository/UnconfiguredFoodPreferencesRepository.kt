package io.github.lopezjavier.appfitness.data.repository

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.FoodPreferences
import io.github.lopezjavier.appfitness.domain.repository.FoodPreferencesRepository

/** Used when Supabase is not configured: the read fails. */
class UnconfiguredFoodPreferencesRepository : FoodPreferencesRepository {
    override suspend fun get(): Result<FoodPreferences> = Result.failure(AppException(AppError.Unknown))
}
