package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.BlockReview
import io.github.lopezjavier.appfitness.domain.repository.CoachReviewRepository
import kotlinx.datetime.LocalDate

/** Opens the end-of-block review; the result is null until the day after the block's last day. */
class LoadBlockReviewUseCase(private val repository: CoachReviewRepository) {
    suspend operator fun invoke(today: LocalDate): Result<BlockReview?> = repository.openReview(today)
}
