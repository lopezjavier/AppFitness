package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.FoodPortion
import io.github.lopezjavier.appfitness.domain.model.MealCode
import io.github.lopezjavier.appfitness.domain.model.MealSlot
import io.github.lopezjavier.appfitness.domain.model.NutritionPer100
import io.github.lopezjavier.appfitness.domain.model.NutritionSnapshot
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResolvePortionTest {
    private val resolve = ResolvePortion()

    @Test
    fun twoEggsOf55Grams_is110() {
        assertEquals(110.0, resolve(2.0, FoodPortion("p", "huevo", 55.0)))
    }

    @Test
    fun withoutPortion_theRawAmountIsKept() {
        assertEquals(180.0, resolve(180.0, null))
    }

    @Test
    fun resultIsRoundedToOneDecimal() {
        assertEquals(41.3, resolve(1.5, FoodPortion("p", "loncha", 27.55)))
        assertEquals(10.1, resolve(10.14, null))
    }
}

class ScaleNutritionTest {
    private val scale = ScaleNutrition()

    @Test
    fun scalesPer100ToTheAmount() {
        val result = scale(NutritionPer100(110.0, 10.0, 20.0, 5.0), 220.0)

        assertEquals(NutritionSnapshot(242.0, 22.0, 44.0, 11.0), result)
    }

    @Test
    fun rice180Grams_is234Kcal() {
        assertEquals(234.0, scale(NutritionPer100(130.0, 2.7, 28.0, 0.3), 180.0).kcal)
    }

    @Test
    fun drink250Ml_keepsTheHalfUpTenth() {
        assertEquals(112.5, scale(NutritionPer100(45.0, 0.0, 11.0, 0.0), 250.0).kcal)
    }

    @Test
    fun zeroKcalFood_isZero() {
        assertEquals(NutritionSnapshot(0.0, 0.0, 0.0, 0.0), scale(NutritionPer100(0.0, 0.0, 0.0, 0.0), 300.0))
    }

    @Test
    fun roundsToTenthHalfUp() {
        assertEquals(0.4, scale(NutritionPer100(3.5, 0.0, 0.0, 0.0), 10.0).kcal)
        assertEquals(0.3, scale(NutritionPer100(3.4, 0.0, 0.0, 0.0), 10.0).kcal)
    }
}

class ValidateQuantityTest {
    private val validate = ValidateQuantity()

    @Test
    fun zeroAndNegative_areTooLow() {
        assertEquals(QuantityCheck.TooLow, validate(0.0))
        assertEquals(QuantityCheck.TooLow, validate(-1.0))
    }

    @Test
    fun aboveFiveThousand_isTooHigh() {
        assertEquals(QuantityCheck.TooHigh, validate(5000.1))
        assertEquals(QuantityCheck.TooHigh, validate(5001.0))
    }

    @Test
    fun limitsAndOneDecimal_areValid() {
        assertEquals(QuantityCheck.Valid, validate(0.1))
        assertEquals(QuantityCheck.Valid, validate(5000.0))
        assertEquals(QuantityCheck.Valid, validate(180.0))
        assertEquals(QuantityCheck.Valid, validate(12.3))
    }

    @Test
    fun moreThanOneDecimal_isRejected() {
        assertEquals(QuantityCheck.TooManyDecimals, validate(12.34))
        assertEquals(QuantityCheck.TooManyDecimals, validate(0.05))
    }
}

class DefaultMealSlotTest {
    private val pick = DefaultMealSlot()
    private val all = listOf(
        MealSlot("b", MealCode.Breakfast, "Desayuno", 0),
        MealSlot("l", MealCode.Lunch, "Comida", 1),
        MealSlot("s", MealCode.Snack, "Merienda", 2),
        MealSlot("d", MealCode.Dinner, "Cena", 3),
    )

    private fun at(hour: Int, minute: Int, slots: List<MealSlot> = all) = pick(LocalTime(hour, minute), slots)?.id

    @Test
    fun boundariesSelectTheTimeSlot() {
        assertEquals("b", at(10, 59))
        assertEquals("l", at(11, 0))
        assertEquals("l", at(15, 59))
        assertEquals("s", at(16, 0))
        assertEquals("s", at(19, 59))
        assertEquals("d", at(20, 0))
        assertEquals("b", at(0, 0))
    }

    @Test
    fun disabledSlot_fallsBackToTheNearestLaterEnabledOne() {
        assertEquals("d", at(17, 0, all.filter { it.id != "s" }))
    }

    @Test
    fun noLaterSlot_fallsBackToTheFirstEnabled() {
        assertEquals("b", at(21, 0, all.take(2)))
    }

    @Test
    fun slotsWithoutCodes_pickTheFirstEnabled() {
        val custom = listOf(MealSlot("x", null, "Pre-entreno", 5), MealSlot("y", null, "Post", 6))

        assertEquals("x", at(13, 0, custom))
    }

    @Test
    fun noSlots_isNull() {
        assertNull(at(13, 0, emptyList()))
    }
}
