package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.Biometrics
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.repository.ProfileRepository
import kotlinx.datetime.LocalDate

/**
 * Recalculates the TDEE and the daily target (floored by the new sex, ADR-25) from the edited biometrics and
 * stores biometrics and goals in one atomic call.
 */
class SaveProfileSettings(private val profileRepository: ProfileRepository) {
    @Suppress("LongParameterList") // The edited biometrics, the latest weight and the three goal choices.
    suspend operator fun invoke(
        biometrics: Biometrics,
        weightKg: Double,
        today: LocalDate,
        strategy: GoalStrategy,
        adjustment: Int,
        split: MacroSplit,
    ): Result<Unit> {
        val tdee = CalculateTdee(
            biometrics.sex,
            biometrics.birthDate,
            biometrics.heightCm,
            weightKg,
            biometrics.activityLevel,
            today,
        )
        val target = CalculateCalorieTarget(tdee, biometrics.sex, strategy, adjustment)
        val goals = NutritionGoals(strategy, adjustment, target.targetKcal, split)
        return profileRepository.saveSettings(biometrics, goals)
    }
}
