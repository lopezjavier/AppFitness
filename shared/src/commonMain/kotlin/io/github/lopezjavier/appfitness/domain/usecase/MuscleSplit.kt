package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.WorkoutSession

/** Share of one primary muscle group in a session: [sets] completed sets, [fraction] of the counted sets. */
data class MuscleShare(val group: MuscleGroup, val sets: Int, val fraction: Double)

object MuscleSplit {
    /**
     * Completed sets per primary muscle group, most sets first, ties in enum order. Warm-up sets count only when
     * [includeWarmUp]; pending sets and exercises without a known group are ignored. Empty when nothing counts.
     */
    operator fun invoke(
        session: WorkoutSession,
        groups: Map<String, MuscleGroup>,
        includeWarmUp: Boolean,
    ): List<MuscleShare> {
        val counts = mutableMapOf<MuscleGroup, Int>()
        for (exercise in session.exercises) {
            val group = groups[exercise.exerciseId] ?: continue
            val sets = exercise.sets.count { it.isCompleted && (includeWarmUp || it.setType != SetType.WarmUp) }
            if (sets > 0) counts[group] = (counts[group] ?: 0) + sets
        }
        val total = counts.values.sum()
        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<MuscleGroup, Int>> { it.value }.thenBy { it.key.ordinal })
            .map { MuscleShare(it.key, it.value, it.value.toDouble() / total) }
    }
}
