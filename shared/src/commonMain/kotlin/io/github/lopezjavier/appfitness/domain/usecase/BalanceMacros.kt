package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.Macronutrient

/**
 * Auto-balance of the macro split (planning/04 §1.5): the moved macro takes the requested percent (clamped to
 * 5..90) and the other two share the rest proportionally to their current value, by largest remainder. A share that
 * would fall below 5 stops at 5 and the other takes the rest.
 */
object BalanceMacros {
    const val MIN_PCT = 5
    private const val TOTAL = 100
    private const val OTHERS = 2
    const val MAX_PCT = TOTAL - OTHERS * MIN_PCT

    operator fun invoke(current: MacroSplit, moved: Macronutrient, requested: Int): MacroSplit {
        val target = requested.coerceIn(MIN_PCT, MAX_PCT)
        if (target == current[moved]) return current
        val (first, second) = Macronutrient.entries.filter { it != moved }
        val a = current[first]
        val b = current[second]
        val pool = TOTAL - target
        val base = a + b
        val numeratorA = pool * a
        var newA = numeratorA / base
        var newB = pool * b / base
        val leftover = pool - newA - newB
        if (leftover > 0) {
            // Largest remainder; a tie goes to the first (declaration order).
            if (numeratorA % base >= pool * b % base) newA += leftover else newB += leftover
        }
        if (newA < MIN_PCT) {
            newA = MIN_PCT
            newB = pool - MIN_PCT
        } else if (newB < MIN_PCT) {
            newB = MIN_PCT
            newA = pool - MIN_PCT
        }
        return current.with(moved, target).with(first, newA).with(second, newB)
    }
}
