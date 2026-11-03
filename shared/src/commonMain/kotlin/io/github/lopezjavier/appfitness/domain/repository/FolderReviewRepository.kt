package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.CoachBlock
import io.github.lopezjavier.appfitness.domain.model.FolderCopy
import io.github.lopezjavier.appfitness.domain.model.FolderReview
import io.github.lopezjavier.appfitness.domain.model.ReviewPurpose
import kotlinx.datetime.LocalDate

/**
 * Review of a routine folder by the coach (US-95) and the block built from it (US-93). The folder is never written.
 * Failures carry an [io.github.lopezjavier.appfitness.domain.AppException] or a
 * [io.github.lopezjavier.appfitness.domain.model.CoachException].
 */
interface FolderReviewRepository {
    /** Reviews [folderId] (a paid call). [planId] is required for [ReviewPurpose.Block]. */
    suspend fun review(
        folderId: String,
        purpose: ReviewPurpose,
        planId: String?,
        today: LocalDate,
    ): Result<FolderReview>

    /** The stored review, or null when it does not exist (or is not the caller's). */
    suspend fun stored(reviewId: String): Result<FolderReview?>

    /**
     * Copies the folder with the chosen changes applied. Unknown ids fail with
     * [io.github.lopezjavier.appfitness.domain.model.CoachFailure.InvalidReplacement]; a folder edited since the
     * review fails with [io.github.lopezjavier.appfitness.domain.model.CoachFailure.Conflict].
     */
    suspend fun applyCopy(reviewId: String, changeIds: List<String>): Result<FolderCopy>

    /**
     * Builds and stores the block of [planId] from the reviewed folder plus the accepted changes (no model call).
     * A folder that still breaks a rule fails with
     * [io.github.lopezjavier.appfitness.domain.model.CoachFailure.NeedsChanges].
     */
    suspend fun blockFromReview(
        planId: String,
        today: LocalDate,
        reviewId: String,
        acceptedIds: List<String>,
    ): Result<CoachBlock>
}
