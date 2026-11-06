package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.GroupSets
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.ProposalStatus
import io.github.lopezjavier.appfitness.domain.model.StallCause
import io.github.lopezjavier.appfitness.domain.model.StallDiagnosis
import io.github.lopezjavier.appfitness.domain.model.WeekSummary
import io.github.lopezjavier.appfitness.domain.model.WeekSummaryProposal
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

private fun groupsOf(element: JsonElement?): List<GroupSets> =
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

private fun proposalOf(obj: JsonObject?): WeekSummaryProposal? {
    val status = obj?.text("status")?.let(PROPOSAL_STATUSES::get)
    val row = obj?.let {
        runCatching { coachPlanJson.decodeFromJsonElement(CoachRecommendationDto.serializer(), it) }.getOrNull()
    }
    return if (status == null || row == null) null else WeekSummaryProposal(row.toCalorieProposal(), status)
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

private fun JsonObject.day(key: String): LocalDate? = text(key)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

/**
 * Reads the `coach_week_summary` answer tolerantly: JSON null, a non-object body or a missing identity key
 * (plan, week, dates) yield null; any other missing figure falls back to zero, empty or null.
 */
internal fun weekSummaryOf(element: JsonElement): WeekSummary? {
    val obj = element.obj() ?: JsonObject(emptyMap()) // Without identity keys it yields null below.
    val key = obj.text("plan_id")?.let { id -> obj.int("week")?.let { id to it } }
    val start = obj.day("start_date")
    val end = obj.day("end_date")
    if (key == null || start == null || end == null) return null
    return WeekSummary(
        planId = key.first,
        week = key.second,
        startDate = start,
        endDate = end,
        seen = obj.truth("seen"),
        sessionsDone = obj.count("sessions_done"),
        sessionsPlanned = obj.count("sessions_planned"),
        groups = groupsOf(obj["groups"]),
        weight = weightOf(obj["weight"].obj()),
        completeDays = obj.count("complete_days"),
        weighInDays = obj.count("weigh_in_days"),
        missingRecords = obj.truth("missing_records"),
        changes = coachChangesOf(obj["changes"] ?: JsonNull),
        calorieProposal = proposalOf(obj["calorie_proposal"].obj()),
        deload = obj.truth("deload"),
        stalls = stallsOf(obj["stalls"]),
    )
}

private fun muscleGroupOf(code: String?): MuscleGroup? = code?.let {
    runCatching { coachPlanJson.decodeFromJsonElement(MuscleGroup.serializer(), JsonPrimitive(it)) }.getOrNull()
}

private fun causeOf(obj: JsonObject): StallCause? = when (obj.text("cause")) {
    "sessions" -> obj.number("avg_sessions")?.let { StallCause.Sessions(it, obj.count("plan_days")) }
    "sets" -> setsCauseOf(obj)
    "maintain" -> muscleGroupOf(obj.text("group"))?.let { StallCause.GroupOnMaintain(it) }
    "protein" -> proteinCauseOf(obj)
    "sleep" -> obj.number("avg_sleep_h")?.let { StallCause.Sleep(it) }
    "none" -> StallCause.NoClearCause(obj.truth("protein_gate_missing"))
    else -> null
}

private fun setsCauseOf(obj: JsonObject): StallCause? {
    val avg = obj.number("avg_sets")
    val group = muscleGroupOf(obj.text("group"))
    return if (avg == null || group == null) null else StallCause.Sets(avg, group)
}

private fun proteinCauseOf(obj: JsonObject): StallCause? {
    val grams = obj.number("avg_protein_g")
    val perKg = obj.number("protein_g_per_kg")
    val target = obj.number("target_g")
    return if (grams == null || perKg == null || target == null) {
        null
    } else {
        StallCause.Protein(grams.roundToInt(), perKg, target.roundToInt())
    }
}

/** Stalls of an old row (no key) or with an unknown cause or missing figures are dropped, never an error. */
private fun stallsOf(element: JsonElement?): List<StallDiagnosis> =
    ((element as? JsonArray) ?: emptyList<JsonElement>()).mapNotNull { item ->
        val obj = item.obj()
        val id = obj?.text("exercise_id")
        val cause = obj?.let(::causeOf)
        if (id == null || cause == null) null else StallDiagnosis(id, cause)
    }
