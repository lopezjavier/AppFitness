package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.WarmUpSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WarmUpCalculatorTest {
    private fun scheme(working: Double, bar: Double = 20.0, rounding: Double = 2.5) =
        WarmUpCalculator(working, bar, rounding)

    @Test
    fun hundredKilosWithTwentyKiloBar_isTheDefaultScheme() {
        assertEquals(
            listOf(WarmUpSet(20.0, 10), WarmUpSet(40.0, 5), WarmUpSet(60.0, 3), WarmUpSet(80.0, 2)),
            scheme(100.0),
        )
    }

    @Test
    fun weightsAreRoundedToTheNearestStep_tiesUp() {
        // 90 kg: 36 -> 35, 54 -> 55, 72 -> 72.5 with a 2.5 step.
        assertEquals(listOf(20.0, 35.0, 55.0, 72.5), scheme(90.0).map { it.weightKg })
    }

    @Test
    fun aBiggerStepChangesTheWeights() {
        // 5 kg step: 36 -> 35, 54 -> 55, 72 -> 70.
        assertEquals(listOf(20.0, 35.0, 55.0, 70.0), scheme(90.0, rounding = 5.0).map { it.weightKg })
    }

    @Test
    fun aTieRoundsUp() {
        // 62.5 kg: 40 % = 25 (exact); 60 % = 37.5 -> 37.5; with a 5 step 37.5 is a tie -> 40.
        assertEquals(40.0, scheme(62.5, rounding = 5.0)[2].weightKg)
    }

    @Test
    fun weightsNeverFallBelowTheBar_andRepeatedWeightsCollapse() {
        // 30 kg: 12 and 18 floor at the bar (20, already present); 24 -> 25.
        assertEquals(listOf(WarmUpSet(20.0, 10), WarmUpSet(25.0, 2)), scheme(30.0))
    }

    @Test
    fun setsAtOrAboveTheWorkingWeightAreDropped() {
        // 22.5 kg: only the bar set is lighter than the working weight.
        assertEquals(listOf(WarmUpSet(20.0, 10)), scheme(22.5))
    }

    @Test
    fun workingWeightAtOrBelowTheBar_hasNoWarmUp() {
        assertTrue(scheme(20.0).isEmpty())
        assertTrue(scheme(15.0).isEmpty())
        assertTrue(scheme(0.0).isEmpty())
    }

    @Test
    fun threeSets_are40_60_80Percent_withoutTheBarSet() {
        assertEquals(
            listOf(WarmUpSet(40.0, 5), WarmUpSet(60.0, 3), WarmUpSet(80.0, 2)),
            WarmUpCalculator(100.0, 20.0, 2.5, sets = 3),
        )
    }

    @Test
    fun twoSets_are50And80Percent_forFiveAndTwoReps() {
        assertEquals(
            listOf(WarmUpSet(50.0, 5), WarmUpSet(80.0, 2)),
            WarmUpCalculator(100.0, 20.0, 2.5, sets = 2),
        )
    }

    @Test
    fun oneSet_is60Percent_forThreeReps() {
        assertEquals(listOf(WarmUpSet(60.0, 3)), WarmUpCalculator(100.0, 20.0, 2.5, sets = 1))
    }

    @Test
    fun fourSets_isTheDefault() {
        assertEquals(scheme(100.0), WarmUpCalculator(100.0, 20.0, 2.5, sets = 4))
    }

    @Test
    fun fewerSets_stillNeverGoBelowTheBarOrReachTheWorkingWeight() {
        assertEquals(listOf(WarmUpSet(20.0, 5), WarmUpSet(25.0, 2)), WarmUpCalculator(30.0, 20.0, 2.5, sets = 2))
        assertTrue(WarmUpCalculator(20.0, 20.0, 2.5, sets = 1).isEmpty())
    }

    @Test
    fun aLighterBarMovesTheFirstSet() {
        assertEquals(WarmUpSet(7.5, 10), scheme(100.0, bar = 7.5).first())
    }

    @Test
    fun aTwoKiloStep_roundsTiesUp() {
        // 45 kg with a 2 kg step: 18, 27 -> 28 (tie), 36; the "bar" here is 2 kg.
        assertEquals(listOf(2.0, 18.0, 28.0, 36.0), scheme(45.0, bar = 2.0, rounding = 2.0).map { it.weightKg })
    }
}
