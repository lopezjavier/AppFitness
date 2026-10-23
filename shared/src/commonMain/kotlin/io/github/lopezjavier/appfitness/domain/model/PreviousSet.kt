package io.github.lopezjavier.appfitness.domain.model

/** A completed set of the user's most recent finished session of an exercise (the "ghost" of US-12). */
data class PreviousSet(val setIndex: Int, val weightKg: Double, val reps: Int, val rpe: Double? = null)
