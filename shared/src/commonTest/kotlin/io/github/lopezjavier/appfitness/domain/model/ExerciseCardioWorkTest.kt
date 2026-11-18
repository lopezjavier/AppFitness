package io.github.lopezjavier.appfitness.domain.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExerciseCardioWorkTest {
    private fun exercise(
        equipment: Equipment = Equipment.Barbell,
        metric: ExerciseMetricType = ExerciseMetricType.WeightReps,
        pattern: MovementPattern? = null,
    ) = Exercise("e", "k", null, MuscleGroup.Chest, emptyList(), equipment, metric, false, movementPattern = pattern)

    @Test
    fun aCardioMovementPatternIsCardioWork() {
        assertTrue(exercise(pattern = MovementPattern.Cardio).isCardioWork)
    }

    @Test
    fun cardioEquipmentIsCardioWork() {
        val cardioEquipment = Equipment.entries.first { it.isCardio }
        assertTrue(exercise(equipment = cardioEquipment).isCardioWork)
    }

    @Test
    fun distanceStepsAndFloorsMetricsAreCardioWork() {
        listOf(
            ExerciseMetricType.DistanceDuration,
            ExerciseMetricType.StepsDuration,
            ExerciseMetricType.FloorsDuration,
        ).forEach { assertTrue(exercise(metric = it).isCardioWork, it.name) }
    }

    @Test
    fun aLiftingExerciseIsNotCardioWork() {
        assertFalse(exercise().isCardioWork)
    }
}
