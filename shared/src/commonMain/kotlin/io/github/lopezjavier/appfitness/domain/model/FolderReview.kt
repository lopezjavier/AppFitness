package io.github.lopezjavier.appfitness.domain.model

/** Why a folder review was asked: only to read it, or as the checklist of a block built from the folder. */
enum class ReviewPurpose { Review, Block }

/** A hard failure ("Falta") the checker found in a folder; [data] holds the figures of the rule, as text. */
data class FolderFalta(
    val code: String,
    val routineIndex: Int?,
    val exerciseKey: String?,
    val data: Map<String, String>,
)

/** A soft comment of the coach, optionally about one exercise. */
data class ReviewComment(val text: String, val exerciseKey: String?)

/** One planned set of an `add_exercise` change. */
data class FolderSetSpec(
    val n: Int,
    val drop: Boolean,
    val repMin: Int,
    val repMax: Int,
    val rpe: Double,
    val restSeconds: Int,
)

/** The closed set of typed changes; `routineIndex` and `position` are 1-based. */
sealed interface FolderOp {
    data class AddExercise(val routineIndex: Int, val exerciseKey: String, val sets: List<FolderSetSpec>) : FolderOp

    data class ReplaceExercise(val routineIndex: Int, val position: Int, val exerciseKey: String) : FolderOp

    data class RemoveExercise(val routineIndex: Int, val position: Int) : FolderOp

    data class SetSets(val routineIndex: Int, val position: Int, val n: Int) : FolderOp

    data class SetRepRange(val routineIndex: Int, val position: Int, val repMin: Int, val repMax: Int) : FolderOp

    data class SetRpe(val routineIndex: Int, val position: Int, val rpe: Double) : FolderOp

    data class SetRest(val routineIndex: Int, val position: Int, val restSeconds: Int) : FolderOp
}

/**
 * A re-checked change. [fixes] is the code of the Falta item it resolves; [conflictsWith] lists changes on the same
 * target (the user takes only one of each group).
 */
data class FolderChange(
    val id: String,
    val op: FolderOp,
    val reason: String,
    val fixes: String?,
    val conflictsWith: List<String>,
)

data class FolderReview(
    val id: String,
    val folderId: String?,
    val purpose: ReviewPurpose,
    val falta: List<FolderFalta>,
    val notes: List<String>,
    val message: String,
    val comments: List<ReviewComment>,
    val changes: List<FolderChange>,
)
