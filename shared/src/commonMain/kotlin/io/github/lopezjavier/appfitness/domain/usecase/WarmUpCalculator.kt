package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.WarmUpSet
import kotlin.math.floor

/**
 * Warm-up scheme from the working weight (`04` §4.9, ADR-52): bar x 10, 40 % x 5, 60 % x 3, 80 % x 2. Each weight is
 * rounded to the nearest multiple of [roundingKg] (ties up) and never goes below the bar. Sets at or above the working
 * weight are dropped and a set with the same weight as the previous one collapses into it (the first is kept), so a
 * working weight at or below the bar gives no warm-up.
 */
object WarmUpCalculator {
    private val STEPS_BY_COUNT = mapOf(
        3 to listOf(0.4 to 5, 0.6 to 3, 0.8 to 2),
        2 to listOf(0.5 to 5, 0.8 to 2),
        1 to listOf(0.6 to 3),
    )
    private const val BAR_REPS = 10
    const val MAX_SETS = 4
    private const val EPSILON = 1e-9
    private const val HALF = 0.5

    /** [sets] (1..[MAX_SETS], ADR-55): 4 adds the bar x 10 set; 3, 2 and 1 use fewer percentages. */
    operator fun invoke(workingKg: Double, barKg: Double, roundingKg: Double, sets: Int = MAX_SETS): List<WarmUpSet> {
        val count = sets.coerceIn(1, MAX_SETS)
        val steps = STEPS_BY_COUNT[minOf(count, MAX_SETS - 1)].orEmpty()
        val barSet = if (count == MAX_SETS) listOf(WarmUpSet(barKg, BAR_REPS)) else emptyList()
        val candidates = barSet + steps.map { (fraction, reps) ->
            WarmUpSet(maxOf(roundTo(workingKg * fraction, roundingKg), barKg), reps)
        }
        return candidates.filter { it.weightKg < workingKg }.fold(emptyList()) { kept, set ->
            if (kept.lastOrNull()?.weightKg == set.weightKg) kept else kept + set
        }
    }

    private fun roundTo(value: Double, step: Double): Double =
        if (step <= 0.0) value else floor(value / step + HALF + EPSILON) * step
}
