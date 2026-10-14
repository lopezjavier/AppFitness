package io.github.lopezjavier.appfitness.domain.model

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** Biological sex used by the BMR formula (planning/04-business-rules.md §1.1). */
enum class Sex { Male, Female }

/** Activity level, one per TDEE factor (planning/04-business-rules.md §1.3). */
enum class ActivityLevel { Sedentary, Light, Moderate, High, VeryHigh }

/** The four biometric profile fields, all chosen: what onboarding writes. */
data class Biometrics(val sex: Sex, val birthDate: LocalDate, val heightCm: Int, val activityLevel: ActivityLevel)

/** What the profile holds so far; any field can still be missing (interrupted onboarding). */
data class SavedBiometrics(
    val sex: Sex? = null,
    val birthDate: LocalDate? = null,
    val heightCm: Int? = null,
    val activityLevel: ActivityLevel? = null,
)

/** Where a body measurement came from. */
enum class MeasurementSource { Manual, HealthConnect }

/** One weight entry of `body_measurements`. */
data class BodyMeasurement(val id: String, val measuredAt: Instant, val weightKg: Double, val source: MeasurementSource)
