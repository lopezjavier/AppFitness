package io.github.lopezjavier.appfitness.feature.diary

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.MealCode
import io.github.lopezjavier.appfitness.domain.model.MealSlot
import io.github.lopezjavier.appfitness.domain.model.MealSlotDraft
import io.github.lopezjavier.appfitness.domain.model.NewFoodEntry
import io.github.lopezjavier.appfitness.domain.model.UpdatedFoodEntry
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

/**
 * Test double of [MealSlotRepository]: [result] answers [listEnabled] (and [listAll] unless [allResult] is set). A
 * successful [save] stores the new structure, so later reads reflect it; a failing one ([saveResult]) changes nothing.
 */
internal class FakeMealSlotRepository(var result: Result<List<MealSlot>> = Result.success(DefaultMealSlots)) :
    MealSlotRepository {
    var allResult: Result<List<MealSlot>>? = null
    var saveResult: Result<Unit> = Result.success(Unit)
    var deleteResult: Result<Unit> = Result.success(Unit)
    val saved = mutableListOf<List<MealSlotDraft>>()
    val deleted = mutableListOf<String>()

    override suspend fun listEnabled(): Result<List<MealSlot>> = result

    override suspend fun listAll(): Result<List<MealSlot>> = allResult ?: result

    override suspend fun save(slots: List<MealSlotDraft>): Result<Unit> {
        if (saveResult.isFailure) return saveResult
        saved += slots
        val stored = slots.mapIndexed { index, draft ->
            MealSlot(draft.id ?: "custom-${saved.size}-$index", draft.code, draft.label, index)
        }
        allResult = Result.success(stored)
        result = Result.success(stored)
        return saveResult
    }

    override suspend fun delete(id: String): Result<Unit> {
        if (deleteResult.isFailure) return deleteResult
        deleted += id
        val remaining = (allResult ?: result).getOrThrow().filter { it.id != id }
        allResult = Result.success(remaining)
        result = Result.success(remaining)
        return deleteResult
    }

    fun failSave(error: AppError) {
        saveResult = Result.failure(AppException(error))
    }

    fun failDelete(error: AppError) {
        deleteResult = Result.failure(AppException(error))
    }
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
        if (date in failingDays) return Result.failure(AppException(AppError.Network))
        if (refreshResult.isSuccess) cache.update { it + (date to server[date].orEmpty()) }
        return refreshResult
    }

    private val failingDays = mutableSetOf<LocalDate>()

    /** Makes [refresh] fail for [date] only. */
    fun failRefreshOf(date: LocalDate) {
        failingDays += date
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

    val updated = mutableListOf<Pair<String, UpdatedFoodEntry>>()
    var updateResult: Result<Unit> = Result.success(Unit)

    /** Like the real repository: the entry is replaced in the cache of its day, which re-emits. */
    override suspend fun updateEntry(id: String, update: UpdatedFoodEntry): Result<FoodLogEntry> {
        if (updateResult.isFailure) return Result.failure(updateResult.exceptionOrNull()!!)
        val current = cache.value.values.flatten().first { it.id == id }
        updated += id to update
        val edited = current.copy(
            mealSlotId = update.mealSlotId,
            amount = update.amount,
            portionLabel = update.portionLabel,
            kcal = update.snapshot.kcal,
            proteinG = update.snapshot.proteinG,
            carbsG = update.snapshot.carbsG,
            fatG = update.snapshot.fatG,
        )
        cache.update { days -> days.mapValues { (_, list) -> list.map { if (it.id == id) edited else it } } }
        return Result.success(edited)
    }

    val deleted = mutableListOf<String>()
    var deleteResult: Result<Unit> = Result.success(Unit)

    override suspend fun deleteEntry(id: String): Result<Unit> {
        if (deleteResult.isFailure) return deleteResult
        deleted += id
        cache.update { days -> days.mapValues { (_, list) -> list.filterNot { it.id == id } } }
        return Result.success(Unit)
    }

    /** One element per [addEntries] call, so a test can assert a copy is a single request. */
    val addedBatches = mutableListOf<List<NewFoodEntry>>()
    val deletedBatches = mutableListOf<List<String>>()
    var batchResult: Result<Unit> = Result.success(Unit)

    override suspend fun addEntries(entries: List<NewFoodEntry>): Result<List<FoodLogEntry>> {
        if (batchResult.isFailure) return Result.failure(batchResult.exceptionOrNull()!!)
        addedBatches += entries
        val created = entries.mapIndexed { index, entry ->
            FoodLogEntry(
                id = "batch-${addedBatches.size}-$index",
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
        }
        cache.update { days ->
            days.mapValues { (date, list) -> list + created.filter { it.logDate == date } }
        }
        return Result.success(created)
    }

    override suspend fun deleteEntries(ids: List<String>): Result<Unit> {
        if (batchResult.isFailure) return batchResult
        deletedBatches += ids
        cache.update { days -> days.mapValues { (_, list) -> list.filterNot { it.id in ids } } }
        return Result.success(Unit)
    }

    fun failBatch() {
        batchResult = Result.failure(AppException(AppError.Network))
    }

    fun push(date: LocalDate, entries: List<FoodLogEntry>) = cache.update { it + (date to entries) }

    fun failUpdate() {
        updateResult = Result.failure(AppException(AppError.Network))
    }

    fun failDelete() {
        deleteResult = Result.failure(AppException(AppError.Network))
    }

    fun failAdd() {
        addResult = Result.failure(AppException(AppError.Network))
    }

    fun failRefresh() {
        refreshResult = Result.failure(AppException(AppError.Network))
    }
}
