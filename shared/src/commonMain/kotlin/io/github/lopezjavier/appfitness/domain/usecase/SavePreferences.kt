package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.BiometricBounds
import io.github.lopezjavier.appfitness.domain.model.Biometrics
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.model.PreviousValuesMode
import io.github.lopezjavier.appfitness.domain.model.WeightEntry
import io.github.lopezjavier.appfitness.domain.repository.ProfileRepository
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt
import kotlin.time.Instant

/**
 * Validates the edited preferences, recalculates the TDEE with the edited weight and the daily target (floored by
 * the new sex, ADR-25) and stores name, biometrics, goals and (only when [weightChanged]) today's manual weight in
 * one atomic call. "Today" is the local day of [now] in [timeZone].
 */
class SavePreferences(private val profileRepository: ProfileRepository) {
    @Suppress("LongParameterList") // The edited form fields, the clock inputs and the three goal choices.
    suspend operator fun invoke(
        displayName: String,
        biometrics: Biometrics,
        weightKg: Double,
        weightChanged: Boolean,
        now: Instant,
        timeZone: TimeZone,
        strategy: GoalStrategy,
        adjustment: Int,
        split: MacroSplit,
        warmUpIncluded: Boolean = false,
        previousValuesMode: PreviousValuesMode = PreviousValuesMode.Overall,
    ): Result<Unit> {
        val name = displayName.trim()
        val tenths = (weightKg * TENTHS_PER_KG).roundToInt()
        val nameValid = name.isNotEmpty() && name.length <= BiometricBounds.NAME_MAX_LENGTH
        if (!nameValid || tenths !in BiometricBounds.WEIGHT_TENTHS) {
            return Result.failure(AppException(AppError.Validation))
        }
        val today = now.toLocalDateTime(timeZone).date
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
        val weight = if (weightChanged) {
            WeightEntry(
                weightKg = weightKg,
                measuredAt = now,
                dayStart = today.atStartOfDayIn(timeZone),
                dayEnd = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone),
            )
        } else {
            null
        }
        return profileRepository.savePreferences(name, biometrics, goals, weight, warmUpIncluded, previousValuesMode)
    }

    private companion object {
        const val TENTHS_PER_KG = 10
    }
}
