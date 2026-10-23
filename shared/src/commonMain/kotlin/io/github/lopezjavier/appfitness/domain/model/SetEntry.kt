package io.github.lopezjavier.appfitness.domain.model

/**
 * The measured values of one set. Polymorphic by the exercise's [ExerciseMetricType]: one variant per metric type
 * (see [metricType] and `SetEntryMapping.kt` for the `workout_sets` columns each one writes).
 */
sealed interface SetEntry {
    data class WeightReps(val weightKg: Double, val reps: Int) : SetEntry

    data class RepsOnly(val reps: Int) : SetEntry

    data class Duration(val durationS: Int) : SetEntry

    data class WeightDuration(val weightKg: Double, val durationS: Int) : SetEntry

    data class DistanceDuration(val distanceM: Double, val durationS: Int) : SetEntry

    /** Reps with an optional [addedKg] load on top of the body weight. */
    data class Bodyweight(val addedKg: Double, val reps: Int) : SetEntry

    /** Reps with [assistKg] of counter-weight that lowers the effective body weight. */
    data class Assisted(val assistKg: Double, val reps: Int) : SetEntry

    /** Reps with an [addedKg] load only (the body weight is not part of the volume). */
    data class Weighted(val addedKg: Double, val reps: Int) : SetEntry

    data class StepsDuration(val steps: Int, val durationS: Int) : SetEntry

    data class FloorsDuration(val floors: Int, val durationS: Int) : SetEntry

    companion object
}
