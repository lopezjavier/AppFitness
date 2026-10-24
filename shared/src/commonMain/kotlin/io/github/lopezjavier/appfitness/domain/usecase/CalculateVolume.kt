package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.WorkoutSet

/**
 * Total volume of the completed sets; warm-up sets count only when [includeWarmUp] is true. Per variant:
 * weight x reps for [SetEntry.WeightReps] and [SetEntry.Weighted] (added load only); (body weight + added) x reps for
 * [SetEntry.Bodyweight]; max(body weight - assistance, 0) x reps for [SetEntry.Assisted]; 0 for every other variant.
 * [bodyWeightKg] is the user's latest body weight; when null it counts as 0 (bodyweight sets then carry the added load
 * only and assisted sets carry none).
 * With [perHand] the weight x reps variants count twice (the logged weight is per hand, 04 §4.7).
 */
object CalculateVolume {
    operator fun invoke(
        sets: List<WorkoutSet>,
        includeWarmUp: Boolean = false,
        bodyWeightKg: Double? = null,
        perHand: Boolean = false,
    ): Double = sets
        .filter { it.isCompleted && (includeWarmUp || it.setType != SetType.WarmUp) }
        .sumOf { volumeOf(it.entry, bodyWeightKg ?: 0.0, if (perHand) 2.0 else 1.0) }

    private fun volumeOf(entry: SetEntry, bodyWeightKg: Double, factor: Double): Double = when (entry) {
        is SetEntry.WeightReps -> entry.weightKg * entry.reps * factor

        is SetEntry.Weighted -> entry.addedKg * entry.reps * factor

        is SetEntry.Bodyweight -> (bodyWeightKg + entry.addedKg) * entry.reps

        is SetEntry.Assisted -> (bodyWeightKg - entry.assistKg).coerceAtLeast(0.0) * entry.reps

        is SetEntry.RepsOnly,
        is SetEntry.Duration,
        is SetEntry.WeightDuration,
        is SetEntry.DistanceDuration,
        is SetEntry.StepsDuration,
        is SetEntry.FloorsDuration,
        -> 0.0
    }
}
