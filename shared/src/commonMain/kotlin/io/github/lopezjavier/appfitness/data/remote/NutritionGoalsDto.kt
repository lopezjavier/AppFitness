package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** DB values of `public.goal_strategy`. */
@Serializable
internal enum class GoalStrategyDto(val domain: GoalStrategy) {
    @SerialName("deficit")
    Deficit(GoalStrategy.Deficit),

    @SerialName("maintenance")
    Maintenance(GoalStrategy.Maintenance),

    @SerialName("surplus")
    Surplus(GoalStrategy.Surplus),
}

/** Row of `public.nutrition_goals` as read. */
@Serializable
internal data class NutritionGoalsDto(
    val strategy: GoalStrategyDto,
    @SerialName("calorie_adjustment") val calorieAdjustment: Int,
    @SerialName("target_kcal") val targetKcal: Int,
    @SerialName("protein_pct") val proteinPct: Int,
    @SerialName("carbs_pct") val carbsPct: Int,
    @SerialName("fat_pct") val fatPct: Int,
) {
    fun toDomain(): NutritionGoals = NutritionGoals(
        strategy = strategy.domain,
        calorieAdjustment = calorieAdjustment,
        targetKcal = targetKcal,
        split = MacroSplit(proteinPct, carbsPct, fatPct),
    )

    companion object {
        const val COLUMNS = "strategy,calorie_adjustment,target_kcal,protein_pct,carbs_pct,fat_pct"
    }
}

/** Body of the `POST /nutrition_goals?on_conflict=user_id` upsert. */
@Serializable
internal data class NutritionGoalsUpsertDto(
    @SerialName("user_id") val userId: String,
    val strategy: GoalStrategyDto,
    @SerialName("calorie_adjustment") val calorieAdjustment: Int,
    @SerialName("target_kcal") val targetKcal: Int,
    @SerialName("protein_pct") val proteinPct: Int,
    @SerialName("carbs_pct") val carbsPct: Int,
    @SerialName("fat_pct") val fatPct: Int,
) {
    companion object {
        fun from(userId: String, goals: NutritionGoals) = NutritionGoalsUpsertDto(
            userId = userId,
            strategy = GoalStrategyDto.entries.first { it.domain == goals.strategy },
            calorieAdjustment = goals.calorieAdjustment,
            targetKcal = goals.targetKcal,
            proteinPct = goals.split.proteinPct,
            carbsPct = goals.split.carbsPct,
            fatPct = goals.split.fatPct,
        )
    }
}
