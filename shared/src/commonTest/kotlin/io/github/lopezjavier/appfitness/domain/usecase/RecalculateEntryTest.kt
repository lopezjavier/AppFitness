package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.FoodPortion
import io.github.lopezjavier.appfitness.domain.model.NutritionPer100
import io.github.lopezjavier.appfitness.domain.model.NutritionSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

class RecalculateEntryTest {
    private val egg = FoodPortion("p-egg", "huevo", 55.0)
    private val chicken = Food(
        id = "chicken",
        name = "Pechuga de pollo",
        brand = null,
        baseUnit = "g",
        nutrition = NutritionPer100(kcal = 110.0, proteinG = 23.0, carbsG = 0.0, fatG = 1.2),
        servingLabel = null,
        servingAmount = null,
        portions = listOf(egg),
        imageUrl = null,
    )
    private val recalculate = RecalculateEntry(ResolvePortion(), ScaleNutrition(), ValidateQuantity())

    @Test
    fun changingTheAmountFrom220To150gScalesTheSnapshot() {
        val original = recalculate(chicken, 220.0, null) as EntryRecalculation.Valid
        val edited = recalculate(chicken, 150.0, null) as EntryRecalculation.Valid

        assertEquals(242.0, original.snapshot.kcal)
        assertEquals(150.0, edited.amount)
        assertEquals(NutritionSnapshot(165.0, 34.5, 0.0, 1.8), edited.snapshot)
    }

    @Test
    fun aPortionMultipliesItsAmountByTheCount() {
        val result = recalculate(chicken, 2.0, egg) as EntryRecalculation.Valid

        assertEquals(110.0, result.amount)
        assertEquals(121.0, result.snapshot.kcal)
    }

    @Test
    fun switchingBackToTheRawUnitUsesTheTypedAmountAsIs() {
        val result = recalculate(chicken, 80.0, null) as EntryRecalculation.Valid

        assertEquals(80.0, result.amount)
        assertEquals(88.0, result.snapshot.kcal)
    }

    @Test
    fun zeroOrNegativeIsTooLow() {
        assertEquals(EntryRecalculation.Invalid(QuantityCheck.TooLow), recalculate(chicken, 0.0, null))
        assertEquals(EntryRecalculation.Invalid(QuantityCheck.TooLow), recalculate(chicken, -5.0, null))
    }

    @Test
    fun aboveThe5000LimitIsTooHigh() {
        assertEquals(EntryRecalculation.Invalid(QuantityCheck.TooHigh), recalculate(chicken, 5000.1, null))
    }

    @Test
    fun aPortionCountWhoseResolvedAmountExceedsTheLimitIsTooHigh() {
        assertEquals(EntryRecalculation.Invalid(QuantityCheck.TooHigh), recalculate(chicken, 100.0, egg))
    }

    @Test
    fun moreThanOneDecimalIsRejected() {
        assertEquals(EntryRecalculation.Invalid(QuantityCheck.TooManyDecimals), recalculate(chicken, 10.25, null))
    }
}
