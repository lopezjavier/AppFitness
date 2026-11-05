package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.CoachChange
import io.github.lopezjavier.appfitness.domain.model.CoachChangeDecision
import io.github.lopezjavier.appfitness.domain.model.CoachChangeFigures
import io.github.lopezjavier.appfitness.domain.model.CoachChangeKind
import io.github.lopezjavier.appfitness.domain.model.CoachChangeStatus
import io.github.lopezjavier.appfitness.domain.model.FlagDecisionResult
import io.github.lopezjavier.appfitness.domain.model.PlannedRoutineValues
import io.github.lopezjavier.appfitness.domain.model.PlannedSetValues
import io.github.lopezjavier.appfitness.domain.model.UndoResult
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

@Serializable
internal data class ApplyCoachProgressionParams(@SerialName("p_session_id") val sessionId: String)

@Serializable
internal data class UndoCoachChangeParams(@SerialName("p_id") val id: String)

@Serializable
internal data class DecideProgressionFlagParams(
    @SerialName("p_id") val id: String,
    @SerialName("p_decision") val decision: String,
)

@Serializable
internal data class SnapshotSetDto(
    @SerialName("set_index") val setIndex: Int,
    @SerialName("set_type") val setType: String? = null,
    @SerialName("target_weight_kg") val targetWeightKg: Double? = null,
    @SerialName("target_reps") val targetReps: Int? = null,
) {
    fun toDomain() = PlannedSetValues(setIndex, setType, targetWeightKg, targetReps)
}

@Serializable
internal data class SnapshotRoutineDto(
    @SerialName("routine_id") val routineId: String,
    @SerialName("exercise_id") val exerciseId: String,
    @SerialName("rep_min") val repMin: Int? = null,
    @SerialName("rep_max") val repMax: Int? = null,
    val sets: List<SnapshotSetDto> = emptyList(),
) {
    fun toDomain() = PlannedRoutineValues(routineId, exerciseId, repMin, repMax, sets.map { it.toDomain() })
}

/** A `coach_changes` row or an element of the `apply_coach_progression` array (which omits plan and dates). */
@Serializable
internal data class CoachChangeDto(
    val id: String,
    @SerialName("plan_id") val planId: String? = null,
    val kind: String,
    @SerialName("exercise_id") val exerciseId: String,
    val status: String,
    val reason: String = "",
    val figures: JsonElement? = null,
    @SerialName("before_values") val beforeValues: List<SnapshotRoutineDto>? = null,
    @SerialName("after_values") val afterValues: List<SnapshotRoutineDto>? = null,
    val decision: String? = null,
    @SerialName("created_at") val createdAt: Instant? = null,
    @SerialName("decided_at") val decidedAt: Instant? = null,
) {
    fun toDomain() = CoachChange(
        id = id,
        planId = planId,
        kind = KINDS[kind] ?: CoachChangeKind.Unknown,
        exerciseId = exerciseId,
        status = STATUSES[status] ?: CoachChangeStatus.Unknown,
        reason = reason,
        figures = (figures as? JsonObject).toFigures(),
        before = beforeValues?.map { it.toDomain() },
        after = afterValues?.map { it.toDomain() },
        createdAt = createdAt,
        decidedAt = decidedAt,
        decision = decision?.let { DECISIONS[it] ?: CoachChangeDecision.Unknown },
    )

    internal companion object {
        val KINDS = mapOf(
            "load_raise" to CoachChangeKind.LoadRaise,
            "load_drop" to CoachChangeKind.LoadDrop,
            "load_kept" to CoachChangeKind.LoadKept,
            "load_flag" to CoachChangeKind.LoadFlag,
            "no_range_flag" to CoachChangeKind.NoRangeFlag,
        )
        val STATUSES = mapOf(
            "applied" to CoachChangeStatus.Applied,
            "undone" to CoachChangeStatus.Undone,
            "pending" to CoachChangeStatus.Pending,
            "decided" to CoachChangeStatus.Decided,
        )
        val DECISIONS = mapOf(
            "raise" to CoachChangeDecision.Raise,
            "keep" to CoachChangeDecision.Keep,
            "range_added" to CoachChangeDecision.RangeAdded,
        )
    }
}

private fun JsonObject?.number(key: String): Double? = (this?.get(key) as? JsonPrimitive)?.doubleOrNull

private fun JsonObject?.whole(key: String): Int? = (this?.get(key) as? JsonPrimitive)?.intOrNull

/** Reads the figures tolerantly: a missing or mistyped key is null. */
private fun JsonObject?.toFigures() = CoachChangeFigures(
    weightFromKg = number("weight_from_kg"),
    weightToKg = number("weight_to_kg"),
    currentKg = number("current_kg"),
    nextKg = number("next_kg"),
    stepKg = number("step_kg"),
    percent = number("percent"),
    repsBest = whole("reps_best"),
    rpeMax = number("rpe_max"),
    sessions = whole("sessions"),
    repMin = whole("rep_min"),
    repMax = whole("rep_max"),
)

@Serializable
internal data class UndoResultDto(val restored: Int = 0, val skipped: Int = 0) {
    fun toDomain() = UndoResult(restored, skipped)
}

/** The rows of an RPC or select answer; a non-array body or an unreadable element yields nothing for it. */
internal fun coachChangesOf(element: JsonElement): List<CoachChange> {
    if (element !is JsonArray) return emptyList()
    return element.mapNotNull { item ->
        runCatching { coachPlanJson.decodeFromJsonElement(CoachChangeDto.serializer(), item).toDomain() }.getOrNull()
    }
}

/** `{flag, change}` of `decide_progression_flag`; null when the flag cannot be read. */
internal fun flagDecisionOf(element: JsonElement): FlagDecisionResult? {
    val obj = element as? JsonObject
    val flag = obj?.get("flag").toChange()
    return flag?.let { FlagDecisionResult(it, obj?.get("change").toChange()) }
}

private fun JsonElement?.toChange(): CoachChange? {
    if (this == null || this is JsonNull) return null
    return runCatching { coachPlanJson.decodeFromJsonElement(CoachChangeDto.serializer(), this).toDomain() }.getOrNull()
}
