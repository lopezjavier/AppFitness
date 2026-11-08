package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.GoalSwitchPreview
import io.github.lopezjavier.appfitness.domain.repository.CoachReviewRepository
import kotlinx.datetime.LocalDate

/** Builds the proposed plan of the saved goal (deterministic, no model); nothing else changes until the save. */
class PreviewGoalSwitchUseCase(private val repository: CoachReviewRepository) {
    suspend operator fun invoke(today: LocalDate): Result<GoalSwitchPreview> = repository.previewGoalSwitch(today)
}
