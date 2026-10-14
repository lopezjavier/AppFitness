package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.Sex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalculateCalorieTargetTest {
    private fun target(tdee: Int, sex: Sex, strategy: GoalStrategy, adjustment: Int) =
        CalculateCalorieTarget(tdee, sex, strategy, adjustment)

    @Test
    fun deficitDefault_ofTdee2800_is2400Moderate() {
        val adjustment = EnergyStrategyRules.defaultAdjustment(GoalStrategy.Deficit)
        val result = target(2800, Sex.Male, GoalStrategy.Deficit, adjustment)
        assertEquals(2400, result.targetKcal)
        assertEquals(AdjustmentIntensity.Moderate, result.intensity)
        assertFalse(result.isFloored)
    }

    @Test
    fun twoStepsDown_fromMinus400_giveMinus500AndTarget2300() {
        var adjustment = -400
        repeat(2) { adjustment = EnergyStrategyRules.stepDown(GoalStrategy.Deficit, adjustment) }
        assertEquals(-500, adjustment)
        assertEquals(2300, target(2800, Sex.Male, GoalStrategy.Deficit, adjustment).targetKcal)
    }

    @Test
    fun femaleBelowTheFloor_isRaisedTo1200() {
        val result = target(1500, Sex.Female, GoalStrategy.Deficit, -400)
        assertEquals(1200, result.targetKcal)
        assertTrue(result.isFloored)
        assertEquals(-400, result.adjustment)
    }

    @Test
    fun maleBelowTheFloor_isRaisedTo1500() {
        val result = target(1800, Sex.Male, GoalStrategy.Deficit, -400)
        assertEquals(1500, result.targetKcal)
        assertTrue(result.isFloored)
    }

    @Test
    fun exactlyAtTheFloor_isNotFloored() {
        val result = target(1900, Sex.Male, GoalStrategy.Deficit, -400)
        assertEquals(1500, result.targetKcal)
        assertFalse(result.isFloored)
    }

    @Test
    fun defaultsPerStrategy() {
        assertEquals(-400, EnergyStrategyRules.defaultAdjustment(GoalStrategy.Deficit))
        assertEquals(0, EnergyStrategyRules.defaultAdjustment(GoalStrategy.Maintenance))
        assertEquals(250, EnergyStrategyRules.defaultAdjustment(GoalStrategy.Surplus))
    }

    @Test
    fun floorsPerSex() {
        assertEquals(1200, EnergyStrategyRules.floorKcal(Sex.Female))
        assertEquals(1500, EnergyStrategyRules.floorKcal(Sex.Male))
    }

    @Test
    fun rangeEnds_areNotCrossed() {
        assertEquals(-1000, EnergyStrategyRules.stepDown(GoalStrategy.Deficit, -1000))
        assertEquals(-50, EnergyStrategyRules.stepUp(GoalStrategy.Deficit, -50))
        assertEquals(-100, EnergyStrategyRules.stepDown(GoalStrategy.Maintenance, -100))
        assertEquals(100, EnergyStrategyRules.stepUp(GoalStrategy.Maintenance, 100))
        assertEquals(50, EnergyStrategyRules.stepDown(GoalStrategy.Surplus, 50))
        assertEquals(750, EnergyStrategyRules.stepUp(GoalStrategy.Surplus, 750))
    }

    @Test
    fun stepsMoveBy50() {
        assertEquals(-450, EnergyStrategyRules.stepDown(GoalStrategy.Deficit, -400))
        assertEquals(-350, EnergyStrategyRules.stepUp(GoalStrategy.Deficit, -400))
    }

    @Test
    fun intensityThresholds() {
        fun intensity(adjustment: Int) = target(3000, Sex.Male, GoalStrategy.Deficit, adjustment).intensity
        assertEquals(AdjustmentIntensity.Light, intensity(-300))
        assertEquals(AdjustmentIntensity.Moderate, intensity(-350))
        assertEquals(AdjustmentIntensity.Moderate, intensity(-600))
        assertEquals(AdjustmentIntensity.Aggressive, intensity(-650))
        assertEquals(AdjustmentIntensity.Light, target(3000, Sex.Male, GoalStrategy.Surplus, 250).intensity)
    }

    @Test
    fun maintenance_hasNoIntensity() {
        assertNull(target(2500, Sex.Male, GoalStrategy.Maintenance, 0).intensity)
    }

    @Test
    fun clampAdjustment_bringsAPrefillIntoTheRange() {
        assertEquals(-1000, EnergyStrategyRules.clampAdjustment(GoalStrategy.Deficit, -1500))
        assertEquals(-50, EnergyStrategyRules.clampAdjustment(GoalStrategy.Deficit, 200))
        assertEquals(50, EnergyStrategyRules.clampAdjustment(GoalStrategy.Surplus, 0))
        assertEquals(100, EnergyStrategyRules.clampAdjustment(GoalStrategy.Maintenance, 400))
    }
}
