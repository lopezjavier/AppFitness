package io.github.lopezjavier.appfitness.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NewFoodValidationTest {
    private fun food(
        name: String = "Tortilla de patatas casera",
        brand: String? = null,
        kcal: Double = 190.0,
        protein: Double = 7.0,
        carbs: Double = 15.0,
        fat: Double = 11.0,
        portions: List<NewPortion> = emptyList(),
    ) = NewFood(name, brand, "g", NutritionPer100(kcal, protein, carbs, fat), null, portions)

    @Test
    fun tortillaExample_isValid() {
        assertTrue(NewFoodValidation.validate(food()).isValid)
    }

    @Test
    fun blankName_isRequired() {
        assertEquals(NewFoodError.Required, NewFoodValidation.validate(food(name = "   ")).name)
        assertEquals(NewFoodError.Required, NewFoodValidation.validate(food(name = "")).name)
    }

    @Test
    fun nameLength_200ok_201TooLong() {
        assertNull(NewFoodValidation.validate(food(name = "a".repeat(200))).name)
        assertEquals(NewFoodError.TooLong, NewFoodValidation.validate(food(name = "a".repeat(201))).name)
    }

    @Test
    fun brandTooLong() {
        assertEquals(NewFoodError.TooLong, NewFoodValidation.validate(food(brand = "b".repeat(201))).brand)
    }

    @Test
    fun negativeKcal_isNegative_zeroIsAllowed() {
        assertEquals(NewFoodError.Negative, NewFoodValidation.validate(food(kcal = -1.0)).kcal)
        assertNull(NewFoodValidation.validate(food(kcal = 0.0)).kcal)
    }

    @Test
    fun nanAndInfinity_areNotANumber() {
        assertEquals(NewFoodError.NotANumber, NewFoodValidation.validate(food(protein = Double.NaN)).protein)
        assertEquals(NewFoodError.NotANumber, NewFoodValidation.validate(food(carbs = Double.POSITIVE_INFINITY)).carbs)
    }

    @Test
    fun macroAboveMaximum_isTooLarge() {
        assertEquals(NewFoodError.TooLarge, NewFoodValidation.validate(food(fat = 10000.0)).fat)
        assertNull(NewFoodValidation.validate(food(fat = 9999.99)).fat)
    }

    @Test
    fun portionAmount_zeroAndNegativeAreInvalid() {
        val errors = NewFoodValidation.validate(
            food(portions = listOf(NewPortion("a", 0.0), NewPortion("b", 5.0), NewPortion("c", -2.0))),
        )
        assertEquals(mapOf(0 to PortionError.AmountInvalid, 2 to PortionError.AmountInvalid), errors.portions)
    }

    @Test
    fun portionLabel_blankOrOver100IsInvalid() {
        val errors = NewFoodValidation.validate(
            food(
                portions = listOf(
                    NewPortion(" ", 5.0),
                    NewPortion("x".repeat(101), 5.0),
                    NewPortion("x".repeat(100), 5.0),
                ),
            ),
        )
        assertEquals(mapOf(0 to PortionError.LabelInvalid, 1 to PortionError.LabelInvalid), errors.portions)
    }

    @Test
    fun portionAmountMaximum() {
        assertTrue(NewFoodValidation.validate(food(portions = listOf(NewPortion("a", 9999.9)))).isValid)
        assertEquals(
            mapOf(0 to PortionError.AmountInvalid),
            NewFoodValidation.validate(food(portions = listOf(NewPortion("a", 10000.0)))).portions,
        )
    }

    @Test
    fun untypedMicronutrients_areValid() {
        assertTrue(NewFoodValidation.validate(food()).isValid)
    }

    @Test
    fun typedMicronutrients_zeroAndMaximumAreValid() {
        val values = mapOf(
            MicronutrientKind.SaturatedFat to 0.0,
            MicronutrientKind.Sugars to 9999.99,
            MicronutrientKind.Fiber to 2.5,
        )
        assertTrue(NewFoodValidation.validate(food().copy(micronutrients = NewMicronutrients(values))).isValid)
    }

    @Test
    fun everyEditableKindIsValidatedInItsLabelUnit() {
        MicronutrientKind.editableKinds.forEach { kind ->
            val ok = NewMicronutrients(mapOf(kind to 5.0))
            assertTrue(NewFoodValidation.validate(food().copy(micronutrients = ok)).isValid, kind.name)
            val bad = NewMicronutrients(mapOf(kind to -1.0))
            val errors = NewFoodValidation.validate(food().copy(micronutrients = bad))
            assertEquals(NewFoodError.Negative, errors.nutrients[kind])
        }
    }

    @Test
    fun theLimitAppliesToTheGramsAfterUnitConversion() {
        val big = NewMicronutrients(mapOf(MicronutrientKind.Selenium to 9_999_990.0))
        assertTrue(NewFoodValidation.validate(food().copy(micronutrients = big)).isValid)
        val tooBig = NewMicronutrients(mapOf(MicronutrientKind.Selenium to 1e10))
        assertEquals(
            NewFoodError.TooLarge,
            NewFoodValidation.validate(food().copy(micronutrients = tooBig)).nutrients[MicronutrientKind.Selenium],
        )
    }

    @Test
    fun labelUnitsConvertToGrams() {
        assertEquals(0.001, MicronutrientUnit.MG.toGrams(1.0))
        assertEquals(0.000001, MicronutrientUnit.UG.toGrams(1.0))
        assertEquals(2.5, MicronutrientUnit.G.toGrams(2.5))
        assertEquals(1500.0, MicronutrientUnit.MG.fromGrams(1.5))
        assertEquals(MicronutrientUnit.UG, MicronutrientKind.VitaminB12.labelUnit)
        assertEquals(MicronutrientUnit.MG, MicronutrientKind.VitaminC.labelUnit)
    }

    @Test
    fun updatedFoodSharesTheRules() {
        val updated = UpdatedFood(
            id = "f1",
            name = " ",
            brand = null,
            nutrition = NutritionPer100(-1.0, 0.0, 0.0, 0.0),
            portions = listOf(NewPortion("", 1.0)),
            micronutrients = NewMicronutrients(mapOf(MicronutrientKind.VitaminC to -2.0)),
        )
        val errors = NewFoodValidation.validate(updated)
        assertEquals(NewFoodError.Required, errors.name)
        assertEquals(NewFoodError.Negative, errors.kcal)
        assertEquals(mapOf(0 to PortionError.LabelInvalid), errors.portions)
        assertEquals(NewFoodError.Negative, errors.nutrients[MicronutrientKind.VitaminC])
    }

    @Test
    fun micronutrients_negativeNanAndTooLargeAreRejectedPerField() {
        val errors = NewFoodValidation.validate(
            food().copy(
                micronutrients = NewMicronutrients(
                    mapOf(
                        MicronutrientKind.SaturatedFat to -0.1,
                        MicronutrientKind.Sugars to Double.NaN,
                        MicronutrientKind.Fiber to 10000.0,
                        MicronutrientKind.Salt to 0.4,
                    ),
                ),
            ),
        )
        assertEquals(
            mapOf(
                MicronutrientKind.SaturatedFat to NewFoodError.Negative,
                MicronutrientKind.Sugars to NewFoodError.NotANumber,
                MicronutrientKind.Fiber to NewFoodError.TooLarge,
            ),
            errors.nutrients,
        )
        assertEquals(false, errors.isValid)
    }

    @Test
    fun tenPortionsAllowed_elevenNot() {
        val ten = List(10) { NewPortion("p$it", 10.0) }
        assertTrue(NewFoodValidation.validate(food(portions = ten)).isValid)
        assertTrue(NewFoodValidation.validate(food(portions = ten + NewPortion("p", 1.0))).tooManyPortions)
    }
}
