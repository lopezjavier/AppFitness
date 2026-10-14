package io.github.lopezjavier.appfitness.feature.goals

import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.Sex
import io.github.lopezjavier.appfitness.domain.usecase.AdjustmentIntensity
import io.github.lopezjavier.appfitness.domain.usecase.CalculateCalorieTarget
import io.github.lopezjavier.appfitness.domain.usecase.CalculateTdee
import io.github.lopezjavier.appfitness.domain.usecase.EnergyStrategyRules
import kotlinx.datetime.LocalDate

/** What the energy strategy section shows; [floorKcal] is the safety floor of the user's sex. */
data class EnergyStrategyUiState(
    val tdeeKcal: Int,
    val strategy: GoalStrategy,
    val adjustment: Int,
    val targetKcal: Int,
    val isFloored: Boolean,
    val floorKcal: Int,
    val intensity: AdjustmentIntensity?,
    val canStepDown: Boolean,
    val canStepUp: Boolean,
    val enabled: Boolean = true,
)

/** Events of the energy strategy section. */
sealed interface EnergyStrategyEvent {
    data class SelectStrategy(val strategy: GoalStrategy) : EnergyStrategyEvent

    data object StepDown : EnergyStrategyEvent

    data object StepUp : EnergyStrategyEvent
}

/**
 * Pure builder: TDEE from the biometrics, then the target for [strategy] and [adjustment]. "-" is disabled once the
 * target is floored (a further step would change the number but not the target); "+" stays available.
 */
@Suppress("LongParameterList") // The biometric inputs of the TDEE plus the two user choices.
fun energyStrategyUiState(
    sex: Sex,
    birthDate: LocalDate,
    heightCm: Int,
    weightKg: Double,
    activityLevel: ActivityLevel,
    today: LocalDate,
    strategy: GoalStrategy,
    adjustment: Int,
    enabled: Boolean = true,
): EnergyStrategyUiState {
    val tdee = CalculateTdee(sex, birthDate, heightCm, weightKg, activityLevel, today)
    val target = CalculateCalorieTarget(tdee, sex, strategy, adjustment)
    val range = EnergyStrategyRules.adjustmentRange(strategy)
    return EnergyStrategyUiState(
        tdeeKcal = tdee,
        strategy = strategy,
        adjustment = adjustment,
        targetKcal = target.targetKcal,
        isFloored = target.isFloored,
        floorKcal = EnergyStrategyRules.floorKcal(sex),
        intensity = target.intensity,
        canStepDown = enabled && adjustment > range.first && !target.isFloored,
        canStepUp = enabled && adjustment < range.last,
        enabled = enabled,
    )
}
