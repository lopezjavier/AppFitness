package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import kotlin.math.abs
import kotlin.math.floor

/** Where the current weight stands against the target; kg values are the absolute 1-decimal distance. */
sealed interface TargetDistanceResult {
    data class Remaining(val kg: Double) : TargetDistanceResult

    data object Reached : TargetDistanceResult

    /** Maintenance only. */
    data class Above(val kg: Double) : TargetDistanceResult

    /** Maintenance only. */
    data class Below(val kg: Double) : TargetDistanceResult
}

/** Distance to the target weight and its wording by strategy (planning/04-business-rules.md §3.5). */
class TargetDistance {
    operator fun invoke(currentKg: Double, targetKg: Double, strategy: GoalStrategy): TargetDistanceResult {
        val tenths = floor((currentKg - targetKg) * TENTHS + HALF).toInt()
        val kg = abs(tenths) / TENTHS
        return when (strategy) {
            GoalStrategy.Deficit -> if (tenths > 0) TargetDistanceResult.Remaining(kg) else TargetDistanceResult.Reached

            GoalStrategy.Surplus -> if (tenths < 0) TargetDistanceResult.Remaining(kg) else TargetDistanceResult.Reached

            GoalStrategy.Maintenance -> when {
                tenths > 0 -> TargetDistanceResult.Above(kg)
                tenths < 0 -> TargetDistanceResult.Below(kg)
                else -> TargetDistanceResult.Reached
            }
        }
    }

    private companion object {
        const val TENTHS = 10.0
        const val HALF = 0.5
    }
}
