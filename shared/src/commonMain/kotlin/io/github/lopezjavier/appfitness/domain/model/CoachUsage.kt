package io.github.lopezjavier.appfitness.domain.model

import kotlinx.datetime.LocalDate

/** AI spending of the signed-in user in one month (month = first day, UTC). */
data class CoachUsage(val month: LocalDate, val inputTokens: Long, val outputTokens: Long, val costEur: Double) {
    val fraction: Float get() = CoachBudget.fractionOf(costEur)
    val limitReached: Boolean get() = CoachBudget.isReached(costEur)

    companion object {
        fun empty(month: LocalDate) = CoachUsage(month, inputTokens = 0, outputTokens = 0, costEur = 0.0)
    }
}

/** Client mirror of the server budget. */
object CoachBudget {
    /** Mirrors `USER_LIMIT_EUR` in `supabase/functions/coach/budget.ts`; keep both in sync. */
    const val USER_MONTHLY_LIMIT_EUR = 2.0

    /** Spent share of the limit, clamped to 0..1. */
    fun fractionOf(costEur: Double): Float = (costEur / USER_MONTHLY_LIMIT_EUR).coerceIn(0.0, 1.0).toFloat()

    /** The function refuses when spent >= limit. */
    fun isReached(costEur: Double): Boolean = costEur >= USER_MONTHLY_LIMIT_EUR
}
