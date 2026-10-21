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
        val errors = NewFoodValidation.validate(food().copy(micronutrients = NewMicronutrients(0.0, 9999.99, 2.5, 0.4)))
        assertTrue(errors.isValid)
    }

    @Test
    fun micronutrients_negativeNanAndTooLargeAreRejectedPerField() {
        val errors = NewFoodValidation.validate(
            food().copy(
                micronutrients = NewMicronutrients(
                    saturatedFat = -0.1,
                    sugars = Double.NaN,
                    fiber = 10000.0,
                    salt = null,
                ),
            ),
        )
        assertEquals(NewFoodError.Negative, errors.saturatedFat)
        assertEquals(NewFoodError.NotANumber, errors.sugars)
        assertEquals(NewFoodError.TooLarge, errors.fiber)
        assertNull(errors.salt)
        assertEquals(false, errors.isValid)
    }

    @Test
    fun tenPortionsAllowed_elevenNot() {
        val ten = List(10) { NewPortion("p$it", 10.0) }
        assertTrue(NewFoodValidation.validate(food(portions = ten)).isValid)
        assertTrue(NewFoodValidation.validate(food(portions = ten + NewPortion("p", 1.0))).tooManyPortions)
    }
}
