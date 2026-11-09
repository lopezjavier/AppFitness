package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.FoodPortion
import io.github.lopezjavier.appfitness.domain.model.Micronutrient
import io.github.lopezjavier.appfitness.domain.model.MicronutrientKind
import io.github.lopezjavier.appfitness.domain.model.MicronutrientUnit
import io.github.lopezjavier.appfitness.domain.model.Micronutrients
import io.github.lopezjavier.appfitness.domain.model.NewPortion
import io.github.lopezjavier.appfitness.domain.model.NutritionPer100
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OffFoodToNewFoodDraftTest {
    private val off = Food(
        id = "off1",
        name = "Galletas",
        brand = "Marca",
        baseUnit = "g",
        nutrition = NutritionPer100(480.0, 6.0, 70.0, 20.0),
        servingLabel = null,
        servingAmount = null,
        portions = listOf(FoodPortion("p1", "1 galleta", 8.0), FoodPortion("p2", "1 paquete", 40.0)),
        imageUrl = "https://img",
        isOff = true,
        barcode = "8410000000001",
    )

    @Test
    fun copiesIdentityNutritionPortionsAndBarcode() {
        val draft = OffFoodToNewFoodDraft(off, Micronutrients.Empty, alcohol = null)

        assertEquals("Galletas", draft.name)
        assertEquals("Marca", draft.brand)
        assertEquals("g", draft.baseUnit)
        assertEquals(480.0, draft.kcal)
        assertEquals(6.0, draft.protein)
        assertEquals(70.0, draft.carbs)
        assertEquals(20.0, draft.fat)
        assertEquals(listOf(NewPortion("1 galleta", 8.0), NewPortion("1 paquete", 40.0)), draft.portions)
        assertEquals("8410000000001", draft.barcode)
    }

    @Test
    fun carriesFibrePolyolsAndAlcoholButNoOtherNutrient() {
        val extras = Micronutrients(
            listOf(
                Micronutrient(MicronutrientKind.Fiber, 3.5, MicronutrientUnit.G),
                Micronutrient(MicronutrientKind.Polyols, 12.0, MicronutrientUnit.G),
                Micronutrient(MicronutrientKind.Sodium, 0.4, MicronutrientUnit.G),
            ),
        )

        val draft = OffFoodToNewFoodDraft(off, extras, alcohol = 9.5)

        assertEquals(
            mapOf(MicronutrientKind.Fiber to 3.5, MicronutrientKind.Polyols to 12.0),
            draft.micronutrients.values,
        )
        assertEquals(9.5, draft.alcohol)
    }

    @Test
    fun missingValuesStayNullAndABlankBarcodeIsDropped() {
        val draft =
            OffFoodToNewFoodDraft(
                off.copy(brand = null, barcode = null, portions = emptyList()),
                Micronutrients.Empty,
                null,
            )

        assertNull(draft.brand)
        assertNull(draft.barcode)
        assertNull(draft.alcohol)
        assertEquals(emptyMap(), draft.micronutrients.values)
        assertEquals(emptyList(), draft.portions)
    }
}
