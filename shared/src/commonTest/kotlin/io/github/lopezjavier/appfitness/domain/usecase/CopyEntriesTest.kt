package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.NutritionSnapshot
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class CopyEntriesTest {
    private val source = LocalDate(2026, 10, 19)

    private fun entry(id: String, amount: Double, label: String?, kcal: Double) = FoodLogEntry(
        id = id,
        logDate = source,
        mealSlotId = "lunch",
        foodId = "food-$id",
        foodName = "Food $id",
        amount = amount,
        portionLabel = label,
        baseUnit = "g",
        kcal = kcal,
        proteinG = kcal / 10,
        carbsG = kcal / 5,
        fatG = kcal / 20,
    )

    private val entries = listOf(
        entry("a", 220.0, null, 242.0),
        entry("b", 40.5, "1 taza", 151.2),
        entry("c", 100.0, null, 89.0),
    )

    @Test
    fun copy_keepsAmountsLabelsAndSnapshotsAndSetsTargetSlotAndDate() {
        val target = LocalDate(2026, 10, 21)

        val copies = CopyEntries()(entries, "dinner", target)

        assertEquals(3, copies.size)
        copies.zip(entries).forEach { (copy, original) ->
            assertEquals(target, copy.date)
            assertEquals("dinner", copy.mealSlotId)
            assertEquals(original.foodId, copy.foodId)
            assertEquals(original.foodName, copy.foodName)
            assertEquals(original.amount, copy.amount)
            assertEquals(original.portionLabel, copy.portionLabel)
            assertEquals(
                NutritionSnapshot(original.kcal, original.proteinG, original.carbsG, original.fatG),
                copy.snapshot,
            )
        }
    }

    @Test
    fun copy_ofNothing_isEmpty() {
        assertEquals(emptyList(), CopyEntries()(emptyList(), "dinner", source))
    }

    @Test
    fun repeatYesterday_takesOnlyTheSlotOfYesterdayAndMapsItToToday() {
        val today = LocalDate(2026, 11, 1)
        val yesterday = LocalDate(2026, 10, 31)
        val other = entry("x", 10.0, null, 50.0).copy(mealSlotId = "breakfast")
        val yesterdayEntries = entries.map { it.copy(logDate = yesterday) } + other.copy(logDate = yesterday)

        val result = RepeatYesterdayMeal()(yesterdayEntries, "lunch", today)

        assertEquals(listOf("food-a", "food-b", "food-c"), result.map { it.foodId })
        assertEquals(setOf(today), result.map { it.date }.toSet())
        assertEquals(setOf("lunch"), result.map { it.mealSlotId }.toSet())
        assertEquals(40.5, result[1].amount)
        assertEquals("1 taza", result[1].portionLabel)
    }

    @Test
    fun repeatYesterday_withNothingInTheSlot_isEmpty() {
        val result = RepeatYesterdayMeal()(entries, "dinner", LocalDate(2026, 10, 20))

        assertEquals(emptyList(), result)
    }

    @Test
    fun yesterday_crossesTheMonthBoundary() {
        assertEquals(LocalDate(2026, 10, 31), RepeatYesterdayMeal.yesterdayOf(LocalDate(2026, 11, 1)))
    }
}
