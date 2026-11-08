package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.NewBlockEntry
import io.github.lopezjavier.appfitness.domain.repository.CoachReviewRepository
import kotlinx.datetime.LocalDate

/**
 * Opens "Bloque nuevo": the review so far and whether the goal changed; null without a saved block. It writes
 * nothing and never previews the goal switch by itself: the caller decides when to call [PreviewGoalSwitchUseCase].
 */
class OpenNewBlockUseCase(private val repository: CoachReviewRepository) {
    suspend operator fun invoke(today: LocalDate): Result<NewBlockEntry?> = repository.openNewBlock(today)
}
