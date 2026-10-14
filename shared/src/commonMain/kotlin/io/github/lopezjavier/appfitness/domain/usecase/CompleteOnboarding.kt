package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.repository.NutritionGoalsRepository
import io.github.lopezjavier.appfitness.domain.repository.ProfileRepository

/**
 * Saves the goals and then flips `onboarding_completed`. The order guarantees the flag is never true without
 * goals; a failure after the upsert leaves the flag false and a retry upserts again (idempotent).
 */
class CompleteOnboarding(
    private val goalsRepository: NutritionGoalsRepository,
    private val profileRepository: ProfileRepository,
) {
    suspend operator fun invoke(goals: NutritionGoals): Result<Unit> {
        goalsRepository.upsert(goals).onFailure { return Result.failure(it) }
        return profileRepository.markOnboardingCompleted()
    }
}
