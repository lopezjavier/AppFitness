package io.github.lopezjavier.appfitness.feature.diary

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.MealCode
import io.github.lopezjavier.appfitness.domain.model.MealSlot
import io.github.lopezjavier.appfitness.domain.model.NewFoodEntry
import io.github.lopezjavier.appfitness.domain.repository.FoodDiaryRepository
import io.github.lopezjavier.appfitness.domain.repository.MealSlotRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate

internal val DefaultMealSlots = listOf(
    MealSlot("breakfast", MealCode.Breakfast, "Desayuno", 0),
    MealSlot("lunch", MealCode.Lunch, "Comida", 1),
    MealSlot("snack", MealCode.Snack, "Merienda", 2),
    MealSlot("dinner", MealCode.Dinner, "Cena", 3),
)

/** Test double of [MealSlotRepository]: [result] answers every call. */
internal class FakeMealSlotRepository(var result: Result<List<MealSlot>> = Result.success(DefaultMealSlots)) :
    MealSlotRepository {
    override suspend fun listEnabled(): Result<List<MealSlot>> = result
}

/**
 * Test double of [FoodDiaryRepository] with a cache like the real one: [refresh] copies [server] into it (or fails
 * with [refreshResult]) and [push] simulates a write that updates the cache in place.
 */
internal class FakeFoodDiaryRepository : FoodDiaryRepository {
    private val cache = MutableStateFlow<Map<LocalDate, List<FoodLogEntry>>>(emptyMap())
    val server = mutableMapOf<LocalDate, List<FoodLogEntry>>()
    var refreshResult: Result<Unit> = Result.success(Unit)
    val refreshed = mutableListOf<LocalDate>()

    /** When set, [refresh] suspends until it completes. */
    var gate: CompletableDeferred<Unit>? = null

    override fun entries(date: LocalDate): Flow<List<FoodLogEntry>> = cache.map { it[date] }.filterNotNull()

    override suspend fun refresh(date: LocalDate): Result<Unit> {
        refreshed += date
        gate?.await()
        if (refreshResult.isSuccess) cache.update { it + (date to server[date].orEmpty()) }
        return refreshResult
    }

    val added = mutableListOf<NewFoodEntry>()
    var addResult: Result<Unit> = Result.success(Unit)

    /** Like the real repository: the entry joins the cache of a loaded day, which re-emits. */
    override suspend fun addEntry(entry: NewFoodEntry): Result<FoodLogEntry> {
        if (addResult.isFailure) return Result.failure(addResult.exceptionOrNull()!!)
        added += entry
        val created = FoodLogEntry(
            id = "new-${added.size}",
            logDate = entry.date,
            mealSlotId = entry.mealSlotId,
            foodId = entry.foodId,
            foodName = entry.foodName,
            amount = entry.amount,
            portionLabel = entry.portionLabel,
            baseUnit = "g",
            kcal = entry.snapshot.kcal,
            proteinG = entry.snapshot.proteinG,
            carbsG = entry.snapshot.carbsG,
            fatG = entry.snapshot.fatG,
        )
        cache.update { days -> days[entry.date]?.let { days + (entry.date to (it + created)) } ?: days }
        return Result.success(created)
    }

    fun push(date: LocalDate, entries: List<FoodLogEntry>) = cache.update { it + (date to entries) }

    fun failAdd() {
        addResult = Result.failure(AppException(AppError.Network))
    }

    fun failRefresh() {
        refreshResult = Result.failure(AppException(AppError.Network))
    }
}
