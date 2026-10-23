package io.github.lopezjavier.appfitness.domain.model

/**
 * A completed set of the user's most recent finished session of an exercise (the "ghost" of US-12). Only the columns
 * of the exercise's metric type carry values; the others are 0 ([weightKg], [reps]) or null.
 */
data class PreviousSet(
    val setIndex: Int,
    val weightKg: Double,
    val reps: Int,
    val rpe: Double? = null,
    val durationS: Int? = null,
    val distanceM: Double? = null,
    val steps: Int? = null,
    val floors: Int? = null,
)
