package io.github.lopezjavier.appfitness.domain.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

enum class PlanStatus { Proposed, Accepted, Superseded, Discarded }

/** Why the calories are not what the rate alone would give: the safety floor or the goal's allowed range. */
enum class PlanLimit { Floor, GoalRange, DeficitCap }

/** Maintenance-week figures of a plan with a diet break: weeks 1-3 use the base figures, [week] 4 these. */
data class DietBreakWeek(val targetKcal: Int, val proteinG: Int, val carbsG: Int, val fatG: Int, val week: Int = 4)

/** Per-meal protein guidance (not a meal plan): about [perMealG] g per meal, in [minMeals] meals or more. */
data class ProteinDistribution(val perMealG: Int, val minMeals: Int, val gPerKg: Double)

/** Where the daily steps target comes from: the user's 28-day average or the 7 000 floor (no or little data). */
enum class StepsBasis { Average, Floor }

/** WHO weekly minimum of moderate cardio; below it the editor shows a note (the user may keep the value). */
const val RECOMMENDED_CARDIO_MIN_WEEK = 150

/** Weekly cardio (user-editable) and daily steps (not editable) targets of a plan; calories never depend on them. */
data class PlanActivity(
    val cardioMinWeek: Int,
    val cardioEdited: Boolean,
    val stepsDay: Int,
    val stepsBasis: StepsBasis,
)

/** Valid weekly cardio minutes (0..600 in steps of 5), shared by the editor and the view models. */
object CardioMinutes {
    const val MIN = 0
    const val MAX = 600
    const val STEP = 5

    fun isValid(minutes: Int): Boolean = minutes in MIN..MAX && minutes % STEP == 0

    /** The nearest valid value to [minutes]. */
    fun clamp(minutes: Int): Int {
        val rounded = (minutes + STEP / 2).floorDiv(STEP) * STEP
        return rounded.coerceIn(MIN, MAX)
    }

    fun isBelowRecommended(minutes: Int): Boolean = minutes < RECOMMENDED_CARDIO_MIN_WEEK
}

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
    /** Null for a plan stored before the activity targets existed. */
    val activity: PlanActivity? = null,
    val phase: Phase = Phase.Maintenance,
    val phaseReason: PhaseReason = PhaseReason.Intention,
    val phaseOptions: PhaseOptions = PhaseOptions(),
    /** Only for a cut plan with the diet break on. */
    val dietBreak: DietBreakWeek? = null,
)

/** Result of the diet-break switch: [applied] when the Diario target changed, for plan [week] ([targetKcal] now). */
data class DietBreakApplication(val applied: Boolean, val week: Int, val targetKcal: Int?)

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
    val phase: Phase get() = nutrition.phase

    /** The weight outcome the Weight screen shows, if the plan has one. */
    val weightOutcome: PlanOutcome? get() = outcomes.firstOrNull { it.kind == OutcomeKind.Weight }
}
