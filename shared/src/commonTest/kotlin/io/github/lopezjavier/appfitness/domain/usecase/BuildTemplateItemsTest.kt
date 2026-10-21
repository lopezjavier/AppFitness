package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.NewTemplateItem
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuildTemplateItemsTest {
    private val build = BuildTemplateItems()

    private fun entry(id: String, foodId: String, amount: Double, portion: String? = null) = FoodLogEntry(
        id = id,
        logDate = LocalDate(2026, 10, 21),
        mealSlotId = "breakfast",
        foodId = foodId,
        foodName = "Food $foodId",
        amount = amount,
        portionLabel = portion,
        baseUnit = "g",
        kcal = 100.0,
        proteinG = 1.0,
        carbsG = 1.0,
        fatG = 1.0,
    )

    @Test
    fun keepsOrderFoodAndAmount() {
        val items = build(listOf(entry("e1", "eggs", 120.0), entry("e2", "shake", 300.0)))

        assertEquals(listOf(NewTemplateItem("eggs", 120.0, null), NewTemplateItem("shake", 300.0, null)), items)
    }

    @Test
    fun keepsThePortionLabel() {
        val items = build(listOf(entry("e1", "shake", 300.0, portion = "1 vaso")))

        assertEquals("1 vaso", items.single().portionLabel)
    }

    @Test
    fun emptySection_hasNothingToSave() {
        assertTrue(build(emptyList()).isEmpty())
    }
}
