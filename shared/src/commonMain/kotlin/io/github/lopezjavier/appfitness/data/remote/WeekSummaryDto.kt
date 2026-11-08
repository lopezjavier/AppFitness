package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.GroupSets
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.ProposalStatus
import io.github.lopezjavier.appfitness.domain.model.WeekSummary
import io.github.lopezjavier.appfitness.domain.model.WeekSummaryDecision
import io.github.lopezjavier.appfitness.domain.model.WeekSummaryNutrition
import io.github.lopezjavier.appfitness.domain.model.WeekSummaryWeight
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.roundToInt

@Serializable
internal data class WeekSummaryParams(@SerialName("p_today") val today: LocalDate)

@Serializable
internal data class MarkWeekSummarySeenParams(
    @SerialName("p_plan_id") val planId: String,
    @SerialName("p_week") val week: Int,
)

internal fun groupsOf(element: JsonElement?): List<GroupSets> =
    ((element as? JsonArray) ?: emptyList<JsonElement>()).mapNotNull { item ->
        val obj = item.obj()
        val group = obj?.text("muscle_group")?.let { code ->
            runCatching { coachPlanJson.decodeFromJsonElement(MuscleGroup.serializer(), JsonPrimitive(code)) }
                .getOrNull()
        }
        val sets = obj?.number("sets")
        if (group == null || sets == null) null else GroupSets(group, sets)
    }

private val PROPOSAL_STATUSES = mapOf(
    "pending" to ProposalStatus.Pending,
    "accepted" to ProposalStatus.Applied,
    "edited" to ProposalStatus.Applied,
    "discarded" to ProposalStatus.Declined,
)

private fun decisionOf(obj: JsonObject?): WeekSummaryDecision? {
    val status = obj?.text("status")?.let(PROPOSAL_STATUSES::get)
    val row = obj?.let {
        runCatching { coachPlanJson.decodeFromJsonElement(CoachRecommendationDto.serializer(), it) }.getOrNull()
    }
    return if (status == null || row == null) null else WeekSummaryDecision(row.toCalorieProposal(), status)
}

private fun decisionsOf(element: JsonElement?): List<WeekSummaryDecision> =
    ((element as? JsonArray) ?: emptyList<JsonElement>()).mapNotNull { decisionOf(it.obj()) }

/** A missing key (a summary from before the migration) yields null; missing figures are null. */
internal fun nutritionOf(obj: JsonObject?): WeekSummaryNutrition? = obj?.let {
    WeekSummaryNutrition(
        completeDays = it.count("complete_days"),
        gateOk = it.truth("gate_ok"),
        avgKcal = it.number("avg_kcal")?.roundToInt(),
        avgProteinG = it.number("avg_protein_g"),
        targetKcal = it.number("target_kcal")?.roundToInt(),
        targetProteinG = it.number("target_protein_g")?.roundToInt(),
        proteinGPerKg = it.number("protein_g_per_kg"),
        decisions = decisionsOf(it["decisions"]),
    )
}

private fun weightOf(obj: JsonObject?): WeekSummaryWeight? = obj?.let {
    WeekSummaryWeight(
        avgKg = it.number("avg_kg"),
        slopeKgWeek = it.number("slope_kg_week"),
        intervalLow = it.number("interval_low"),
        intervalHigh = it.number("interval_high"),
        targetRateKgWeek = it.number("target_rate_kg_week"),
    )
}

internal fun JsonObject.day(key: String): LocalDate? = text(key)?.let {
    runCatching { LocalDate.parse(it) }.getOrNull()
}

/**
 * Reads the `coach_week_summary` answer tolerantly: JSON null, a non-object body or a missing identity key
 * (plan, week, dates) yield null; any other missing figure falls back to zero, empty or null.
 */
internal fun weekSummaryOf(element: JsonElement): WeekSummary? {
    val obj = element.obj() ?: JsonObject(emptyMap()) // Without identity keys it yields null below.
    val planId = obj.text("plan_id")
    val start = obj.day("start_date")
    val end = obj.day("end_date")
    if (planId == null || start == null || end == null) return null
    // A paused week (`{plan_id, paused:true, start_date, end_date}`) carries no week number or figures.
    return if (obj.truth("paused")) {
        WeekSummary.pausedWeek(planId, start, end)
    } else {
        obj.int("week")?.let { week ->
            WeekSummary(
                planId = planId,
                week = week,
                startDate = start,
                endDate = end,
                seen = obj.truth("seen"),
                sessionsDone = obj.count("sessions_done"),
                sessionsPlanned = obj.count("sessions_planned"),
                groups = groupsOf(obj["groups"]),
                weight = weightOf(obj["weight"].obj()),
                completeDays = obj.count("complete_days"),
                weighInDays = obj.count("weigh_in_days"),
                changes = coachChangesOf(obj["changes"] ?: JsonNull),
                deload = obj.truth("deload"),
                stalls = stallsOf(obj["stalls"]),
                nutrition = nutritionOf(obj["nutrition"].obj()),
            )
        }
    }
}
