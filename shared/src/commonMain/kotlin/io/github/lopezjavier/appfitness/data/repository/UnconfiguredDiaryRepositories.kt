package io.github.lopezjavier.appfitness.data.repository

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.AmountEstimate
import io.github.lopezjavier.appfitness.domain.model.DiaryDaySummary
import io.github.lopezjavier.appfitness.domain.model.EditableFood
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.FoodPhotoDraft
import io.github.lopezjavier.appfitness.domain.model.FoodPhotoMode
import io.github.lopezjavier.appfitness.domain.model.MealDefault
import io.github.lopezjavier.appfitness.domain.model.MealDefaultAssignment
import io.github.lopezjavier.appfitness.domain.model.MealSlot
import io.github.lopezjavier.appfitness.domain.model.MealSlotDraft
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.model.Micronutrients
import io.github.lopezjavier.appfitness.domain.model.NewFood
import io.github.lopezjavier.appfitness.domain.model.NewFoodEntry
import io.github.lopezjavier.appfitness.domain.model.NewTemplateItem
import io.github.lopezjavier.appfitness.domain.model.UpdatedFood
import io.github.lopezjavier.appfitness.domain.model.UpdatedFoodEntry
import io.github.lopezjavier.appfitness.domain.repository.FoodDiaryRepository
import io.github.lopezjavier.appfitness.domain.repository.FoodPhotoRepository
import io.github.lopezjavier.appfitness.domain.repository.FoodRepository
import io.github.lopezjavier.appfitness.domain.repository.MealDefaultsRepository
import io.github.lopezjavier.appfitness.domain.repository.MealSlotRepository
import io.github.lopezjavier.appfitness.domain.repository.MealTemplateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.LocalDate

/** Used when Supabase is not configured: every call fails. */
class UnconfiguredMealTemplateRepository : MealTemplateRepository {
    private fun <T> failure(): Result<T> = Result.failure(AppException(AppError.Unknown))

    override suspend fun list(): Result<List<MealTemplate>> = failure()

    override suspend fun save(
        id: String?,
        name: String,
        mealSlotId: String?,
        items: List<NewTemplateItem>,
    ): Result<String> = failure()

    override suspend fun saveAsComida(
        date: LocalDate,
        mealSlotId: String,
        name: String,
        entryIds: List<String>,
        items: List<NewTemplateItem>,
    ): Result<String> = failure()

    override suspend fun rename(id: String, name: String): Result<Unit> = failure()

    override suspend fun delete(id: String): Result<Unit> = failure()
}

/** Used when Supabase is not configured: every call fails. */
class UnconfiguredMealDefaultsRepository : MealDefaultsRepository {
    override suspend fun list(): Result<List<MealDefault>> = Result.failure(AppException(AppError.Unknown))

    override suspend fun save(assignments: List<MealDefaultAssignment>): Result<Unit> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun applyForDate(date: LocalDate): Result<Int> = Result.failure(AppException(AppError.Unknown))
}

/** Used when Supabase is not configured: every call fails. */
class UnconfiguredMealSlotRepository : MealSlotRepository {
    override suspend fun listEnabled(): Result<List<MealSlot>> = Result.failure(AppException(AppError.Unknown))

    override suspend fun listAll(): Result<List<MealSlot>> = Result.failure(AppException(AppError.Unknown))

    override suspend fun save(slots: List<MealSlotDraft>): Result<Unit> = Result.failure(AppException(AppError.Unknown))

    override suspend fun delete(id: String): Result<Unit> = Result.failure(AppException(AppError.Unknown))
}

/** Used when Supabase is not configured: nothing is ever emitted and every refresh fails. */
class UnconfiguredFoodDiaryRepository : FoodDiaryRepository {
    override fun entries(date: LocalDate): Flow<List<FoodLogEntry>> = emptyFlow()

    override suspend fun refresh(date: LocalDate): Result<Unit> = Result.failure(AppException(AppError.Unknown))

    override suspend fun addEntry(entry: NewFoodEntry): Result<FoodLogEntry> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun updateEntry(id: String, update: UpdatedFoodEntry): Result<FoodLogEntry> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun deleteEntry(id: String): Result<Unit> = Result.failure(AppException(AppError.Unknown))

    override suspend fun addEntries(entries: List<NewFoodEntry>): Result<List<FoodLogEntry>> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun deleteEntries(ids: List<String>): Result<Unit> = Result.failure(AppException(AppError.Unknown))

    override suspend fun summaries(from: LocalDate, to: LocalDate): Result<List<DiaryDaySummary>> =
        Result.failure(AppException(AppError.Unknown))
}

/** Used when Supabase is not configured: nothing is found and every write fails. */
@Suppress("TooManyFunctions") // One function per FoodRepository operation.
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

    override suspend fun micronutrients(food: Food): Result<Micronutrients> = failure()

    override suspend fun editableFood(id: String): Result<EditableFood> = failure()

    override suspend fun updateFood(food: UpdatedFood): Result<Food> = failure()

    override suspend fun createFood(food: NewFood): Result<Food> = failure()
}

/** Used when Supabase is not configured: every estimate fails. */
class UnconfiguredFoodPhotoRepository : FoodPhotoRepository {
    override suspend fun estimate(mode: FoodPhotoMode, imageBase64: String, comment: String?): Result<FoodPhotoDraft> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun estimateAmount(food: Food, imageBase64: String): Result<AmountEstimate> =
        Result.failure(AppException(AppError.Unknown))
}
