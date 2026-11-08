package io.github.lopezjavier.appfitness.domain.model

import kotlinx.datetime.LocalDate

/** Sets of one muscle group in the week; half sets (secondary muscles) make [sets] fractional (04 section 4.11). */
data class GroupSets(val muscleGroup: MuscleGroup, val sets: Double)

/** Weight figures of the week; any of them is null when the trend lacks data. */
data class WeekSummaryWeight(
    val avgKg: Double?,
    val slopeKgWeek: Double?,
    val intervalLow: Double?,
    val intervalHigh: Double?,
    val targetRateKgWeek: Double?,
)

/** Decision state of a calorie proposal (`coach_recommendations.status`). */
enum class ProposalStatus { Pending, Applied, Declined }

/** A calorie decision of the week (created or decided inside it) with its current [status]. */
data class WeekSummaryDecision(val proposal: CalorieProposal, val status: ProposalStatus)

/**
 * Nutrition section of the week (07 section 4 (a)). Below 5 [completeDays] ([gateOk] false) every figure is null.
 * [decisions] are newest first.
 */
data class WeekSummaryNutrition(
    val completeDays: Int,
    val gateOk: Boolean,
    val avgKcal: Int?,
    val avgProteinG: Double?,
    val targetKcal: Int?,
    val targetProteinG: Int?,
    val proteinGPerKg: Double?,
    val decisions: List<WeekSummaryDecision>,
)

/**
 * The weekly summary of the latest finished plan week (US-98), built from recorded numbers only. [seen] is stored per
 * user, plan and week. [changes] are newest first.
 */
data class WeekSummary(
    val planId: String,
    val week: Int,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val seen: Boolean,
    val sessionsDone: Int,
    val sessionsPlanned: Int,
    val groups: List<GroupSets>,
    val weight: WeekSummaryWeight?,
    val completeDays: Int,
    val weighInDays: Int,
    val changes: List<CoachChange>,
    val deload: Boolean,
    val stalls: List<StallDiagnosis> = emptyList(),
    /** Null for a paused week and for a summary built before the nutrition section existed. */
    val nutrition: WeekSummaryNutrition? = null,
    /**
     * The week that just ended had no completed workout (US-106): only [planId], [startDate] and [endDate] are
     * meaningful, [week] is 0 and every other field is empty.
     */
    val paused: Boolean = false,
) {
    /** The unseen-notice view of this summary, or null when it was already opened or the week is paused. */
    fun toNotice(): WeekSummaryNotice? = if (seen || paused) null else WeekSummaryNotice(planId, week, endDate)

    companion object {
        /** The summary of a paused week: it is never marked seen and never a notice. */
        fun pausedWeek(planId: String, startDate: LocalDate, endDate: LocalDate) = WeekSummary(
            planId = planId,
            week = 0,
            startDate = startDate,
            endDate = endDate,
            seen = true,
            sessionsDone = 0,
            sessionsPlanned = 0,
            groups = emptyList(),
            weight = null,
            completeDays = 0,
            weighInDays = 0,
            changes = emptyList(),
            deload = false,
            paused = true,
        )
    }
}

/** What the notification center needs of an unseen summary. */
data class WeekSummaryNotice(val planId: String, val week: Int, val endDate: LocalDate)
