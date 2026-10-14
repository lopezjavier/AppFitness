package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.Sex
import kotlin.math.abs

/** How strong a deficit or surplus is, by its absolute adjustment (planning/04 §1.4). */
enum class AdjustmentIntensity { Light, Moderate, Aggressive }

/** [targetKcal] is already floored; [isFloored] is true only when the raw value fell below the safety floor. */
data class CalorieTarget(
    val targetKcal: Int,
    val adjustment: Int,
    val isFloored: Boolean,
    val intensity: AdjustmentIntensity?,
)

/** Ranges, defaults and floors of the energy strategy (planning/04 §1.4, ADR-25). */
object EnergyStrategyRules {
    const val ADJUSTMENT_STEP = 50
    private const val FEMALE_FLOOR_KCAL = 1200
    private const val MALE_FLOOR_KCAL = 1500
    private const val DEFICIT_DEFAULT = -400
    private const val SURPLUS_DEFAULT = 250
    private const val DEFICIT_MIN = -1000
    private const val SURPLUS_MAX = 750
    private const val MAINTENANCE_LIMIT = 100
    internal const val LIGHT_LIMIT = 300
    internal const val MODERATE_LIMIT = 600

    fun defaultAdjustment(strategy: GoalStrategy): Int = when (strategy) {
        GoalStrategy.Deficit -> DEFICIT_DEFAULT
        GoalStrategy.Maintenance -> 0
        GoalStrategy.Surplus -> SURPLUS_DEFAULT
    }

    fun adjustmentRange(strategy: GoalStrategy): IntRange = when (strategy) {
        GoalStrategy.Deficit -> DEFICIT_MIN..-ADJUSTMENT_STEP
        GoalStrategy.Maintenance -> -MAINTENANCE_LIMIT..MAINTENANCE_LIMIT
        GoalStrategy.Surplus -> ADJUSTMENT_STEP..SURPLUS_MAX
    }

    fun floorKcal(sex: Sex): Int = if (sex == Sex.Female) FEMALE_FLOOR_KCAL else MALE_FLOOR_KCAL

    fun clampAdjustment(strategy: GoalStrategy, value: Int): Int = value.coerceIn(adjustmentRange(strategy))

    fun stepDown(strategy: GoalStrategy, adjustment: Int): Int = clampAdjustment(strategy, adjustment - ADJUSTMENT_STEP)

    fun stepUp(strategy: GoalStrategy, adjustment: Int): Int = clampAdjustment(strategy, adjustment + ADJUSTMENT_STEP)
}

/** Daily calorie target from the TDEE, the strategy adjustment and the per-sex safety floor. */
object CalculateCalorieTarget {
    operator fun invoke(tdee: Int, sex: Sex, strategy: GoalStrategy, adjustment: Int): CalorieTarget {
        val raw = tdee + adjustment
        val floor = EnergyStrategyRules.floorKcal(sex)
        return CalorieTarget(
            targetKcal = maxOf(raw, floor),
            adjustment = adjustment,
            isFloored = raw < floor,
            intensity = intensityOf(strategy, adjustment),
        )
    }

    private fun intensityOf(strategy: GoalStrategy, adjustment: Int): AdjustmentIntensity? = when {
        strategy == GoalStrategy.Maintenance -> null
        abs(adjustment) <= EnergyStrategyRules.LIGHT_LIMIT -> AdjustmentIntensity.Light
        abs(adjustment) <= EnergyStrategyRules.MODERATE_LIMIT -> AdjustmentIntensity.Moderate
        else -> AdjustmentIntensity.Aggressive
    }
}
