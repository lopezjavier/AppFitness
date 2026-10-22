package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.WorkoutSet

/** Total volume (weight x reps) of the completed sets; warm-up sets count only when [includeWarmUp] is true. */
object CalculateVolume {
    operator fun invoke(sets: List<WorkoutSet>, includeWarmUp: Boolean = false): Double = sets
        .filter { it.isCompleted && (includeWarmUp || it.setType != SetType.WarmUp) }
        .sumOf { (it.entry as? SetEntry.WeightReps)?.let { entry -> entry.weightKg * entry.reps } ?: 0.0 }
}
