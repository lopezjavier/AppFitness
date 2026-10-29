package io.github.lopezjavier.appfitness.domain.model

import kotlinx.datetime.LocalDate

/** Confirmation state of one derived value; a re-asked value is always provisional too. */
enum class ValueState { Provisional, Confirmed, Edited, Reask }

/** Data the coach lacked when it derived a value. [Unknown] keeps a new server key from crashing the app. */
sealed interface CoachMissing {
    data object Steps : CoachMissing

    data object ActiveKcal : CoachMissing

    data object TrainingHistory : CoachMissing

    data object ProfileData : CoachMissing

    data object Diary : CoachMissing

    data object WeighIns : CoachMissing

    data class Unknown(val raw: String) : CoachMissing

    companion object {
        fun fromWire(raw: String): CoachMissing = when (raw) {
            "steps" -> Steps
            "active_kcal" -> ActiveKcal
            "training_history" -> TrainingHistory
            "profile_data" -> ProfileData
            "diary" -> Diary
            "weigh_ins" -> WeighIns
            else -> Unknown(raw)
        }
    }
}

enum class DerivedSource { Data, Profile, Adaptive, Formula }

enum class DerivedExperience { Beginner, Intermediate, Advanced }

/** Measured inputs behind the activity level estimate. */
data class ActivitySources(
    val stepsAvg: Double?,
    val stepsDays: Int?,
    val activeKcalAvg: Double?,
    val activeKcalDays: Int?,
    val sessionsPerWeek: Double?,
)

data class DerivedActivityLevel(
    val value: ActivityLevel?,
    val estimate: ActivityLevel?,
    val factor: Double?,
    val source: DerivedSource,
    val provisional: Boolean,
    val confirmed: Boolean,
    val edited: Boolean,
    val reask: Boolean,
    val uncertainty: Double?,
    val sources: ActivitySources,
    val missing: List<CoachMissing>,
) {
    val state: ValueState get() = valueState(provisional, confirmed, edited, reask)
}

data class DerivedTotalExpenditure(
    val kcal: Double?,
    val source: DerivedSource,
    val unreliable: Boolean,
    val allowCalorieChanges: Boolean,
    val provisional: Boolean,
    val confirmed: Boolean,
    val reask: Boolean,
    val uncertainty: Double?,
    val missing: List<CoachMissing>,
) {
    val state: ValueState get() = valueState(provisional, confirmed, false, reask)
}

data class DerivedWeightTrend(
    val slopeKgWeek: Double?,
    val intervalLow: Double?,
    val intervalHigh: Double?,
    val weeks: Int?,
    val lowConfidence: Boolean,
    val provisional: Boolean,
    val confirmed: Boolean,
    val reask: Boolean,
    val uncertainty: Double?,
    val missing: List<CoachMissing>,
) {
    val state: ValueState get() = valueState(provisional, confirmed, false, reask)
}

data class DerivedIntake(val loggedDays: Int?, val windowDays: Int?, val ratio: Double?, val reliable: Boolean)

data class DerivedExperienceValue(
    val value: DerivedExperience?,
    val sessions: Int?,
    val weeks: Int?,
    val provisional: Boolean,
    val confirmed: Boolean,
    val reask: Boolean,
    val uncertainty: Double?,
    val missing: List<CoachMissing>,
) {
    val state: ValueState get() = valueState(provisional, confirmed, false, reask)
}

/** The coach's derived values with their sources and confirmation state (US-68, US-69). */
data class CoachDerivedValues(
    val asOf: LocalDate,
    val activityLevel: DerivedActivityLevel,
    val totalExpenditure: DerivedTotalExpenditure,
    val weightTrend: DerivedWeightTrend,
    val intake: DerivedIntake,
    val experience: DerivedExperienceValue,
) {
    /** True when no value is provisional: the plan may use them. */
    val allConfirmed: Boolean
        get() = !activityLevel.provisional && !totalExpenditure.provisional &&
            !weightTrend.provisional && !experience.provisional

    val anyReask: Boolean
        get() = activityLevel.reask || totalExpenditure.reask || weightTrend.reask || experience.reask
}

private fun valueState(provisional: Boolean, confirmed: Boolean, edited: Boolean, reask: Boolean): ValueState = when {
    reask -> ValueState.Reask
    edited -> ValueState.Edited
    provisional -> ValueState.Provisional
    confirmed -> ValueState.Confirmed
    else -> ValueState.Provisional
}
