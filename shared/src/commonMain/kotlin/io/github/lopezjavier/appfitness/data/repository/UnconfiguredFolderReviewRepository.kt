package io.github.lopezjavier.appfitness.data.repository

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CoachBlock
import io.github.lopezjavier.appfitness.domain.model.FolderCopy
import io.github.lopezjavier.appfitness.domain.model.FolderReview
import io.github.lopezjavier.appfitness.domain.model.ReviewPurpose
import io.github.lopezjavier.appfitness.domain.repository.FolderReviewRepository
import kotlinx.datetime.LocalDate

/** Used when Supabase is not configured: every call fails. */
class UnconfiguredFolderReviewRepository : FolderReviewRepository {
    override suspend fun review(
        folderId: String,
        purpose: ReviewPurpose,
        planId: String?,
        today: LocalDate,
    ): Result<FolderReview> = Result.failure(AppException(AppError.Unknown))

    override suspend fun stored(reviewId: String): Result<FolderReview?> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun applyCopy(reviewId: String, changeIds: List<String>): Result<FolderCopy> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun blockFromReview(
        planId: String,
        today: LocalDate,
        reviewId: String,
        acceptedIds: List<String>,
    ): Result<CoachBlock> = Result.failure(AppException(AppError.Unknown))
}
