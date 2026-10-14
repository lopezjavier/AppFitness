package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.BiometricBounds
import io.github.lopezjavier.appfitness.domain.model.Sex
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt

/** Total daily energy expenditure: Mifflin-St Jeor BMR times the activity factor (planning/04 §1.2, §1.3). */
object CalculateTdee {
    private const val WEIGHT_COEFFICIENT = 10.0
    private const val HEIGHT_COEFFICIENT = 6.25
    private const val AGE_COEFFICIENT = 5.0
    private const val MALE_OFFSET = 5.0
    private const val FEMALE_OFFSET = -161.0

    private val factors: Map<ActivityLevel, Double> = mapOf(
        ActivityLevel.Sedentary to 1.2,
        ActivityLevel.Light to 1.375,
        ActivityLevel.Moderate to 1.55,
        ActivityLevel.High to 1.725,
        ActivityLevel.VeryHigh to 1.9,
    )

    fun factor(level: ActivityLevel): Double = factors.getValue(level)

    @Suppress("LongParameterList") // The five biometric inputs plus the reference date.
    operator fun invoke(
        sex: Sex,
        birthDate: LocalDate,
        heightCm: Int,
        weightKg: Double,
        activityLevel: ActivityLevel,
        today: LocalDate,
    ): Int {
        val age = BiometricBounds.ageOn(birthDate, today)
        val offset = if (sex == Sex.Male) MALE_OFFSET else FEMALE_OFFSET
        val bmr = WEIGHT_COEFFICIENT * weightKg + HEIGHT_COEFFICIENT * heightCm - AGE_COEFFICIENT * age + offset
        return (bmr * factor(activityLevel)).roundToInt()
    }
}
