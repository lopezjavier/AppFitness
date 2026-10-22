package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import kotlin.test.Test
import kotlin.test.assertEquals

class TargetDistanceTest {
    private val distance = TargetDistance()

    @Test
    fun deficit_aboveTarget_remainsTheRoundedDistance() {
        assertEquals(TargetDistanceResult.Remaining(4.2), distance(74.2, 70.0, GoalStrategy.Deficit))
    }

    @Test
    fun deficit_atOrBelowTarget_isReached() {
        assertEquals(TargetDistanceResult.Reached, distance(69.5, 70.0, GoalStrategy.Deficit))
        assertEquals(TargetDistanceResult.Reached, distance(70.0, 70.0, GoalStrategy.Deficit))
    }

    @Test
    fun deficit_targetAboveCurrent_isReached() {
        assertEquals(TargetDistanceResult.Reached, distance(65.0, 70.0, GoalStrategy.Deficit))
    }

    @Test
    fun surplus_mirrorsDeficit() {
        assertEquals(TargetDistanceResult.Remaining(3.0), distance(67.0, 70.0, GoalStrategy.Surplus))
        assertEquals(TargetDistanceResult.Reached, distance(70.5, 70.0, GoalStrategy.Surplus))
        assertEquals(TargetDistanceResult.Reached, distance(70.0, 70.0, GoalStrategy.Surplus))
        assertEquals(TargetDistanceResult.Reached, distance(75.0, 70.0, GoalStrategy.Surplus))
    }

    @Test
    fun maintenance_reportsBothSides() {
        assertEquals(TargetDistanceResult.Above(1.0), distance(71.0, 70.0, GoalStrategy.Maintenance))
        assertEquals(TargetDistanceResult.Below(1.0), distance(69.0, 70.0, GoalStrategy.Maintenance))
        assertEquals(TargetDistanceResult.Reached, distance(70.0, 70.0, GoalStrategy.Maintenance))
    }

    @Test
    fun maintenance_distanceRoundingToZeroIsReached() {
        assertEquals(TargetDistanceResult.Reached, distance(70.04, 70.0, GoalStrategy.Maintenance))
        assertEquals(TargetDistanceResult.Reached, distance(69.96, 70.0, GoalStrategy.Maintenance))
    }
}
