package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.DiaryGoals
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.MealCode
import io.github.lopezjavier.appfitness.domain.model.MealSlot
import io.github.lopezjavier.appfitness.domain.model.MealTotals
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BuildDiaryDayTest {
    private val date = LocalDate(2026, 10, 19)
    private val build = BuildDiaryDay()
    private val slots = listOf(
        MealSlot("dinner", MealCode.Dinner, "Cena", 3),
        MealSlot("breakfast", MealCode.Breakfast, "Desayuno", 0),
        MealSlot("snack", MealCode.Snack, "Merienda", 2, enabled = false),
        MealSlot("lunch", MealCode.Lunch, "Comida", 1),
    )
    private val goals = NutritionGoals(GoalStrategy.Maintenance, 0, 2400, MacroSplit(30, 45, 25))

    @Test
    fun sections_areTheEnabledSlotsInPositionOrder() {
        val day = build(date, slots, emptyList(), goals)

        assertEquals(listOf("breakfast", "lunch", "dinner"), day.meals.map { it.slot.id })
    }

    @Test
    fun entries_goToTheirSectionWithItsTotals() {
        val entries = listOf(
            logEntry(id = "a", slotId = "lunch", kcal = 500.5, protein = 30.0),
            logEntry(id = "b", slotId = "lunch", kcal = 100.0, protein = 5.5),
            logEntry(id = "c", slotId = "dinner", kcal = 300.0),
        )

        val day = build(date, slots, entries, goals)

        val lunch = day.meals.first { it.slot.id == "lunch" }
        assertEquals(listOf("a", "b"), lunch.entries.map { it.id })
        assertEquals(MealTotals(600.5, 35.5, 0.0, 0.0), lunch.totals)
        assertTrue(day.meals.first { it.slot.id == "breakfast" }.entries.isEmpty())
        assertEquals(900.5, day.totals.kcal)
    }

    @Test
    fun entriesOfAnUnknownSlot_countInTheDayTotalButHaveNoSection() {
        val entries = listOf(
            logEntry(id = "a", slotId = "breakfast", kcal = 200.0),
            logEntry(id = "x", slotId = "snack", kcal = 150.0),
        )

        val day = build(date, slots, entries, goals)

        assertEquals(3, day.meals.size)
        assertEquals(350.0, day.totals.kcal)
    }

    @Test
    fun gramTargets_comeFromCalculateMacroTargets() {
        val day = build(date, slots, emptyList(), goals)

        assertEquals(DiaryGoals(2400, 180, 270, 67), day.goals)
    }

    @Test
    fun withoutGoals_thereIsNoGoalsInfo() {
        assertNull(build(date, slots, emptyList(), null).goals)
    }

    @Test
    fun emptyDay_hasZeroTotals() {
        val day = build(date, slots, emptyList(), goals)

        assertEquals(date, day.date)
        assertEquals(MealTotals.ZERO, day.totals)
    }
}
