package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.repository.CoachReviewRepository

/** Marks a stored block review as seen; the caller then refreshes the reminders so the notice clears. */
class MarkBlockReviewSeen(private val repository: CoachReviewRepository) {
    suspend operator fun invoke(reviewId: String): Result<Unit> = repository.markSeen(reviewId)
}
