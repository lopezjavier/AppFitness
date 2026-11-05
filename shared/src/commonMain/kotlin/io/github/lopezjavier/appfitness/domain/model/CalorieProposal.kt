package io.github.lopezjavier.appfitness.domain.model

/**
 * A pending calorie proposal of the accepted plan (`coach_recommendations`, kind `calorie_change`): the user applies it
 * or says "not now". [rateKgWeek] is the plan's intended weekly rate (null on rows written before it was stored) and
 * [weeks] the number of consecutive weeks outside the interval that triggered it.
 */
data class CalorieProposal(
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
    val rateKgWeek: Double? = null,
    val weeks: Int = 2,
)
