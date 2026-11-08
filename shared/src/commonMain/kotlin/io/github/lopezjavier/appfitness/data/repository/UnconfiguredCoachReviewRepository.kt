package io.github.lopezjavier.appfitness.data.repository

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.BlockReview
import io.github.lopezjavier.appfitness.domain.model.GoalSwitchPreview
import io.github.lopezjavier.appfitness.domain.model.NewBlockEntry
import io.github.lopezjavier.appfitness.domain.model.PhaseWeeks
import io.github.lopezjavier.appfitness.domain.repository.CoachReviewRepository
import kotlinx.datetime.LocalDate

/** Used when Supabase is not configured: every call fails. */
class UnconfiguredCoachReviewRepository : CoachReviewRepository {
    override suspend fun openReview(today: LocalDate): Result<BlockReview?> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun keepPhase(reviewId: String): Result<Unit> = Result.failure(AppException(AppError.Unknown))

    override suspend fun acceptPhase(reviewId: String, today: LocalDate): Result<String> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun openNewBlock(today: LocalDate): Result<NewBlockEntry?> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun previewGoalSwitch(today: LocalDate): Result<GoalSwitchPreview> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun weeksInPhase(today: LocalDate): Result<PhaseWeeks?> =
        Result.failure(AppException(AppError.Unknown))
}
