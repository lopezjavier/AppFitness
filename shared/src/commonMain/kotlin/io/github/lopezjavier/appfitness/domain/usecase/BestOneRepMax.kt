package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType

/** Best estimated 1RM among the completed, non-warm-up sets of [rows]; null when no set is eligible. */
internal fun bestOneRepMax(rows: List<SessionExercise>): Double? = rows.flatMap { it.sets }
    .filter { it.isCompleted && it.setType != SetType.WarmUp }
    .mapNotNull { set -> (set.entry as? SetEntry.WeightReps)?.let { EstimateOneRepMax(it.weightKg, it.reps) } }
    .maxOrNull()
