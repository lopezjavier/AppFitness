package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.WorkoutSet

/** Total volume (weight x reps) of the completed, non-warm-up sets. */
object CalculateVolume {
    operator fun invoke(sets: List<WorkoutSet>): Double = sets
        .filter { it.isCompleted && it.setType != SetType.WarmUp }
        .sumOf { (it.entry as? SetEntry.WeightReps)?.let { entry -> entry.weightKg * entry.reps } ?: 0.0 }
}
