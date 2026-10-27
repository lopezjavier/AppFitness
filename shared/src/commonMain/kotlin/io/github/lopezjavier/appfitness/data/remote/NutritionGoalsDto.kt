package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.Biometrics
import io.github.lopezjavier.appfitness.domain.model.CalculatorSettings
import io.github.lopezjavier.appfitness.domain.model.FoodPreferences
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.model.PreviousValuesMode
import io.github.lopezjavier.appfitness.domain.model.WeightEntry
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

/** Arguments of the `save_preferences` RPC: name, biometrics, goals and optionally today's weight, atomically. */
@Serializable
internal data class SavePreferencesParams(
    @SerialName("p_display_name") val displayName: String,
    @SerialName("p_sex") val sex: SexDto,
    @SerialName("p_birth_date") val birthDate: String,
    @SerialName("p_height_cm") val heightCm: Int,
    @SerialName("p_activity_level") val activityLevel: ActivityLevelDto,
    @SerialName("p_strategy") val strategy: GoalStrategyDto,
    @SerialName("p_calorie_adjustment") val calorieAdjustment: Int,
    @SerialName("p_target_kcal") val targetKcal: Int,
    @SerialName("p_protein_pct") val proteinPct: Int,
    @SerialName("p_carbs_pct") val carbsPct: Int,
    @SerialName("p_fat_pct") val fatPct: Int,
    @SerialName("p_weight_kg") val weightKg: Double? = null,
    @SerialName("p_measured_at") val measuredAt: String? = null,
    @SerialName("p_day_start") val dayStart: String? = null,
    @SerialName("p_day_end") val dayEnd: String? = null,
    @SerialName("p_warm_up_included") val warmUpIncluded: Boolean? = null,
    @SerialName("p_previous_values_mode") val previousValuesMode: PreviousValuesModeDto? = null,
    @SerialName("p_bar_weight_kg") val barWeightKg: Double? = null,
    @SerialName("p_plate_kg") val plateKg: List<Double>? = null,
    @SerialName("p_dumbbell_handle_kg") val dumbbellHandleKg: Double? = null,
    @SerialName("p_fat_pct_griddle") val fatPctGriddle: Double? = null,
    @SerialName("p_fat_pct_air_fryer") val fatPctAirFryer: Double? = null,
    @SerialName("p_fat_pct_fried") val fatPctFried: Double? = null,
    @SerialName("p_auto_update_routine") val autoUpdateRoutine: Boolean? = null,
) {
    companion object {
        @Suppress("LongParameterList") // The edited form fields.
        fun from(
            displayName: String,
            biometrics: Biometrics,
            goals: NutritionGoals,
            weight: WeightEntry?,
            warmUpIncluded: Boolean,
            previousValuesMode: PreviousValuesMode,
            calculators: CalculatorSettings? = null,
            foodPreferences: FoodPreferences? = null,
            autoUpdateRoutine: Boolean? = null,
        ): SavePreferencesParams {
            val profile = BiometricsUpdateDto.from(biometrics)
            return SavePreferencesParams(
                displayName = displayName,
                sex = profile.sex,
                birthDate = profile.birthDate,
                heightCm = profile.heightCm,
                activityLevel = profile.activityLevel,
                strategy = GoalStrategyDto.entries.first { it.domain == goals.strategy },
                calorieAdjustment = goals.calorieAdjustment,
                targetKcal = goals.targetKcal,
                proteinPct = goals.split.proteinPct,
                carbsPct = goals.split.carbsPct,
                fatPct = goals.split.fatPct,
                weightKg = weight?.weightKg,
                measuredAt = weight?.measuredAt?.toString(),
                dayStart = weight?.dayStart?.toString(),
                dayEnd = weight?.dayEnd?.toString(),
                warmUpIncluded = warmUpIncluded,
                previousValuesMode = PreviousValuesModeDto.of(previousValuesMode),
                barWeightKg = calculators?.barWeightKg,
                plateKg = calculators?.plateKg,
                dumbbellHandleKg = calculators?.dumbbellHandleKg,
                fatPctGriddle = foodPreferences?.fatPctGriddle,
                fatPctAirFryer = foodPreferences?.fatPctAirFryer,
                fatPctFried = foodPreferences?.fatPctFried,
                autoUpdateRoutine = autoUpdateRoutine,
            )
        }
    }
}

/** Row of `select=target_weight_kg` on `profiles` and body of the `PATCH` that sets it (null clears it). */
@Serializable
internal data class TargetWeightDto(@SerialName("target_weight_kg") val targetWeightKg: Double?) {
    companion object {
        const val COLUMNS = "target_weight_kg"
    }
}

/** Body of the `PATCH /profiles` that saves only the display name. */
@Serializable
internal data class DisplayNameUpdateDto(@SerialName("display_name") val displayName: String)
