package io.github.lopezjavier.appfitness.domain.model

import kotlinx.datetime.LocalDate

/** Where a plan week sits relative to today (T-165). */
enum class WeekPhase { Past, Current, Upcoming }

/** One planned routine of a week; [done] once a finished workout of that routine was linked to it. */
data class PlanWeekSession(val sessionIndex: Int, val name: String, val routineId: String?, val done: Boolean)

/** A week of the saved training block with its adherence; [adherencePct] is rounded half up (3 of 4 = 75). */
data class PlanWeek(
    val week: Int,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val phase: WeekPhase,
    val deload: Boolean,
    val planned: Int,
    val completed: Int,
    val adherencePct: Int,
    val sessions: List<PlanWeekSession>,
    /** Minutes of cardio logged in the week (any cardio exercise, planned or not); nothing carries over. */
    val cardioMinDone: Int = 0,
    /** The plan's weekly cardio target in minutes; null for a plan stored before the cardio target existed. */
    val cardioMinTarget: Int? = null,
    /** Mean daily steps over the week's days with data; null when no day has steps. */
    val stepsAvg: Int? = null,
    val stepsTarget: Int? = null,
)

/** A pending coach suggestion to make [week] of plan [planId] easier (same weights, 40 % fewer working sets). */
data class DeloadProposal(val id: String, val planId: String, val week: Int)

/** Working sets of an exercise in a deload week: [deloadSets] of the [plannedSets] the routine prefilled. */
data class DeloadExerciseSuggestion(val exerciseId: String, val plannedSets: Int, val deloadSets: Int)
