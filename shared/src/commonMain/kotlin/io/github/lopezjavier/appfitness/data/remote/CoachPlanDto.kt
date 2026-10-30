package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.CalorieChange
import io.github.lopezjavier.appfitness.domain.model.CoachPlan
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.OutcomeKind
import io.github.lopezjavier.appfitness.domain.model.PlanLimit
import io.github.lopezjavier.appfitness.domain.model.PlanNutrition
import io.github.lopezjavier.appfitness.domain.model.PlanOutcome
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.domain.model.ProteinDistribution
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
) {
    fun toDomain(): PlanNutrition = PlanNutrition(
        targetKcal = targetKcal.roundToInt(),
        floorKcal = floorKcal?.roundToInt(),
        limitedBy = when (limitedBy) {
            "floor" -> PlanLimit.Floor
            "goal_range" -> PlanLimit.GoalRange
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
    )
}

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
        nutrition = nutrition.toDomain(),
        outcomes = outcomes.mapNotNull(PlanOutcomeDto::toDomain),
        acceptedAt = acceptedAt,
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
internal data class CalorieChangePayloadDto(
    @SerialName("from_kcal") val fromKcal: Double,
    @SerialName("to_kcal") val toKcal: Double,
    @SerialName("change_pct") val changePct: Double = 0.0,
    @SerialName("slope_kg_week") val slopeKgWeek: Double? = null,
    @SerialName("interval_low") val intervalLow: Double? = null,
    @SerialName("interval_high") val intervalHigh: Double? = null,
)

/** Row of `coach_recommendations` (owner select). */
@Serializable
internal data class CoachRecommendationDto(
    val id: String,
    @SerialName("plan_id") val planId: String? = null,
    val reason: String? = null,
    val payload: CalorieChangePayloadDto,
) {
    fun toCalorieChange(): CalorieChange = CalorieChange(
        id = id,
        planId = planId,
        fromKcal = payload.fromKcal.roundToInt(),
        toKcal = payload.toKcal.roundToInt(),
        changePct = payload.changePct,
        reasonCode = reason.orEmpty(),
        slopeKgWeek = payload.slopeKgWeek,
        intervalLow = payload.intervalLow,
        intervalHigh = payload.intervalHigh,
    )
}

/** Result of `evaluate_coach_calorie_change`; `recommendation_id` only when a change was created. */
@Serializable
internal data class CalorieEvaluationDto(
    val status: String = "none",
    @SerialName("recommendation_id") val recommendationId: String? = null,
)

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
internal data class EvaluateCalorieParams(@SerialName("p_today") val today: LocalDate)

@Serializable
internal data class DecideRecommendationParams(
    @SerialName("p_id") val id: String,
    @SerialName("p_status") val status: String,
    @SerialName("p_today") val today: LocalDate,
)
