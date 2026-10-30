package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.DeloadExerciseSuggestion
import io.github.lopezjavier.appfitness.domain.model.DeloadProposal
import io.github.lopezjavier.appfitness.domain.model.PlanWeek
import io.github.lopezjavier.appfitness.domain.model.PlanWeekSession
import io.github.lopezjavier.appfitness.domain.model.WeekPhase
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class PlanWeeksParams(
    @SerialName("p_plan_id") val planId: String,
    @SerialName("p_today") val today: LocalDate,
)

@Serializable
internal data class EvaluateDeloadParams(@SerialName("p_today") val today: LocalDate)

@Serializable
internal data class DecideDeloadParams(@SerialName("p_id") val id: String, @SerialName("p_accept") val accept: Boolean)

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
        )
    }
}

private val PHASES = mapOf(
    "past" to WeekPhase.Past,
    "current" to WeekPhase.Current,
    "upcoming" to WeekPhase.Upcoming,
)

/** Result of `evaluate_coach_deload`: only `created` and `pending` carry a proposal. */
@Serializable
internal data class EvaluateDeloadResultDto(
    val status: String,
    val reason: String? = null,
    @SerialName("recommendation_id") val recommendationId: String? = null,
    @SerialName("plan_id") val planId: String? = null,
    val week: Int? = null,
) {
    fun toProposal(): DeloadProposal? = if (status == "created" || status == "pending") {
        if (recommendationId != null && planId != null && week != null) {
            DeloadProposal(recommendationId, planId, week)
        } else {
            null
        }
    } else {
        null
    }
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
