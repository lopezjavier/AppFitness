package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.CalorieProposal
import io.github.lopezjavier.appfitness.domain.model.CoachPlan
import io.github.lopezjavier.appfitness.domain.model.CutRate
import io.github.lopezjavier.appfitness.domain.model.DietBreakWeek
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.OutcomeKind
import io.github.lopezjavier.appfitness.domain.model.PhaseOptions
import io.github.lopezjavier.appfitness.domain.model.PlanActivity
import io.github.lopezjavier.appfitness.domain.model.PlanLimit
import io.github.lopezjavier.appfitness.domain.model.PlanNutrition
import io.github.lopezjavier.appfitness.domain.model.PlanOutcome
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.domain.model.ProteinDistribution
import io.github.lopezjavier.appfitness.domain.model.StepsBasis
import io.github.lopezjavier.appfitness.domain.model.phaseForIntention
import io.github.lopezjavier.appfitness.domain.model.phaseReasonOf
import io.github.lopezjavier.appfitness.domain.model.storedPhaseOrNull
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.math.roundToInt

/** Tolerant reader for the jsonb the function and the table share: `data_version` can add keys. */
internal val coachPlanJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

@Serializable
internal data class ProteinDistributionDto(
    @SerialName("per_meal_g") val perMealG: Double = 0.0,
    @SerialName("min_meals") val minMeals: Int = 0,
    @SerialName("g_per_kg") val gPerKg: Double = 0.0,
)

/** `coach_plans.nutrition.activity`; absent on plans stored before the activity targets. */
@Serializable
internal data class ActivityDto(
    @SerialName("cardio_min_week") val cardioMinWeek: Double = 0.0,
    @SerialName("cardio_edited") val cardioEdited: Boolean = false,
    @SerialName("steps_day") val stepsDay: Double = 0.0,
    @SerialName("steps_basis") val stepsBasis: String? = null,
) {
    fun toDomain(): PlanActivity = PlanActivity(
        cardioMinWeek = cardioMinWeek.roundToInt(),
        cardioEdited = cardioEdited,
        stepsDay = stepsDay.roundToInt(),
        stepsBasis = if (stepsBasis == "average") StepsBasis.Average else StepsBasis.Floor,
    )
}

/** `coach_plans.nutrition` (shape in planning/03-data-model.md section 5). */
@Serializable
internal data class PlanNutritionDto(
    @SerialName("target_kcal") val targetKcal: Double = 0.0,
    @SerialName("floor_kcal") val floorKcal: Double? = null,
    @SerialName("limited_by") val limitedBy: String? = null,
    @SerialName("expenditure_kcal") val expenditureKcal: Double? = null,
    @SerialName("rate_pct_week") val ratePctWeek: Double = 0.0,
    @SerialName("rate_kg_week") val rateKgWeek: Double = 0.0,
    val strategy: String? = null,
    @SerialName("protein_g") val proteinG: Double = 0.0,
    @SerialName("carbs_g") val carbsG: Double = 0.0,
    @SerialName("fat_g") val fatG: Double = 0.0,
    @SerialName("protein_pct") val proteinPct: Double = 0.0,
    @SerialName("carbs_pct") val carbsPct: Double = 0.0,
    @SerialName("fat_pct") val fatPct: Double = 0.0,
    val distribution: ProteinDistributionDto? = null,
    @SerialName("edited_by_user") val editedByUser: Boolean = false,
    val activity: ActivityDto? = null,
    val phase: String? = null,
    @SerialName("phase_reason") val phaseReason: String? = null,
    @SerialName("phase_options") val phaseOptions: PhaseOptionsDto? = null,
    @SerialName("diet_break") val dietBreak: DietBreakDto? = null,
) {
    /**
     * [intention] (and the row's own phase columns) only matter for a legacy JSON without phase keys: the phase then
     * comes from the row, else from the intention.
     */
    fun toDomain(intention: String = "", row: PlanRowPhase = PlanRowPhase()): PlanNutrition = PlanNutrition(
        targetKcal = targetKcal.roundToInt(),
        floorKcal = floorKcal?.roundToInt(),
        limitedBy = when (limitedBy) {
            "floor" -> PlanLimit.Floor
            "goal_range" -> PlanLimit.GoalRange
            "deficit_cap" -> PlanLimit.DeficitCap
            else -> null
        },
        expenditureKcal = expenditureKcal,
        ratePctWeek = ratePctWeek,
        rateKgWeek = rateKgWeek,
        strategy = when (strategy) {
            "deficit" -> GoalStrategy.Deficit
            "surplus" -> GoalStrategy.Surplus
            else -> GoalStrategy.Maintenance
        },
        proteinG = proteinG.roundToInt(),
        carbsG = carbsG.roundToInt(),
        fatG = fatG.roundToInt(),
        proteinPct = proteinPct.roundToInt(),
        carbsPct = carbsPct.roundToInt(),
        fatPct = fatPct.roundToInt(),
        distribution = distribution?.let { ProteinDistribution(it.perMealG.roundToInt(), it.minMeals, it.gPerKg) },
        editedByUser = editedByUser,
        activity = activity?.toDomain(),
        phase = storedPhaseOrNull(phase) ?: storedPhaseOrNull(row.phase) ?: phaseForIntention(intention),
        phaseReason = phaseReasonOf(phaseReason ?: row.phaseReason),
        phaseOptions = (phaseOptions ?: row.phaseOptions)?.toDomain() ?: PhaseOptions(),
        dietBreak = dietBreak?.toDomain(),
    )
}

/** `nutrition.phase_options` / `coach_plans.phase_options`. */
@Serializable
internal data class PhaseOptionsDto(
    @SerialName("cut_rate_pct") val cutRatePct: Double = CutRate.DEFAULT,
    @SerialName("diet_break") val dietBreak: Boolean = false,
) {
    fun toDomain(): PhaseOptions = PhaseOptions(
        cutRatePct = if (CutRate.isValid(cutRatePct)) cutRatePct else CutRate.DEFAULT,
        dietBreak = dietBreak,
    )
}

/** `nutrition.diet_break`: the maintenance-week figures. */
@Serializable
internal data class DietBreakDto(
    @SerialName("target_kcal") val targetKcal: Double = 0.0,
    @SerialName("protein_g") val proteinG: Double = 0.0,
    @SerialName("carbs_g") val carbsG: Double = 0.0,
    @SerialName("fat_g") val fatG: Double = 0.0,
    val week: Int = 4,
) {
    fun toDomain(): DietBreakWeek = DietBreakWeek(
        targetKcal = targetKcal.roundToInt(),
        proteinG = proteinG.roundToInt(),
        carbsG = carbsG.roundToInt(),
        fatG = fatG.roundToInt(),
        week = week,
    )
}

/** The `phase*` columns of a `coach_plans` row, the fallback when the nutrition JSON has none. */
internal data class PlanRowPhase(
    val phase: String? = null,
    val phaseReason: String? = null,
    val phaseOptions: PhaseOptionsDto? = null,
)

@Serializable
internal data class PlanOutcomeDto(
    val kind: String = "",
    val key: String = "",
    @SerialName("target_value") val targetValue: Double = 0.0,
    val reason: String = "",
    val deadline: LocalDate,
) {
    /** Null for a kind this app version does not know. */
    fun toDomain(): PlanOutcome? {
        val outcomeKind = when (kind) {
            "weight" -> OutcomeKind.Weight
            "e1rm" -> OutcomeKind.E1rm
            else -> return null
        }
        return PlanOutcome(outcomeKind, key, targetValue, reason, deadline)
    }
}

/** Row of `coach_plans` (owner select). */
@Serializable
internal data class CoachPlanDto(
    val id: String,
    @SerialName("block_start") val blockStart: LocalDate,
    @SerialName("block_weeks") val blockWeeks: Int = 4,
    val intention: String = "",
    val status: String,
    val nutrition: PlanNutritionDto = PlanNutritionDto(),
    val outcomes: List<PlanOutcomeDto> = emptyList(),
    @SerialName("accepted_at") val acceptedAt: Instant? = null,
    val phase: String? = null,
    @SerialName("phase_reason") val phaseReason: String? = null,
    @SerialName("phase_options") val phaseOptions: PhaseOptionsDto? = null,
    val method: JsonElement? = null,
) {
    fun toDomain(): CoachPlan = CoachPlan(
        id = id,
        blockStart = blockStart,
        blockWeeks = blockWeeks,
        intention = intention,
        status = when (status) {
            "accepted" -> PlanStatus.Accepted
            "superseded" -> PlanStatus.Superseded
            "discarded" -> PlanStatus.Discarded
            else -> PlanStatus.Proposed
        },
        nutrition = nutrition.toDomain(intention, PlanRowPhase(phase, phaseReason, phaseOptions)),
        outcomes = outcomes.mapNotNull(PlanOutcomeDto::toDomain),
        acceptedAt = acceptedAt,
        fromGoalSwitch = (method as? JsonObject)?.text("source") == "goal_switch",
    )
}

/** The `delta` text of the `plan_nutrition` call: only the id is used, the plan is reloaded from the table. */
@Serializable
internal data class PlanEventDto(
    @SerialName("plan_id") val planId: String,
    @SerialName("recommendation_id") val recommendationId: String? = null,
    val nutrition: PlanNutritionDto = PlanNutritionDto(),
    val outcomes: List<PlanOutcomeDto> = emptyList(),
)

@Serializable
internal data class CalorieProposalPayloadDto(
    @SerialName("from_kcal") val fromKcal: Double,
    @SerialName("to_kcal") val toKcal: Double,
    @SerialName("change_pct") val changePct: Double = 0.0,
    @SerialName("slope_kg_week") val slopeKgWeek: Double? = null,
    @SerialName("interval_low") val intervalLow: Double? = null,
    @SerialName("interval_high") val intervalHigh: Double? = null,
    @SerialName("rate_kg_week") val rateKgWeek: Double? = null,
    val weeks: Int = 2,
)

/** Row of `coach_recommendations` (owner select). */
@Serializable
internal data class CoachRecommendationDto(
    val id: String,
    @SerialName("plan_id") val planId: String? = null,
    val reason: String? = null,
    val payload: CalorieProposalPayloadDto,
) {
    fun toCalorieProposal(): CalorieProposal = CalorieProposal(
        id = id,
        planId = planId,
        fromKcal = payload.fromKcal.roundToInt(),
        toKcal = payload.toKcal.roundToInt(),
        changePct = payload.changePct,
        reasonCode = reason.orEmpty(),
        slopeKgWeek = payload.slopeKgWeek,
        intervalLow = payload.intervalLow,
        intervalHigh = payload.intervalHigh,
        rateKgWeek = payload.rateKgWeek,
        weeks = payload.weeks,
    )
}

@Serializable
internal data class PlanRequestBody(val kind: String, val input: PlanRequestInput)

@Serializable
internal data class PlanRequestInput(val today: LocalDate)

@Serializable
internal data class AcceptPlanParams(
    @SerialName("p_plan_id") val planId: String,
    @SerialName("p_today") val today: LocalDate,
)

@Serializable
internal data class DiscardPlanParams(@SerialName("p_plan_id") val planId: String)

@Serializable
internal data class SetCardioParams(
    @SerialName("p_plan_id") val planId: String,
    @SerialName("p_minutes") val minutes: Int,
)

@Serializable
internal data class EvaluateCalorieParams(@SerialName("p_today") val today: LocalDate)

/** `plan_recompute` call: no model key, the function answers without a paid call. */
@Serializable
internal data class RecomputeRequestBody(val kind: String, val input: RecomputeRequestInput)

@Serializable
internal data class RecomputeRequestInput(
    val today: LocalDate,
    @SerialName("plan_id") val planId: String,
    @SerialName("cut_rate_pct") val cutRatePct: Double,
    @SerialName("diet_break") val dietBreak: Boolean,
)

/** `plan_diet_break` call (accepted cut plan): deterministic, no model key, no usage. */
@Serializable
internal data class DietBreakRequestBody(val kind: String, val input: DietBreakRequestInput)

@Serializable
internal data class DietBreakRequestInput(
    val today: LocalDate,
    @SerialName("plan_id") val planId: String,
    @SerialName("diet_break") val dietBreak: Boolean,
)

@Serializable
internal data class ApplyDietBreakParams(@SerialName("p_today") val today: LocalDate)

/** Result of `apply_coach_diet_break`. */
@Serializable
internal data class ApplyDietBreakResultDto(
    val applied: Boolean = false,
    val week: Int = 0,
    @SerialName("target_kcal") val targetKcal: Double? = null,
)

@Serializable
internal data class DecideCalorieProposalParams(
    @SerialName("p_id") val id: String,
    @SerialName("p_apply") val apply: Boolean,
    @SerialName("p_today") val today: LocalDate,
)
