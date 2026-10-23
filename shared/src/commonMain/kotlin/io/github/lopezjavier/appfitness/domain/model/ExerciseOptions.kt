package io.github.lopezjavier.appfitness.domain.model

/** Per-exercise volume options of the signed-in user; an exercise with no entry has every option off. */
data class ExerciseOptions(val doubleDumbbellExerciseIds: Set<String> = emptySet()) {
    fun isDoubleDumbbell(exerciseId: String): Boolean = exerciseId in doubleDumbbellExerciseIds
}
