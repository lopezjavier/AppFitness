@file:Suppress("MatchingDeclarationName") // The file holds the error enum and the pure validation functions.

package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.PlannedSet
import io.github.lopezjavier.appfitness.domain.model.RoutineBounds
import io.github.lopezjavier.appfitness.domain.model.SetType

/** Why a routine's planned sets cannot be saved; mirrors the checks of the `save_routine` RPC. */
enum class PlannedSetsError { SetCount, Reps, Weight, SupersetMismatch, Duration }

/** The first problem of one exercise's [sets] (1-10 sets, reps 1-50, weight >= 0 or none), or null when valid. */
fun validatePlannedSets(sets: List<PlannedSet>): PlannedSetsError? = when {
    sets.size !in RoutineBounds.SETS_MIN..RoutineBounds.SETS_MAX -> PlannedSetsError.SetCount
    sets.any { it.isTimed && !it.isValidTimed() } -> PlannedSetsError.Duration
    sets.any { !it.isTimed && it.reps !in RoutineBounds.REPS_MIN..RoutineBounds.REPS_MAX } -> PlannedSetsError.Reps
    sets.any { (it.weightKg ?: RoutineBounds.WEIGHT_MIN_KG) < RoutineBounds.WEIGHT_MIN_KG } -> PlannedSetsError.Weight
    else -> null
}

/** A planned-duration set lasts 60..14400 s in whole minutes and has no reps and no weight. */
private fun PlannedSet.isValidTimed(): Boolean {
    val seconds = durationS ?: return false
    return seconds in RoutineBounds.DURATION_MIN_SECONDS..RoutineBounds.DURATION_MAX_SECONDS &&
        seconds % RoutineBounds.SECONDS_PER_MINUTE == 0 && reps == 0 && weightKg == null
}

/** Maps the legacy per-exercise columns (target_sets x target_reps/weight/type) to identical planned sets. */
fun plannedSetsFromLegacy(targetSets: Int, targetReps: Int, weightKg: Double?, type: SetType): List<PlannedSet> =
    List(targetSets) { PlannedSet(weightKg, targetReps, type) }
