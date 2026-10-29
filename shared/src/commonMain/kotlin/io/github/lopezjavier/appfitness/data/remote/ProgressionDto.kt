package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.ProgressionAction
import io.github.lopezjavier.appfitness.domain.model.ProgressionReason
import io.github.lopezjavier.appfitness.domain.model.ProgressionSuggestion
import io.github.lopezjavier.appfitness.domain.model.SetUsed
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ProgressionParams(@SerialName("p_session_id") val sessionId: String)

@Serializable
internal data class SetUsedDto(
    @SerialName("session_date") val sessionDate: LocalDate,
    @SerialName("weight_kg") val weightKg: Double? = null,
    val reps: Int,
    val rpe: Double? = null,
) {
    fun toDomain() = SetUsed(sessionDate, weightKg ?: 0.0, reps, rpe)
}

/** One element of the `progression_suggestions` array. Unknown action or reason codes drop the element. */
@Serializable
internal data class ProgressionSuggestionDto(
    @SerialName("exercise_id") val exerciseId: String,
    val action: String,
    @SerialName("base_weight_kg") val baseWeightKg: Double? = null,
    @SerialName("goal_reps") val goalReps: Int,
    val reason: String,
    @SerialName("sets_used") val setsUsed: List<SetUsedDto> = emptyList(),
) {
    fun toDomain(): ProgressionSuggestion? {
        val parsedAction = ACTIONS[action]
        val parsedReason = REASONS[reason]
        return if (parsedAction == null || parsedReason == null) {
            null
        } else {
            ProgressionSuggestion(
                exerciseId = exerciseId,
                action = parsedAction,
                weightKg = baseWeightKg ?: 0.0,
                goalReps = goalReps,
                reason = parsedReason,
                setsUsed = setsUsed.map { it.toDomain() },
            )
        }
    }
}

private val ACTIONS = mapOf(
    "raise_weight" to ProgressionAction.RaiseWeight,
    "add_rep" to ProgressionAction.AddRep,
    "lower_weight" to ProgressionAction.LowerWeight,
)

private val REASONS = mapOf(
    "all_sets_at_max" to ProgressionReason.AllSetsAtMax,
    "below_max" to ProgressionReason.BelowMax,
    "two_sessions_below_min" to ProgressionReason.TwoSessionsBelowMin,
    "kept_discomfort" to ProgressionReason.KeptDiscomfort,
)
