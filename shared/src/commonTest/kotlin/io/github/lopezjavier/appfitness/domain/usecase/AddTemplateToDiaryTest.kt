package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.model.MealTemplateItem
import io.github.lopezjavier.appfitness.domain.model.NewFoodEntry
import io.github.lopezjavier.appfitness.domain.model.NutritionPer100
import io.github.lopezjavier.appfitness.domain.model.NutritionSnapshot
import io.github.lopezjavier.appfitness.domain.model.UpdatedFoodEntry
import io.github.lopezjavier.appfitness.domain.repository.FoodDiaryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class RecordingDiary(var failure: AppError? = null) : FoodDiaryRepository {
    val addCalls = mutableListOf<List<NewFoodEntry>>()

    override fun entries(date: LocalDate): Flow<List<FoodLogEntry>> = emptyFlow()

    override suspend fun refresh(date: LocalDate): Result<Unit> = Result.success(Unit)

    override suspend fun addEntry(entry: NewFoodEntry): Result<FoodLogEntry> = error("not used")

    override suspend fun updateEntry(id: String, update: UpdatedFoodEntry): Result<FoodLogEntry> = error("not used")

    override suspend fun deleteEntry(id: String): Result<Unit> = error("not used")

    override suspend fun addEntries(entries: List<NewFoodEntry>): Result<List<FoodLogEntry>> {
        addCalls += entries
        failure?.let { return Result.failure(AppException(it)) }
        return Result.success(
            entries.mapIndexed { index, entry ->
                FoodLogEntry(
                    id = "log$index",
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
            },
        )
    }

    override suspend fun deleteEntries(ids: List<String>): Result<Unit> = error("not used")
}

class AddTemplateToDiaryTest {
    private val date = LocalDate(2026, 10, 21)
    private val diary = RecordingDiary()
    private val addTemplate = AddTemplateToDiary(diary, ScaleNutrition())

    private fun food(id: String, name: String, kcal: Double) = Food(
        id = id,
        name = name,
        brand = null,
        baseUnit = "g",
        nutrition = NutritionPer100(kcal, 10.0, 20.0, 5.0),
        servingLabel = null,
        servingAmount = null,
        portions = emptyList(),
        imageUrl = null,
    )

    private val desayunoA =
        MealTemplate(
            id = "t1",
            name = "Desayuno A",
            mealSlotId = "breakfast",
            items = listOf(
                MealTemplateItem(food("eggs", "Huevos revueltos", 150.0), 120.0, null),
                MealTemplateItem(food("shake", "Batido de proteina", 80.0), 300.0, "1 vaso"),
            ),
        )

    @Test
    fun addsOneEntryPerFood_withSnapshotsFromTheCurrentFoodValues() = runTest {
        val result = addTemplate(desayunoA, "lunch", date)

        assertTrue(result.isSuccess)
        val entries = diary.addCalls.single()
        assertEquals(listOf("eggs", "shake"), entries.map { it.foodId })
        assertEquals(listOf("Huevos revueltos", "Batido de proteina"), entries.map { it.foodName })
        assertEquals(listOf(120.0, 300.0), entries.map { it.amount })
        assertEquals(NutritionSnapshot(kcal = 180.0, proteinG = 12.0, carbsG = 24.0, fatG = 6.0), entries[0].snapshot)
        assertEquals(240.0, entries[1].snapshot.kcal)
    }

    @Test
    fun usesTheChosenMealAndDate_notTheSuggestedOne() = runTest {
        addTemplate(desayunoA, "lunch", date)

        val entries = diary.addCalls.single()
        assertTrue(entries.all { it.mealSlotId == "lunch" && it.date == date })
    }

    @Test
    fun copiesThePortionLabel() = runTest {
        addTemplate(desayunoA, "lunch", date)

        assertEquals(listOf(null, "1 vaso"), diary.addCalls.single().map { it.portionLabel })
    }

    @Test
    fun failurePropagates() = runTest {
        diary.failure = AppError.Network

        val result = addTemplate(desayunoA, "lunch", date)

        assertEquals(AppError.Network, result.appErrorOrNull())
    }

    @Test
    fun emptyTemplate_addsNothingAndDoesNotCallTheRepository() = runTest {
        val result = addTemplate(desayunoA.copy(items = emptyList()), "lunch", date)

        assertTrue(result.getOrThrow().isEmpty())
        assertTrue(diary.addCalls.isEmpty())
    }
}
