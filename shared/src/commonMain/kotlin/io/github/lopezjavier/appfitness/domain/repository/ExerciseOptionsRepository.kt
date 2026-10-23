package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.ExerciseOptions

/** The signed-in user's `exercise_options` rows (written through `set_exercise_double_dumbbell`). */
interface ExerciseOptionsRepository {
    /** The exercises with "Contar mancuerna doble" on; no rows yield the defaults (all off). */
    suspend fun get(): Result<ExerciseOptions>

    /** Turns "Contar mancuerna doble" on or off for a dumbbell exercise (applies to all sessions). */
    suspend fun setDoubleDumbbell(exerciseId: String, enabled: Boolean): Result<Unit>
}

/** Exercises counted double; a failed read falls back to none and never blocks the caller. */
suspend fun ExerciseOptionsRepository.doubleDumbbellIdsOrDefault(): Set<String> =
    get().getOrNull()?.doubleDumbbellExerciseIds ?: emptySet()
