package io.github.lopezjavier.appfitness.domain.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

enum class PlanStatus { Proposed, Accepted, Superseded, Discarded }

/** Why the calories are not what the rate alone would give: the safety floor or the goal's allowed range. */
enum class PlanLimit { Floor, GoalRange }

/** Per-meal protein guidance (not a meal plan): about [perMealG] g per meal, in [minMeals] meals or more. */
data class ProteinDistribution(val perMealG: Int, val minMeals: Int, val gPerKg: Double)

/** Deterministic calories and macros of a plan (`coach_plans.nutrition`, data_version 1). */
data class PlanNutrition(
    val targetKcal: Int,
    val floorKcal: Int?,
    val limitedBy: PlanLimit?,
    val expenditureKcal: Double?,
    /** Percent of body weight per week in the direction of the intention. */
    val ratePctWeek: Double,
    /** Expected weight change per week in kg, negative for a loss. */
    val rateKgWeek: Double,
    val strategy: GoalStrategy,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val proteinPct: Int,
    val carbsPct: Int,
    val fatPct: Int,
    val distribution: ProteinDistribution?,
    /** True once the user changed the Diario targets by hand after accepting. */
    val editedByUser: Boolean,
)

enum class OutcomeKind { Weight, E1rm }

/** An optional, read-only target of the plan; [key] is `weight` or the exercise id for an e1RM outcome. */
data class PlanOutcome(
    val kind: OutcomeKind,
    val key: String,
    val targetValue: Double,
    val reason: String,
    val deadline: LocalDate,
)

data class CoachPlan(
    val id: String,
    val blockStart: LocalDate,
    val blockWeeks: Int,
    val intention: String,
    val status: PlanStatus,
    val nutrition: PlanNutrition,
    val outcomes: List<PlanOutcome>,
    val acceptedAt: Instant?,
) {
    /** The weight outcome the Weight screen shows, if the plan has one. */
    val weightOutcome: PlanOutcome? get() = outcomes.firstOrNull { it.kind == OutcomeKind.Weight }
}
