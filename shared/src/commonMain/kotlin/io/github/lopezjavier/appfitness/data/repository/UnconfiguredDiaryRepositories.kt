package io.github.lopezjavier.appfitness.data.repository

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.MealSlot
import io.github.lopezjavier.appfitness.domain.repository.FoodDiaryRepository
import io.github.lopezjavier.appfitness.domain.repository.MealSlotRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.LocalDate

/** Used when Supabase is not configured: every call fails. */
class UnconfiguredMealSlotRepository : MealSlotRepository {
    override suspend fun listEnabled(): Result<List<MealSlot>> = Result.failure(AppException(AppError.Unknown))
}

/** Used when Supabase is not configured: nothing is ever emitted and every refresh fails. */
class UnconfiguredFoodDiaryRepository : FoodDiaryRepository {
    override fun entries(date: LocalDate): Flow<List<FoodLogEntry>> = emptyFlow()

    override suspend fun refresh(date: LocalDate): Result<Unit> = Result.failure(AppException(AppError.Unknown))
}
