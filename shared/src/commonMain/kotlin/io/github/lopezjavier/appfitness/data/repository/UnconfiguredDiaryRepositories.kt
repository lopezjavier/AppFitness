package io.github.lopezjavier.appfitness.data.repository

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.MealSlot
import io.github.lopezjavier.appfitness.domain.model.NewFood
import io.github.lopezjavier.appfitness.domain.model.NewFoodEntry
import io.github.lopezjavier.appfitness.domain.repository.FoodDiaryRepository
import io.github.lopezjavier.appfitness.domain.repository.FoodRepository
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

    override suspend fun addEntry(entry: NewFoodEntry): Result<FoodLogEntry> =
        Result.failure(AppException(AppError.Unknown))
}

/** Used when Supabase is not configured: nothing is found and every write fails. */
class UnconfiguredFoodRepository : FoodRepository {
    private fun <T> failure(): Result<T> = Result.failure(AppException(AppError.Unknown))

    override suspend fun search(query: String, page: Int): Result<List<Food>> = failure()

    override suspend fun recent(days: Int): Result<List<Food>> = failure()

    override suspend fun mostEaten(limit: Int): Result<List<Food>> = failure()

    override suspend fun favourites(): Result<List<Food>> = failure()

    override fun favouriteIds(): Flow<Set<String>> = emptyFlow()

    override suspend fun setFavourite(foodId: String, favourite: Boolean): Result<Unit> = failure()

    override suspend fun getById(id: String): Result<Food> = failure()

    override suspend fun findByBarcode(code: String): Result<Food?> = failure()

    override suspend fun createFood(food: NewFood): Result<Food> = failure()
}
