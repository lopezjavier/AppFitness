package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.BlockAdherence
import io.github.lopezjavier.appfitness.domain.model.BlockLift
import io.github.lopezjavier.appfitness.domain.model.BlockLiftWeek
import io.github.lopezjavier.appfitness.domain.model.BlockOutcome
import io.github.lopezjavier.appfitness.domain.model.BlockReview
import io.github.lopezjavier.appfitness.domain.model.BlockWaist
import io.github.lopezjavier.appfitness.domain.model.BlockWeight
import io.github.lopezjavier.appfitness.domain.model.OutcomeKind
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.PhaseChangeReason
import io.github.lopezjavier.appfitness.domain.model.PhaseDecision
import io.github.lopezjavier.appfitness.domain.model.PhaseProposal
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class OpenBlockReviewParams(@SerialName("p_today") val today: LocalDate)

@Serializable
internal data class KeepPhaseParams(@SerialName("p_review_id") val reviewId: String)

@Serializable
internal data class PhaseChangeRequestBody(val kind: String, val input: PhaseChangeRequestInput)

@Serializable
internal data class PhaseChangeRequestInput(val today: LocalDate, @SerialName("review_id") val reviewId: String)

private val PHASES = mapOf(
    "cut" to Phase.Cut,
    "gain" to Phase.Gain,
    "maintenance" to Phase.Maintenance,
    "recomposition" to Phase.Recomposition,
)

private val REASONS = mapOf(
    "cut_length" to PhaseChangeReason.CutLength,
    "floor_limited" to PhaseChangeReason.FloorLimited,
    "back_to_goal" to PhaseChangeReason.BackToGoal,
)

private val DECISIONS = mapOf("accepted" to PhaseDecision.Accepted, "kept" to PhaseDecision.Kept)

private val OUTCOME_KINDS = mapOf("weight" to OutcomeKind.Weight, "e1rm" to OutcomeKind.E1rm)

private fun JsonElement?.items(): List<JsonObject> =
    ((this as? JsonArray) ?: emptyList<JsonElement>()).mapNotNull { it.obj() }

private fun adherenceOf(obj: JsonObject?) = BlockAdherence(
    completed = obj?.count("completed") ?: 0,
    planned = obj?.count("planned") ?: 0,
    pct = obj?.int("pct"),
)

private fun liftOf(obj: JsonObject): BlockLift? {
    val id = obj.text("exercise_id") ?: return null
    val weeks = obj["weeks"].items().mapNotNull { w ->
        val week = w.int("week")
        val e1rm = w.number("e1rm")
        if (week == null || e1rm == null) null else BlockLiftWeek(week, e1rm)
    }
    return BlockLift(
        exerciseId = id,
        weeks = weeks,
        firstE1rm = obj.number("first_e1rm"),
        lastE1rm = obj.number("last_e1rm"),
        changeKg = obj.number("change_kg"),
        stalled = obj.truth("stalled"),
    )
}

private fun weightOf(obj: JsonObject?): BlockWeight? = obj?.let {
    BlockWeight(
        firstAvgKg = it.number("first_avg_kg"),
        lastAvgKg = it.number("last_avg_kg"),
        slopeKgWeek = it.number("slope_kg_week"),
        intervalLow = it.number("interval_low"),
        intervalHigh = it.number("interval_high"),
        targetRateKgWeek = it.number("target_rate_kg_week"),
    )
}

/** An outcome of a kind this app version does not know is dropped. */
private fun outcomeOf(obj: JsonObject): BlockOutcome? {
    val kind = obj.text("kind")?.let(OUTCOME_KINDS::get) ?: return null
    return BlockOutcome(
        kind = kind,
        key = obj.text("key").orEmpty(),
        targetValue = obj.number("target_value"),
        unit = obj.text("unit"),
        deadline = obj.day("deadline"),
        currentValue = obj.number("current_value"),
        reached = obj.truth("reached"),
    )
}

private fun waistOf(obj: JsonObject?): BlockWaist? {
    if (obj == null) return null
    val delta = obj.number("delta_cm")
    val from = obj.day("from_date")
    val to = obj.day("to_date")
    return if (delta == null || from == null || to == null) {
        null
    } else {
        BlockWaist(delta, from, to, obj.truth("within_margin"))
    }
}

/** An unknown phase or reason (a newer server) yields no proposal, never an error. */
private fun proposalOf(obj: JsonObject?): PhaseProposal? {
    val phase = obj?.text("to_phase")?.let(PHASES::get)
    val reason = obj?.text("reason")?.let(REASONS::get)
    val raw = obj?.get("figures").obj() ?: JsonObject(emptyMap())
    val figures = raw.keys.mapNotNull { key -> raw.number(key)?.let { key to it } }.toMap()
    return if (phase == null || reason == null) null else PhaseProposal(phase, reason, figures)
}

/**
 * Reads the `open_coach_block_review` answer tolerantly: JSON null, a non-object body or a missing identity key
 * (id, plan, end date) yield null; any other missing figure falls back to zero, empty or null.
 */
internal fun blockReviewOf(element: JsonElement): BlockReview? {
    val obj = element.obj() ?: JsonObject(emptyMap()) // Without identity keys it yields null below.
    val id = obj.text("id")
    val planId = obj.text("plan_id")
    val endedOn = obj.day("ended_on")
    if (id == null || planId == null || endedOn == null) return null
    val inputs = obj["inputs"].obj()
    return BlockReview(
        id = id,
        planId = planId,
        endedOn = endedOn,
        adherence = adherenceOf(inputs?.get("adherence").obj()),
        lifts = inputs?.get("lifts").items().mapNotNull(::liftOf),
        groups = groupsOf(inputs?.get("groups")),
        weight = weightOf(inputs?.get("weight").obj()),
        outcomes = inputs?.get("outcomes").items().mapNotNull(::outcomeOf),
        waist = waistOf(inputs?.get("waist").obj()),
        stalls = stallsOf(inputs?.get("stalls")),
        phaseProposal = proposalOf(obj["phase_proposal"].obj()),
        phaseDecision = obj.text("phase_decision")?.let(DECISIONS::get),
    )
}
