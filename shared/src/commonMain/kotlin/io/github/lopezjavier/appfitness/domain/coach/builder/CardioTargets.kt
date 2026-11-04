package io.github.lopezjavier.appfitness.domain.coach.builder

import io.github.lopezjavier.appfitness.domain.model.CoachPlan

/** The weekly cardio target the builder feeds into [CardioPlan.of]; pure. */
object CardioTargets {
    /** WHO weekly minimum, used when the plan holds no positive target. Mirrors the server default (T-224 owns it). */
    const val DEFAULT_WEEKLY_MIN = 150

    fun forWeeklyMinutes(minutes: Int?): Int = minutes?.takeIf { it > 0 } ?: DEFAULT_WEEKLY_MIN

    fun forPlan(plan: CoachPlan?): Int = forWeeklyMinutes(plan?.nutrition?.activity?.cardioMinWeek)
}
