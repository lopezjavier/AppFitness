package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.FolderChange
import io.github.lopezjavier.appfitness.domain.model.FolderFalta
import io.github.lopezjavier.appfitness.domain.model.FolderOp
import io.github.lopezjavier.appfitness.domain.model.FolderReview
import io.github.lopezjavier.appfitness.domain.model.FolderSetSpec
import io.github.lopezjavier.appfitness.domain.model.ReviewComment
import io.github.lopezjavier.appfitness.domain.model.ReviewPurpose
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
internal data class FolderReviewRequestBody(
    val kind: String,
    val input: FolderReviewRequestInput,
    val model: String? = null,
)

@Serializable
internal data class FolderReviewRequestInput(
    val today: LocalDate,
    @SerialName("folder_id") val folderId: String,
    val purpose: String,
    @SerialName("plan_id") val planId: String? = null,
)

/** `plan_block` input of the folder start: no split, no model call. */
@Serializable
internal data class FolderBlockRequestInput(
    val today: LocalDate,
    @SerialName("plan_id") val planId: String,
    val start: String,
    @SerialName("review_id") val reviewId: String,
    @SerialName("accepted_change_ids") val acceptedChangeIds: List<String>,
)

@Serializable
internal data class FolderBlockRequestBody(val kind: String, val input: FolderBlockRequestInput)

@Serializable
internal data class FolderFaltaDto(
    val code: String,
    @SerialName("routine_index") val routineIndex: Int? = null,
    @SerialName("exercise_key") val exerciseKey: String? = null,
    val data: JsonObject = JsonObject(emptyMap()),
) {
    fun toDomain(): FolderFalta = FolderFalta(
        code,
        routineIndex,
        exerciseKey,
        data.mapValues { (_, value) -> (value as? JsonPrimitive)?.content ?: value.toString() },
    )
}

@Serializable
internal data class ReviewCommentDto(val text: String, @SerialName("exercise_key") val exerciseKey: String? = null)

@Serializable
internal data class FolderSetSpecDto(
    val n: Int,
    val type: String = "working",
    @SerialName("rep_min") val repMin: Int,
    @SerialName("rep_max") val repMax: Int,
    val rpe: Double,
    @SerialName("rest_seconds") val restSeconds: Int,
)

/** One change as the function sends it: a flat object whose `op` says which of the other fields are set. */
@Serializable
internal data class FolderChangeDto(
    val id: String,
    val op: String,
    @SerialName("routine_index") val routineIndex: Int,
    val position: Int? = null,
    @SerialName("exercise_key") val exerciseKey: String? = null,
    val sets: List<FolderSetSpecDto>? = null,
    val n: Int? = null,
    @SerialName("rep_min") val repMin: Int? = null,
    @SerialName("rep_max") val repMax: Int? = null,
    val rpe: Double? = null,
    @SerialName("rest_seconds") val restSeconds: Int? = null,
    val reason: String = "",
    val fixes: String? = null,
    @SerialName("conflicts_with") val conflictsWith: List<String> = emptyList(),
) {
    /** Null for an unknown op or a missing field of its op. */
    fun toDomain(): FolderChange? {
        val op = opOrNull() ?: return null
        return FolderChange(id, op, reason, fixes, conflictsWith)
    }

    @Suppress("CyclomaticComplexMethod")
    private fun opOrNull(): FolderOp? = when (op) {
        "add_exercise" -> if (exerciseKey != null && sets != null) {
            FolderOp.AddExercise(
                routineIndex,
                exerciseKey,
                sets.map { FolderSetSpec(it.n, it.type == "drop", it.repMin, it.repMax, it.rpe, it.restSeconds) },
            )
        } else {
            null
        }

        "replace_exercise" -> if (position != null && exerciseKey != null) {
            FolderOp.ReplaceExercise(routineIndex, position, exerciseKey)
        } else {
            null
        }

        "remove_exercise" -> position?.let { FolderOp.RemoveExercise(routineIndex, it) }

        "set_sets" -> if (position != null && n != null) FolderOp.SetSets(routineIndex, position, n) else null

        "set_rep_range" -> if (position != null && repMin != null && repMax != null) {
            FolderOp.SetRepRange(routineIndex, position, repMin, repMax)
        } else {
            null
        }

        "set_rpe" -> if (position != null && rpe != null) FolderOp.SetRpe(routineIndex, position, rpe) else null

        "set_rest" -> if (position != null && restSeconds != null) {
            FolderOp.SetRest(routineIndex, position, restSeconds)
        } else {
            null
        }

        else -> null
    }
}

/** The review result: the stream adds `review_id` and `purpose` to what `coach_folder_reviews.result` stores. */
@Serializable
internal data class FolderReviewResultDto(
    @SerialName("review_id") val reviewId: String? = null,
    val purpose: String? = null,
    val message: String = "",
    val falta: List<FolderFaltaDto> = emptyList(),
    val notes: List<String> = emptyList(),
    val comments: List<ReviewCommentDto> = emptyList(),
    val changes: List<FolderChangeDto> = emptyList(),
) {
    /** Null when a change has an unknown op: the screen cannot show what it cannot apply. */
    fun toDomain(id: String, folderId: String?, fallbackPurpose: ReviewPurpose): FolderReview? {
        val domainChanges = changes.map { it.toDomain() ?: return null }
        return FolderReview(
            id = id,
            folderId = folderId,
            purpose = purposeOf(purpose) ?: fallbackPurpose,
            falta = falta.map(FolderFaltaDto::toDomain),
            notes = notes,
            message = message,
            comments = comments.map { ReviewComment(it.text, it.exerciseKey) },
            changes = domainChanges,
        )
    }
}

/** A row of `coach_folder_reviews`. */
@Serializable
internal data class StoredFolderReviewDto(
    val id: String,
    @SerialName("folder_id") val folderId: String? = null,
    val purpose: String,
    val result: FolderReviewResultDto,
) {
    fun toDomain(): FolderReview? = result.toDomain(id, folderId, purposeOf(purpose) ?: ReviewPurpose.Review)
}

internal fun purposeOf(value: String?): ReviewPurpose? = when (value) {
    "review" -> ReviewPurpose.Review
    "block" -> ReviewPurpose.Block
    else -> null
}

internal fun ReviewPurpose.wire(): String = when (this) {
    ReviewPurpose.Review -> "review"
    ReviewPurpose.Block -> "block"
}
