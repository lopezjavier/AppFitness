@file:Suppress("MatchingDeclarationName") // The file holds the error enum and the pure validation functions.

package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.PlannedSet
import io.github.lopezjavier.appfitness.domain.model.RoutineBounds
import io.github.lopezjavier.appfitness.domain.model.RoutineExercise
import io.github.lopezjavier.appfitness.domain.model.SetType

/** Why a routine's planned sets cannot be saved; mirrors the checks of the `save_routine` RPC. */
enum class PlannedSetsError { SetCount, Reps, Weight, SupersetMismatch }

/** The first problem of one exercise's [sets] (1-10 sets, reps 1-50, weight >= 0 or none), or null when valid. */
fun validatePlannedSets(sets: List<PlannedSet>): PlannedSetsError? = when {
    sets.size !in RoutineBounds.SETS_MIN..RoutineBounds.SETS_MAX -> PlannedSetsError.SetCount
    sets.any { it.reps !in RoutineBounds.REPS_MIN..RoutineBounds.REPS_MAX } -> PlannedSetsError.Reps
    sets.any { (it.weightKg ?: RoutineBounds.WEIGHT_MIN_KG) < RoutineBounds.WEIGHT_MIN_KG } -> PlannedSetsError.Weight
    else -> null
}

/** Validates every exercise and the superset rule: members of the same group have the same set count (ADR-38). */
fun validateRoutinePlannedSets(exercises: List<RoutineExercise>): PlannedSetsError? {
    exercises.firstNotNullOfOrNull { validatePlannedSets(it.plannedSets) }?.let { return it }
    val mismatch = exercises
        .filter { it.supersetGroup != null }
        .groupBy { it.supersetGroup }
        .values
        .any { members -> members.map { it.targetSets }.distinct().size > 1 }
    return if (mismatch) PlannedSetsError.SupersetMismatch else null
}

/** Maps the legacy per-exercise columns (target_sets x target_reps/weight/type) to identical planned sets. */
fun plannedSetsFromLegacy(targetSets: Int, targetReps: Int, weightKg: Double?, type: SetType): List<PlannedSet> =
    List(targetSets) { PlannedSet(weightKg, targetReps, type) }

/** The legacy `target_reps` summary: the first working set's reps, else the first set's. */
fun legacySummaryReps(sets: List<PlannedSet>): Int =
    (sets.firstOrNull { it.type == SetType.Working } ?: sets.first()).reps
