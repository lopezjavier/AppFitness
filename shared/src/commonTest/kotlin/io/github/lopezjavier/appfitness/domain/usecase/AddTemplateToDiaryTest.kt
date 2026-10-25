package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.CookingMethod
import io.github.lopezjavier.appfitness.domain.model.DiaryDaySummary
import io.github.lopezjavier.appfitness.domain.model.EntryGroup
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.FoodPreferences
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.model.MealTemplateItem
import io.github.lopezjavier.appfitness.domain.model.NewFoodEntry
import io.github.lopezjavier.appfitness.domain.model.NutritionPer100
import io.github.lopezjavier.appfitness.domain.model.NutritionSnapshot
import io.github.lopezjavier.appfitness.domain.model.UpdatedFoodEntry
import io.github.lopezjavier.appfitness.domain.repository.FoodDiaryRepository
import io.github.lopezjavier.appfitness.domain.repository.FoodPreferencesRepository
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

    override suspend fun summaries(from: LocalDate, to: LocalDate): Result<List<DiaryDaySummary>> = error("not used")
}

private class FakePreferences(var prefs: FoodPreferences = FoodPreferences(), var failure: AppError? = null) :
    FoodPreferencesRepository {
    var reads = 0

    override suspend fun get(): Result<FoodPreferences> {
        reads++
        return failure?.let { Result.failure(AppException(it)) } ?: Result.success(prefs)
    }
}

class AddTemplateToDiaryTest {
    private val date = LocalDate(2026, 10, 21)
    private val diary = RecordingDiary()
    private val preferences = FakePreferences()
    private var groupCounter = 0
    private val addTemplate =
        AddTemplateToDiary(diary, preferences, BuildCookedEntries(ScaleNutrition())) { "group-${++groupCounter}" }

    private fun food(id: String, name: String, kcal: Double, baseUnit: String = "g") = Food(
        id = id,
        name = name,
        brand = null,
        baseUnit = baseUnit,
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
    fun allEntriesShareOneGroupNamedAfterTheTemplate() = runTest {
        addTemplate(desayunoA, "lunch", date)

        val groups = diary.addCalls.single().map { it.group }
        assertEquals(listOf(EntryGroup("group-1", "Desayuno A")), groups.distinct())
    }

    @Test
    fun eachApplicationGetsItsOwnGroupId() = runTest {
        addTemplate(desayunoA, "lunch", date)
        addTemplate(desayunoA, "lunch", date)

        assertEquals(listOf("group-1", "group-2"), diary.addCalls.map { it.first().group?.id })
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

    private val cooked = MealTemplate(
        id = "t2",
        name = "Pollo con patatas",
        mealSlotId = null,
        items = listOf(
            MealTemplateItem(food("chicken", "Pollo", 110.0), 200.0, null, CookingMethod.Griddle),
            MealTemplateItem(food("potato", "Patatas", 80.0), 100.0, null, CookingMethod.Fried),
            MealTemplateItem(food("rice", "Arroz", 130.0), 100.0, null),
        ),
    )

    @Test
    fun cookedItemsCarryTheirMethodAddedFatAndAdjustedSnapshot() = runTest {
        addTemplate(cooked, "lunch", date)

        val (chicken, potato, rice) = diary.addCalls.single()
        assertEquals(CookingMethod.Griddle, chicken.cookingMethod)
        assertEquals(2.0, chicken.cookingOilRetainedG)
        assertEquals(238.0, chicken.snapshot.kcal)
        assertEquals(CookingMethod.Fried, potato.cookingMethod)
        assertEquals(8.0, potato.cookingOilRetainedG)
        assertEquals(152.0, potato.snapshot.kcal)
        assertEquals(CookingMethod.Raw, rice.cookingMethod)
        assertEquals(0.0, rice.cookingOilRetainedG)
        assertEquals(130.0, rice.snapshot.kcal)
    }

    @Test
    fun changedPreferencesChangeTheNextAdd() = runTest {
        addTemplate(cooked, "lunch", date)
        preferences.prefs = FoodPreferences(fatPctGriddle = 10.0)
        addTemplate(cooked, "lunch", date)

        assertEquals(listOf(2.0, 20.0), diary.addCalls.map { it.first().cookingOilRetainedG })
        assertEquals(2, preferences.reads)
    }

    @Test
    fun aDrinkIsAlwaysRawEvenIfTheTemplateSaysOtherwise() = runTest {
        val drink = MealTemplate(
            "t3",
            "Bebida",
            null,
            listOf(MealTemplateItem(food("milk", "Leche", 50.0, "ml"), 200.0, null, CookingMethod.Fried)),
        )

        addTemplate(drink, "lunch", date)

        val entry = diary.addCalls.single().single()
        assertEquals(CookingMethod.Raw, entry.cookingMethod)
        assertEquals(0.0, entry.cookingOilRetainedG)
        assertEquals(100.0, entry.snapshot.kcal)
    }

    @Test
    fun anAllRawComidaDoesNotReadThePreferences() = runTest {
        addTemplate(desayunoA, "lunch", date)

        assertEquals(0, preferences.reads)
    }

    @Test
    fun failureToReadThePreferencesFailsTheAddWithoutLoggingAnything() = runTest {
        preferences.failure = AppError.Network

        val result = addTemplate(cooked, "lunch", date)

        assertEquals(AppError.Network, result.appErrorOrNull())
        assertTrue(diary.addCalls.isEmpty())
    }
}
