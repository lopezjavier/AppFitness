package io.github.lopezjavier.appfitness.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FoodEnergyCheckTest {
    private fun check(
        kcal: Double,
        protein: Double,
        carbs: Double,
        fat: Double,
        fibre: Double? = null,
        polyols: Double? = null,
        alcohol: Double? = null,
    ) = FoodEnergyCheck.check(kcal, protein, carbs, fat, fibre, polyols, alcohol)

    private fun assertResult(level: EnergyCheckLevel, computed: Double, r: EnergyCheckResult) {
        assertEquals(level, r.level)
        assertEquals(computed, r.computed, 1e-9)
    }

    @Test
    fun epicCaseIsClearlyWrong() {
        val r = check(500.0, 10.0, 30.0, 10.0)
        assertResult(EnergyCheckLevel.ClearlyWrong, 250.0, r)
        assertEquals(-250.0, r.gap, 1e-9)
    }

    @Test
    fun consistentFoodIsOk() = assertResult(EnergyCheckLevel.Ok, 87.0, check(100.0, 5.0, 10.0, 3.0))

    @Test
    fun crispbreadWithFibreIsOk() =
        assertResult(EnergyCheckLevel.Ok, 342.0, check(350.0, 12.0, 45.0, 6.0, fibre = 30.0))

    @Test
    fun crispbreadWithoutFibreMismatches() =
        assertResult(EnergyCheckLevel.Mismatch, 282.0, check(350.0, 12.0, 45.0, 6.0))

    @Test
    fun sweetsWithPolyolsAreOk() =
        assertResult(EnergyCheckLevel.Ok, 236.0, check(240.0, 0.0, 95.0, 0.0, polyols = 90.0))

    @Test
    fun sweetsWithoutPolyolsAreClearlyWrong() {
        val r = check(240.0, 0.0, 95.0, 0.0)
        assertResult(EnergyCheckLevel.ClearlyWrong, 380.0, r)
        assertEquals(140.0, r.gap, 1e-9)
    }

    @Test
    fun zeroKcalWithProteinIsClearlyWrong() =
        assertResult(EnergyCheckLevel.ClearlyWrong, 80.0, check(0.0, 20.0, 0.0, 0.0))

    @Test
    fun coffeeIsSkipped() = assertEquals(EnergyCheckLevel.Skipped, check(2.0, 0.1, 0.3, 0.0).level)

    @Test
    fun redWineWithAlcoholIsOk() = assertResult(EnergyCheckLevel.Ok, 77.3, check(83.0, 0.1, 2.6, 0.0, alcohol = 9.5))

    @Test
    fun redWineWithoutAlcoholIsClearlyWrong() =
        assertEquals(EnergyCheckLevel.ClearlyWrong, check(83.0, 0.1, 2.6, 0.0).level)

    @Test
    fun gapExactlyTwentyAtHundredKcalIsOk() = assertEquals(EnergyCheckLevel.Ok, check(100.0, 0.0, 30.0, 0.0).level)

    @Test
    fun justOverTheMismatchBandMismatches() =
        assertEquals(EnergyCheckLevel.Mismatch, check(100.0, 0.0, 30.0025, 0.0).level)

    @Test
    fun exactClearlyWrongBoundaryIsMismatch() {
        // 0.30 * 100 + 5 = 35 -> carbs 33.75 gives gap 35.0
        assertEquals(EnergyCheckLevel.Mismatch, check(100.0, 0.0, 33.75, 0.0).level)
    }

    @Test
    fun justOverTheClearlyWrongBoundary() =
        assertEquals(EnergyCheckLevel.ClearlyWrong, check(100.0, 0.0, 33.7525, 0.0).level)

    @Test
    fun negativeGapMismatches() = assertEquals(EnergyCheckLevel.Mismatch, check(100.0, 0.0, 17.5, 0.0).level)

    @Test
    fun skipBoundaryAtThirteen() {
        assertEquals(EnergyCheckLevel.Skipped, check(13.0, 0.0, 3.25, 0.0).level)
        assertEquals(EnergyCheckLevel.Ok, check(13.0, 0.0, 3.26, 0.0).level) // computed 13.04: not skipped
        assertEquals(EnergyCheckLevel.ClearlyWrong, check(14.0, 0.0, 0.0, 0.0).level)
    }

    @Test
    fun allZeroIsSkipped() = assertEquals(EnergyCheckLevel.Skipped, check(0.0, 0.0, 0.0, 0.0).level)

    @Test
    fun nullAndZeroOptionalsAreEqual() {
        assertEquals(
            check(100.0, 5.0, 10.0, 3.0),
            check(100.0, 5.0, 10.0, 3.0, fibre = 0.0, polyols = 0.0, alcohol = 0.0),
        )
    }

    @Test
    fun polyolsAboveCarbsAreCapped() = assertEquals(24.0, check(100.0, 0.0, 10.0, 0.0, polyols = 50.0).computed, 1e-9)

    @Test
    fun fibreAndAlcoholFactors() {
        assertEquals(4.0, check(100.0, 0.0, 0.0, 0.0, fibre = 2.0).computed, 1e-9)
        assertEquals(49.0, check(100.0, 0.0, 0.0, 0.0, alcohol = 7.0).computed, 1e-9)
    }

    @Test
    fun computedIsNotRounded() = assertEquals(4.444, check(5.0, 1.111, 0.0, 0.0).computed, 1e-9)

    @Test
    fun issue_ofAFailingFoodCarriesLevelKcalComputedAndGap() {
        val issue = FoodEnergyCheck.issue(kcal = 500.0, protein = 10.0, carbs = 30.0, fat = 10.0)

        assertEquals(EnergyCheckLevel.ClearlyWrong, issue?.result)
        assertEquals(500.0, issue?.kcal)
        assertEquals(250.0, issue?.computed)
        assertEquals(-250.0, issue?.gap)
    }

    @Test
    fun issue_isNullWhenTheFoodPassesIsSkippedOrHasNoKcal() {
        assertNull(FoodEnergyCheck.issue(kcal = 174.0, protein = 6.0, carbs = 15.0, fat = 10.0))
        assertNull(FoodEnergyCheck.issue(kcal = 0.0, protein = 0.0, carbs = 0.0, fat = 0.0))
        assertNull(FoodEnergyCheck.issue(kcal = null, protein = 10.0, carbs = 30.0, fat = 10.0))
    }

    @Test
    fun issue_treatsNullMacrosAsZeroAndIgnoresInvalidValues() {
        assertEquals(EnergyCheckLevel.ClearlyWrong, FoodEnergyCheck.issue(300.0, null, null, null)?.result)
        assertNull(FoodEnergyCheck.issue(300.0, -1.0, 0.0, 0.0))
        assertNull(FoodEnergyCheck.issue(300.0, Double.NaN, 0.0, 0.0))
    }

    @Test
    fun issue_redWineNeedsItsAlcohol() {
        assertEquals(EnergyCheckLevel.ClearlyWrong, FoodEnergyCheck.issue(83.0, 0.1, 2.6, 0.0)?.result)
        assertNull(FoodEnergyCheck.issue(83.0, 0.1, 2.6, 0.0, alcohol = 9.5))
    }
}
