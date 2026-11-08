package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.DiscomfortFollowUp
import io.github.lopezjavier.appfitness.domain.model.DiscomfortNoteReason
import io.github.lopezjavier.appfitness.domain.model.SwapCandidate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** Row of `coach_discomfort_followups()`. */
@Serializable
internal data class DiscomfortFollowUpDto(
    @SerialName("exercise_id") val exerciseId: String,
    @SerialName("discomfort_id") val discomfortId: String,
    @SerialName("logged_at") val loggedAt: Instant,
    @SerialName("offer_swap") val offerSwap: Boolean,
    @SerialName("show_note") val showNote: Boolean,
    @SerialName("note_reason") val noteReason: String? = null,
) {
    /** Throws [IllegalArgumentException] for an unknown reason: the repository maps it to a failed result. */
    fun toDomain() = DiscomfortFollowUp(
        exerciseId = exerciseId,
        discomfortId = discomfortId,
        loggedAt = loggedAt,
        offerSwap = offerSwap,
        showNote = showNote,
        noteReason = when (noteReason) {
            null -> null
            "days14" -> DiscomfortNoteReason.Days14
            "sessions3" -> DiscomfortNoteReason.Sessions3
            else -> throw IllegalArgumentException("Unknown note reason $noteReason")
        },
    )
}

/** Row of `coach_swap_candidates(p_exercise_id)`; `similarity` is ordering only and not kept. */
@Serializable
internal data class SwapCandidateDto(
    @SerialName("exercise_id") val exerciseId: String,
    @SerialName("is_favorite") val isFavorite: Boolean = false,
) {
    fun toDomain() = SwapCandidate(exerciseId, isFavorite)
}

@Serializable
internal data class DiscomfortExerciseParams(@SerialName("p_exercise_id") val exerciseId: String)

@Serializable
internal data class SwapFlaggedParams(
    @SerialName("p_exercise_id") val exerciseId: String,
    @SerialName("p_new_exercise_id") val newExerciseId: String,
)
