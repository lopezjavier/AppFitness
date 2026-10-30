package io.github.lopezjavier.appfitness.domain.model

/** A pending in-block calorie change of the accepted plan (`coach_recommendations`, kind `calorie_change`). */
data class CalorieChange(
    val id: String,
    val planId: String?,
    val fromKcal: Int,
    val toKcal: Int,
    val changePct: Double,
    /** Code such as `rate_outside_interval`; the Spanish text lives in the UI. */
    val reasonCode: String,
    val slopeKgWeek: Double?,
    val intervalLow: Double?,
    val intervalHigh: Double?,
)
