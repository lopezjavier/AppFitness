package io.github.lopezjavier.appfitness.feature.diary

import kotlin.test.Test
import kotlin.test.assertEquals

class FoodEntryDetailTest {
    @Test
    fun portionWithBaseAmount_readsPortionThenAmountInBrackets() {
        assertEquals("1 vaso (200 ml)", FoodEntryDetail.format("1 vaso", 200.0, "ml", null))
    }

    @Test
    fun countedPortion_readsTheCountThenTheAmount() {
        assertEquals("2 × huevo (120 g)", FoodEntryDetail.format("2 × huevo", 120.0, "g", null))
    }

    @Test
    fun rawBaseUnit_readsAmountAndUnit() {
        assertEquals("220 g", FoodEntryDetail.format(null, 220.0, "g", null))
    }

    @Test
    fun cookedEntry_appendsTheCookingLabel() {
        assertEquals("120 g · A la plancha", FoodEntryDetail.format(null, 120.0, "g", "A la plancha"))
    }

    @Test
    fun cookedPortion_appendsTheCookingLabelAfterTheBrackets() {
        assertEquals("1 vaso (200 ml) · Frito", FoodEntryDetail.format("1 vaso", 200.0, "ml", "Frito"))
    }

    @Test
    fun portionWithoutBaseUnit_showsOnlyThePortion() {
        assertEquals("1 unidad", FoodEntryDetail.format("1 unidad", 50.0, null, null))
    }

    @Test
    fun amountWithoutUnit_showsTheNumberOnly() {
        assertEquals("50", FoodEntryDetail.format(null, 50.0, null, null))
    }

    @Test
    fun decimalAmount_usesACommaAndOneDecimal() {
        assertEquals("12,5 g", FoodEntryDetail.format(null, 12.5, "g", null))
    }

    @Test
    fun largeAmount_usesSpanishGrouping() {
        assertEquals("1.250 g", FoodEntryDetail.format(null, 1250.0, "g", null))
    }
}
