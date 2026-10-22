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
) {
    /** The four fields as [Biometrics], or null while any is missing (interrupted onboarding). */
    @Suppress("ComplexCondition") // Four independent optional fields.
    fun toBiometricsOrNull(): Biometrics? =
        if (sex != null && birthDate != null && heightCm != null && activityLevel != null) {
            Biometrics(sex, birthDate, heightCm, activityLevel)
        } else {
            null
        }
}

/** Where a body measurement came from. */
enum class MeasurementSource { Manual, HealthConnect }

/** One weight entry of `body_measurements`, with the optional body-fat percentage. */
data class BodyMeasurement(
    val id: String,
    val measuredAt: Instant,
    val weightKg: Double,
    val source: MeasurementSource,
    val bodyFatPct: Double? = null,
    val leanMassKg: Double? = null,
    val waterKg: Double? = null,
    val boneKg: Double? = null,
    val bmrKcal: Int? = null,
    /** Base id of the copy written back to Health Connect (manual rows only); null when there is no copy. */
    val hcRecordId: String? = null,
)

/**
 * A manual weight to store through the `save_body_measurement` RPC. [id] is null for a new save and the row id for an
 * edit; [dayStart] (inclusive) and [dayEnd] (exclusive) are the bounds of the local day, because the server does not
 * know the user's time zone.
 */
data class ManualWeightSave(
    val id: String?,
    val weightKg: Double,
    val bodyFatPct: Double?,
    val measuredAt: Instant,
    val dayStart: Instant,
    val dayEnd: Instant,
)

/** The measurements of one local day: the manual entry (at most one) and the Health Connect ones. */
data class DayMeasurements(val manual: BodyMeasurement?, val healthConnect: List<BodyMeasurement>) {
    /** A Health Connect weight takes precedence over manual entry for the day (ADR-47). */
    val hasHealthConnectWeight: Boolean get() = healthConnect.isNotEmpty()
}

/** A value with the instant it was measured. */
data class Dated<T>(val value: T, val measuredAt: Instant)

/** The most recent value of each body-composition metric, each from its own newest row (null when never measured). */
data class LatestComposition(
    val bodyFat: Dated<Double>? = null,
    val leanMass: Dated<Double>? = null,
    val water: Dated<Double>? = null,
    val bone: Dated<Double>? = null,
    val bmr: Dated<Int>? = null,
) {
    companion object {
        /** Picks, per metric, the value of the newest row that has it. */
        fun from(rows: List<BodyMeasurement>): LatestComposition {
            val newestFirst = rows.sortedByDescending { it.measuredAt }
            fun <T : Any> pick(select: (BodyMeasurement) -> T?): Dated<T>? =
                newestFirst.firstNotNullOfOrNull { row -> select(row)?.let { Dated(it, row.measuredAt) } }
            return LatestComposition(
                bodyFat = pick { it.bodyFatPct },
                leanMass = pick { it.leanMassKg },
                water = pick { it.waterKg },
                bone = pick { it.boneKg },
                bmr = pick { it.bmrKcal },
            )
        }
    }
}
