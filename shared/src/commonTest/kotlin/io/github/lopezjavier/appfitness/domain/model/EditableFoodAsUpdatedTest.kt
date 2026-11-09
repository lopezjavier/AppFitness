package io.github.lopezjavier.appfitness.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EditableFoodAsUpdatedTest {
    private val editable = EditableFood(
        food = Food(
            id = "c1",
            name = "Vino",
            brand = "Rioja",
            baseUnit = "ml",
            nutrition = NutritionPer100(85.0, 0.1, 2.6, 0.0),
            servingLabel = null,
            servingAmount = null,
            portions = listOf(FoodPortion("p1", "copa", 150.0)),
            imageUrl = null,
            isManual = true,
        ),
        micronutrients = mapOf(MicronutrientKind.Fiber to 0.02),
        inUse = true,
        alcoholG = 9.5,
        savedAnyway = false,
    )

    @Test
    fun keepsEveryStoredValueAndOnlySetsTheFlag() {
        val updated = editable.asUpdated(savedAnyway = true)

        assertEquals("c1", updated.id)
        assertEquals("Vino", updated.name)
        assertEquals("Rioja", updated.brand)
        assertEquals("ml", updated.baseUnit)
        assertEquals(editable.food.nutrition, updated.nutrition)
        assertEquals(listOf(NewPortion("copa", 150.0)), updated.portions)
        assertEquals(9.5, updated.alcoholG)
        assertTrue(updated.savedAnyway)
    }

    @Test
    fun micronutrientsComeBackInTheirLabelUnit() {
        val sodium = editable.asUpdated(false).micronutrients.values.getValue(MicronutrientKind.Fiber)

        assertEquals(MicronutrientKind.Fiber.labelUnit.fromGrams(0.02), sodium)
    }
}
