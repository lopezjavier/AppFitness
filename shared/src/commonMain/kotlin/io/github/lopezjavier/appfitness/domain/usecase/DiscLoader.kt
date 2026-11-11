package io.github.lopezjavier.appfitness.domain.usecase

/**
 * The disc loader (`04` §4.9, ADR-56): the user picks how many discs of each weight go on ONE side and the same
 * discs go on the other; total = bar (or dumbbell handle) + 2 x sum of the discs of a side.
 */
object DiscLoader {
    private const val SIDES = 2

    /** Largest number of discs of one weight per side. */
    const val MAX_DISCS_PER_WEIGHT = 10

    /** The weight of the discs of one side. */
    fun perSideKg(discsPerSide: Map<Double, Int>): Double =
        discsPerSide.entries.sumOf { (kg, count) -> if (count > 0) kg * count else 0.0 }

    fun total(barKg: Double, discsPerSide: Map<Double, Int>): Double = barKg + SIDES * perSideKg(discsPerSide)
}
