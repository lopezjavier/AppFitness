package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType

/**
 * Best estimated 1RM among the completed sets of [rows] (warm-up sets only when [includeWarmUp]); null when no set is
 * eligible.
 */
internal fun bestOneRepMax(rows: List<SessionExercise>, includeWarmUp: Boolean = false): Double? =
    rows.flatMap { it.sets }
        .filter { it.isCompleted && (includeWarmUp || it.setType != SetType.WarmUp) }
        .mapNotNull { set ->
            when (val entry = set.entry) {
                is SetEntry.WeightReps -> EstimateOneRepMax(entry.weightKg, entry.reps)
                is SetEntry.Weighted -> EstimateOneRepMax(entry.addedKg, entry.reps)
                else -> null
            }
        }
        .maxOrNull()
