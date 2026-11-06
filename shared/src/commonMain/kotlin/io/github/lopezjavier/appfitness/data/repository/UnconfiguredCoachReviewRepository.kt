package io.github.lopezjavier.appfitness.data.repository

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.BlockReview
import io.github.lopezjavier.appfitness.domain.repository.CoachReviewRepository
import kotlinx.datetime.LocalDate

/** Used when Supabase is not configured: every call fails. */
class UnconfiguredCoachReviewRepository : CoachReviewRepository {
    override suspend fun openReview(today: LocalDate): Result<BlockReview?> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun keepPhase(reviewId: String): Result<Unit> = Result.failure(AppException(AppError.Unknown))

    override suspend fun acceptPhase(reviewId: String, today: LocalDate): Result<String> =
        Result.failure(AppException(AppError.Unknown))
}
