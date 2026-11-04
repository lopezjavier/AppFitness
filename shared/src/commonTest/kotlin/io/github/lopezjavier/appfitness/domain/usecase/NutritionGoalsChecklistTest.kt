package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.Sex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NutritionGoalsChecklistTest {
    // 80 kg male, expenditure 2600: cut target 2160 (-440, 0.5 %/week), protein 2.2 g/kg, fat 25 %.
    private fun check(
        kcal: Int = 2160,
        protein: Int = 178,
        fat: Int = 60,
        weight: Double = 80.0,
        sex: Sex = Sex.Male,
        phase: Phase = Phase.Cut,
        expenditure: Double? = 2600.0,
    ) = NutritionGoalsChecklist(GoalTargets(kcal, protein, fat), weight, sex, phase, expenditure)

    @Test
    fun coachPlanFigures_passEveryRow() {
        val result = check()

        assertTrue(result.allPassed)
        assertEquals(4, result.checks.size)
        assertEquals(
            listOf(GoalCheckKey.N1Floor, GoalCheckKey.N2Rate, GoalCheckKey.N3Protein, GoalCheckKey.N4Fat),
            result.checks.map { it.key },
        )
    }

    @Test
    fun n1_kcalAtFloorPasses_oneBelowFails() {
        assertTrue(check(kcal = 1500, expenditure = null, fat = 60).passed(GoalCheckKey.N1Floor))
        val below = check(kcal = 1499, expenditure = null).checks.first { it.key == GoalCheckKey.N1Floor }

        assertFalse(below.passed)
        assertEquals(1499, below.kcal)
        assertEquals(1500, below.floorKcal)
    }

    @Test
    fun n1_usesTheFemaleFloor() {
        assertTrue(check(kcal = 1200, sex = Sex.Female, expenditure = null).passed(GoalCheckKey.N1Floor))
        assertFalse(check(kcal = 1199, sex = Sex.Female, expenditure = null).passed(GoalCheckKey.N1Floor))
    }

    @Test
    fun n2_cutRateInsideRange_passes() {
        val result = check(kcal = 2160, expenditure = 2600.0)

        assertTrue(result.passed(GoalCheckKey.N2Rate))
    }

    @Test
    fun n2_deficitOf500Passes_501Fails() {
        // 70 kg: 500 kcal/day = 0.65 %/week, inside 0.5-1.0; only the 500 kcal cap tells 500 from 501.
        val atCap = check(kcal = 2100, expenditure = 2600.0, weight = 70.0)
        val overCap = check(kcal = 2099, expenditure = 2600.0, weight = 70.0)

        assertTrue(atCap.passed(GoalCheckKey.N2Rate))
        val failed = overCap.checks.first { it.key == GoalCheckKey.N2Rate }
        assertFalse(failed.passed)
        assertTrue(failed.deficitOverCap)
    }

    @Test
    fun n2_rateBelowCutMinimum_fails() {
        // 80 kg, -200 kcal/day = 0.22 %/week of loss, under the 0.5 minimum.
        val row = check(kcal = 2400, expenditure = 2600.0).checks.first { it.key == GoalCheckKey.N2Rate }

        assertFalse(row.passed)
        assertFalse(row.deficitOverCap)
        assertEquals(0.5, row.rangeFromPct)
        assertEquals(1.0, row.rangeToPct)
        assertEquals(0.22, row.ratePct!!, 0.01)
    }

    @Test
    fun n2_rateAboveCutMaximum_failsWhenDeficitIsWithinCap() {
        // 30 kg: -450 kcal/day = 1.36 %/week, above 1.0, deficit under 500.
        val row = check(kcal = 2150, expenditure = 2600.0, weight = 30.0).checks.first { it.key == GoalCheckKey.N2Rate }

        assertFalse(row.passed)
        assertFalse(row.deficitOverCap)
    }

    @Test
    fun n2_gainRangeEdges() {
        // 80 kg gain: 0.25 %/week = 220 kcal/day, 0.5 % = 440 kcal/day.
        assertTrue(check(kcal = 2820, phase = Phase.Gain).passed(GoalCheckKey.N2Rate))
        assertTrue(check(kcal = 3040, phase = Phase.Gain).passed(GoalCheckKey.N2Rate))
        assertFalse(check(kcal = 2800, phase = Phase.Gain).passed(GoalCheckKey.N2Rate))
        assertFalse(check(kcal = 3100, phase = Phase.Gain).passed(GoalCheckKey.N2Rate))
    }

    @Test
    fun n2_maintenanceAllowsSmallChangesBothWays() {
        assertTrue(check(kcal = 2600, phase = Phase.Maintenance).passed(GoalCheckKey.N2Rate))
        assertTrue(check(kcal = 2400, phase = Phase.Maintenance).passed(GoalCheckKey.N2Rate))
        assertFalse(check(kcal = 2000, phase = Phase.Maintenance).passed(GoalCheckKey.N2Rate))
        assertFalse(check(kcal = 3200, phase = Phase.Recomposition).passed(GoalCheckKey.N2Rate))
    }

    @Test
    fun n2_withoutExpenditure_isNotEvaluated_andPasses() {
        val row = check(kcal = 1600, expenditure = null).checks.first { it.key == GoalCheckKey.N2Rate }

        assertTrue(row.passed)
        assertEquals(null, row.ratePct)
    }

    @Test
    fun n3_proteinExactly1Point6PerKg_passes() {
        assertTrue(check(protein = 128).passed(GoalCheckKey.N3Protein))
        val row = check(protein = 127).checks.first { it.key == GoalCheckKey.N3Protein }

        assertFalse(row.passed)
        assertEquals(127, row.grams)
        assertEquals(128, row.minGrams)
    }

    @Test
    fun n3_minimumRoundsUpForFractionalWeights() {
        // 1.6 x 70.3 = 112.48 -> 113 g needed.
        assertFalse(check(protein = 112, weight = 70.3).passed(GoalCheckKey.N3Protein))
        assertTrue(check(protein = 113, weight = 70.3).passed(GoalCheckKey.N3Protein))
    }

    @Test
    fun n4_fatExactly20PercentPasses_oneGramLessFails() {
        // 2160 kcal x 20 % = 432 kcal = 48 g.
        assertTrue(check(fat = 48).passed(GoalCheckKey.N4Fat))
        val row = check(fat = 47).checks.first { it.key == GoalCheckKey.N4Fat }

        assertFalse(row.passed)
        assertEquals(48, row.minGrams)
    }

    @Test
    fun n4_minimumRoundsUp() {
        // 2000 kcal x 20 % = 400 kcal = 44.4 g -> 45 g.
        assertEquals(45, check(kcal = 2000, fat = 40, expenditure = null).checks.last().minGrams)
        assertEquals(45, NutritionGoalsChecklist.minFatG(2000))
    }

    @Test
    fun failingRows_listedWithoutTheOthers() {
        val result = check(protein = 100, fat = 10)

        assertFalse(result.allPassed)
        assertEquals(
            listOf(GoalCheckKey.N3Protein, GoalCheckKey.N4Fat),
            result.checks.filter { !it.passed }.map { it.key },
        )
    }

    private fun GoalChecklist.passed(key: GoalCheckKey) = checks.first { it.key == key }.passed
}
