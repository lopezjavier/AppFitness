package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.BlockPause
import io.github.lopezjavier.appfitness.domain.model.CoachChange
import io.github.lopezjavier.appfitness.domain.model.DeloadExerciseSuggestion
import io.github.lopezjavier.appfitness.domain.model.PlanWeek
import io.github.lopezjavier.appfitness.domain.model.PlanWeekSession
import io.github.lopezjavier.appfitness.domain.model.WeekPhase
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class PlanWeeksParams(
    @SerialName("p_plan_id") val planId: String,
    @SerialName("p_today") val today: LocalDate,
)

@Serializable
internal data class EvaluateDeloadParams(@SerialName("p_today") val today: LocalDate)

@Serializable
internal data class CancelDeloadParams(@SerialName("p_change_id") val changeId: String)

@Serializable
internal data class DeloadSuggestionsParams(@SerialName("p_session_id") val sessionId: String)

@Serializable
internal data class PlanWeekSessionDto(
    @SerialName("session_index") val sessionIndex: Int,
    val name: String? = null,
    @SerialName("routine_id") val routineId: String? = null,
    @SerialName("session_id") val sessionId: String? = null,
    val done: Boolean = false,
) {
    fun toDomain() = PlanWeekSession(sessionIndex, name.orEmpty(), routineId, done)
}

/** Result of `coach_block_pause`; a missing key reads as "not paused, nothing moved". */
@Serializable
internal data class BlockPauseDto(
    val paused: Boolean = false,
    @SerialName("paused_weeks") val pausedWeeks: Int = 0,
    @SerialName("block_end") val blockEnd: LocalDate? = null,
) {
    fun toDomain() = BlockPause(paused, pausedWeeks, blockEnd)
}

/** One element of the `coach_plan_weeks` array. An unknown phase code drops the week. */
@Serializable
internal data class PlanWeekDto(
    val week: Int,
    @SerialName("start_date") val startDate: LocalDate,
    @SerialName("end_date") val endDate: LocalDate,
    val phase: String,
    val deload: Boolean = false,
    val planned: Int = 0,
    val completed: Int = 0,
    @SerialName("adherence_pct") val adherencePct: Int? = null,
    val sessions: List<PlanWeekSessionDto> = emptyList(),
    @SerialName("cardio_min_done") val cardioMinDone: Int = 0,
    @SerialName("cardio_min_target") val cardioMinTarget: Int? = null,
    @SerialName("steps_avg") val stepsAvg: Int? = null,
    @SerialName("steps_target") val stepsTarget: Int? = null,
) {
    fun toDomain(): PlanWeek? {
        val parsed = PHASES[phase] ?: return null
        return PlanWeek(
            week = week,
            startDate = startDate,
            endDate = endDate,
            phase = parsed,
            deload = deload,
            planned = planned,
            completed = completed,
            adherencePct = adherencePct ?: 0,
            sessions = sessions.sortedBy { it.sessionIndex }.map { it.toDomain() },
            cardioMinDone = cardioMinDone,
            cardioMinTarget = cardioMinTarget,
            stepsAvg = stepsAvg,
            stepsTarget = stepsTarget,
        )
    }
}

private val PHASES = mapOf(
    "past" to WeekPhase.Past,
    "current" to WeekPhase.Current,
    "upcoming" to WeekPhase.Upcoming,
)

/** Result of `evaluate_coach_deload`: only `applied` carries the deload change. */
@Serializable
internal data class EvaluateDeloadResultDto(
    val status: String,
    val reason: String? = null,
    val week: Int? = null,
    val change: JsonElement? = null,
) {
    fun toChange(): CoachChange? = if (status == "applied") change.toCoachChange() else null
}

/** One change object (`request_coach_deload`, `cancel_coach_deload`, the `change` of the evaluation). */
internal fun JsonElement?.toCoachChange(): CoachChange? = if (this == null || this is JsonNull) {
    null
} else {
    runCatching { coachPlanJson.decodeFromJsonElement(CoachChangeDto.serializer(), this).toDomain() }.getOrNull()
}

@Serializable
internal data class DeloadExerciseDto(
    @SerialName("exercise_id") val exerciseId: String,
    @SerialName("planned_sets") val plannedSets: Int,
    @SerialName("deload_sets") val deloadSets: Int,
) {
    fun toDomain() = DeloadExerciseSuggestion(exerciseId, plannedSets, deloadSets)
}

@Serializable
internal data class DeloadSuggestionsDto(val week: Int? = null, val exercises: List<DeloadExerciseDto> = emptyList())

/** `deload_suggestions` returns an object inside a deload week and `[]` otherwise. */
internal fun deloadSuggestionsOf(element: JsonElement): List<DeloadExerciseSuggestion> {
    if (element !is JsonObject) return emptyList()
    return coachPlanJson.decodeFromJsonElement(DeloadSuggestionsDto.serializer(), element)
        .exercises.map { it.toDomain() }
}
