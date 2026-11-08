package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.PhaseWeeks
import io.github.lopezjavier.appfitness.domain.repository.CoachReviewRepository
import kotlinx.datetime.LocalDate

/** Weeks started in the current calorie phase (US-111); null without an accepted plan. */
class LoadWeeksInPhaseUseCase(private val repository: CoachReviewRepository) {
    suspend operator fun invoke(today: LocalDate): Result<PhaseWeeks?> = repository.weeksInPhase(today)
}
