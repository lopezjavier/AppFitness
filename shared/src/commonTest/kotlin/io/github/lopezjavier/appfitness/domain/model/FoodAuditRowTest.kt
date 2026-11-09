package io.github.lopezjavier.appfitness.domain.model

import io.github.lopezjavier.appfitness.domain.usecase.EnergyCheckLevel
import kotlin.test.Test
import kotlin.test.assertEquals

class FoodAuditRowTest {
    private fun row(kcal: Double, computed: Double) =
        FoodAuditRow("f", "Tarta", null, kcal, computed, computed - kcal, EnergyCheckLevel.ClearlyWrong)

    @Test
    fun gapWhole_isDerivedFromTheRoundedFiguresSoTheThreeNumbersAddUp() {
        val row = row(kcal = 500.0, computed = 249.6)

        assertEquals(500, row.declaredWhole)
        assertEquals(250, row.macrosWhole)
        assertEquals(-250, row.gapWhole)
    }

    @Test
    fun gapWhole_isPositiveWhenTheMacrosAreHigher() {
        val row = row(kcal = 260.4, computed = 300.0)

        assertEquals(260, row.declaredWhole)
        assertEquals(40, row.gapWhole)
    }
}
