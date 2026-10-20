package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.MealTotals
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

internal fun logEntry(
    id: String = "e",
    slotId: String = "s1",
    kcal: Double = 0.0,
    protein: Double = 0.0,
    carbs: Double = 0.0,
    fat: Double = 0.0,
    date: LocalDate = LocalDate(2026, 10, 19),
) = FoodLogEntry(
    id = id,
    logDate = date,
    mealSlotId = slotId,
    foodId = "f-$id",
    foodName = "Food $id",
    amount = 100.0,
    portionLabel = null,
    baseUnit = "g",
    kcal = kcal,
    proteinG = protein,
    carbsG = carbs,
    fatG = fat,
)

class DiaryTotalsTest {
    @Test
    fun emptyList_isZeros() {
        assertEquals(MealTotals.ZERO, DiaryTotals.sum(emptyList()))
    }

    @Test
    fun tenEntriesOfAPointOne_sumToExactlyOne() {
        val entries = List(10) { logEntry(id = "$it", kcal = 0.1, protein = 0.1, carbs = 0.1, fat = 0.1) }

        assertEquals(MealTotals(1.0, 1.0, 1.0, 1.0), DiaryTotals.sum(entries))
    }

    @Test
    fun planExample_sumsTheDay() {
        val entries = listOf(
            logEntry(id = "a", kcal = 700.5, protein = 50.2, carbs = 80.0, fat = 15.1),
            logEntry(id = "b", kcal = 1139.5, protein = 91.8, carbs = 130.0, fat = 32.9),
        )

        assertEquals(MealTotals(1840.0, 142.0, 210.0, 48.0), DiaryTotals.sum(entries))
    }

    @Test
    fun remaining_isTargetMinusConsumed() {
        assertEquals(560, DiaryTotals.remainingKcal(2400, 1840.0))
    }

    @Test
    fun remaining_usesTheHalfUpRoundedConsumed() {
        assertEquals(560, DiaryTotals.remainingKcal(2400, 1839.6))
        assertEquals(560, DiaryTotals.remainingKcal(2400, 1839.5))
    }

    @Test
    fun remaining_isNegativeWhenExceeded() {
        assertEquals(-150, DiaryTotals.remainingKcal(2400, 2550.0))
    }

    @Test
    fun planExample_macroPercentages() {
        val targets = CalculateMacroTargets(2400, MacroSplit(30, 45, 25))

        assertEquals(180, targets.proteinG)
        assertEquals(270, targets.carbsG)
        assertEquals(67, targets.fatG)
        assertEquals(79, DiaryTotals.macroPercent(142.0, targets.proteinG))
        assertEquals(78, DiaryTotals.macroPercent(210.0, targets.carbsG))
        assertEquals(72, DiaryTotals.macroPercent(48.0, targets.fatG))
    }

    @Test
    fun percent_roundsHalfUp() {
        assertEquals(50, DiaryTotals.macroPercent(100.9, 200))
        assertEquals(51, DiaryTotals.macroPercent(101.0, 200))
    }

    @Test
    fun percent_isNotCapped() {
        assertEquals(150, DiaryTotals.macroPercent(300.0, 200))
    }

    @Test
    fun percent_withAZeroTarget_isZero() {
        assertEquals(0, DiaryTotals.macroPercent(10.0, 0))
    }
}
