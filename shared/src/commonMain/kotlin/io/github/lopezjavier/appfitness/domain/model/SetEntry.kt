package io.github.lopezjavier.appfitness.domain.model

/**
 * The measured values of one set. Polymorphic by the exercise's [ExerciseMetricType]: E3 only implements
 * [WeightReps]; E6 adds the duration, distance and other variants next to it.
 */
sealed interface SetEntry {
    data class WeightReps(val weightKg: Double, val reps: Int) : SetEntry
}
