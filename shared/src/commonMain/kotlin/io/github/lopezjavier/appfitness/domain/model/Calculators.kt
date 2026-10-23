package io.github.lopezjavier.appfitness.domain.model

/** One set of the warm-up scheme. */
data class WarmUpSet(val weightKg: Double, val reps: Int)

/** Plates (heaviest first) to load on each side, the weight they make with the bar and whether it is the target. */
data class PlateResult(val platesPerSide: List<Double>, val achievedKg: Double, val exact: Boolean)
