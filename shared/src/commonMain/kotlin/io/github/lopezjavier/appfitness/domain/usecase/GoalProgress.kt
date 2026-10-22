package io.github.lopezjavier.appfitness.domain.usecase

import kotlin.math.abs
import kotlin.math.floor

/** Progress from the first weight to the target, 0-100 (planning/04-business-rules.md §3.5). */
class GoalProgress {
    /** Rounded half up; null when [startKg] equals [targetKg] (no bar). Works for deficit and surplus alike. */
    operator fun invoke(startKg: Double, currentKg: Double, targetKg: Double): Int? {
        val start = tenths(startKg)
        val span = start - tenths(targetKg)
        if (span == 0) return null
        val done = start - tenths(currentKg)
        // Orient both toward the target so a surplus (span < 0) reads like a deficit.
        val sign = if (span > 0) 1 else -1
        val orientedDone = done * sign
        val orientedSpan = abs(span)
        return when {
            orientedDone <= 0 -> 0
            orientedDone >= orientedSpan -> MAX_PERCENT
            else -> (orientedDone * 2 * MAX_PERCENT + orientedSpan) / (2 * orientedSpan)
        }
    }

    private fun tenths(kg: Double): Int = floor(kg * TENTHS + HALF).toInt()

    private companion object {
        const val MAX_PERCENT = 100
        const val TENTHS = 10.0
        const val HALF = 0.5
    }
}
