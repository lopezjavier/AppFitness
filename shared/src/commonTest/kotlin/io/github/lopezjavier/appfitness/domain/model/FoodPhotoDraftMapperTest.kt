package io.github.lopezjavier.appfitness.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FoodPhotoDraftMapperTest {
    private val tortilla = FoodPhotoDraft(
        name = "Tortilla de patatas",
        brand = null,
        baseUnit = "g",
        kcal = 180.0,
        protein = 6.0,
        carbs = 15.0,
        fat = 10.0,
        micronutrients = mapOf("fiber_100g" to 1.5, "vitamin-c_100g" to 0.0125),
        estimatedGrams = 250.0,
        confidence = FoodPhotoConfidence.Medium,
    )

    @Test
    fun dishWithGrams_prefillsValuesAndTheOnePortion() {
        val draft = tortilla.toNewFoodDraft(FoodPhotoMode.Dish)

        assertEquals("Tortilla de patatas", draft.name)
        assertEquals("g", draft.baseUnit)
        assertEquals(180.0, draft.kcal)
        assertEquals(6.0, draft.protein)
        assertEquals(15.0, draft.carbs)
        assertEquals(10.0, draft.fat)
        assertEquals(listOf(NewPortion("1 ración", 250.0)), draft.portions)
    }

    @Test
    fun micronutrients_areConvertedFromGramsToTheLabelUnitOfEachNutrient() {
        val values = tortilla.toNewFoodDraft(FoodPhotoMode.Dish).micronutrients.values

        assertEquals(1.5, values.getValue(MicronutrientKind.Fiber), 1e-9) // label nutrient: grams
        assertEquals(12.5, values.getValue(MicronutrientKind.VitaminC), 1e-9) // 0.0125 g = 12.5 mg
    }

    @Test
    fun dishWithoutGrams_hasNoPortion() {
        val draft = tortilla.copy(estimatedGrams = null).toNewFoodDraft(FoodPhotoMode.Dish)

        assertTrue(draft.portions.isEmpty())
    }

    @Test
    fun dishWithZeroGrams_hasNoPortion() {
        val draft = tortilla.copy(estimatedGrams = 0.0).toNewFoodDraft(FoodPhotoMode.Dish)

        assertTrue(draft.portions.isEmpty())
    }

    @Test
    fun label_keepsAbsentValuesNullAndHasNoPortion() {
        val label = FoodPhotoDraft(
            name = "Yogur natural",
            brand = "Danone",
            baseUnit = "g",
            kcal = 62.0,
            protein = null,
            carbs = 4.7,
            fat = null,
            micronutrients = emptyMap(),
            estimatedGrams = 125.0,
            confidence = null,
        )

        val draft = label.toNewFoodDraft(FoodPhotoMode.Label)

        assertEquals(62.0, draft.kcal)
        assertNull(draft.protein)
        assertEquals(4.7, draft.carbs)
        assertNull(draft.fat)
        assertTrue(draft.portions.isEmpty())
        assertEquals("Danone", draft.brand)
    }

    @Test
    fun barcode_mapsNameAndBrandWithoutPortionOrBarcode() {
        val product = tortilla.copy(name = "Cola Cao", brand = "Nutrexpa", estimatedGrams = 100.0)

        val draft = product.toNewFoodDraft(FoodPhotoMode.Barcode)

        assertEquals("Cola Cao", draft.name)
        assertEquals("Nutrexpa", draft.brand)
        assertTrue(draft.portions.isEmpty())
        assertNull(draft.barcode)
    }

    @Test
    fun mlBaseUnit_isKept() {
        val draft = tortilla.copy(name = "Leche", baseUnit = "ml").toNewFoodDraft(FoodPhotoMode.Label)

        assertEquals("ml", draft.baseUnit)
    }

    @Test
    fun unknownMicronutrientKey_isIgnored() {
        val withUnknown = tortilla.copy(micronutrients = mapOf("unobtainium_100g" to 3.0, "iron_100g" to 0.002))

        val values = withUnknown.toNewFoodDraft(FoodPhotoMode.Dish).micronutrients.values

        assertEquals(setOf(MicronutrientKind.Iron), values.keys)
        assertEquals(2.0, values.getValue(MicronutrientKind.Iron), 1e-9)
    }
}
