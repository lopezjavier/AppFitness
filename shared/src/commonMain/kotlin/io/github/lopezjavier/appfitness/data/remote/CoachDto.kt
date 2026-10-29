package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.ActivitySources
import io.github.lopezjavier.appfitness.domain.model.CoachDerivedValues
import io.github.lopezjavier.appfitness.domain.model.CoachMissing
import io.github.lopezjavier.appfitness.domain.model.DerivedActivityLevel
import io.github.lopezjavier.appfitness.domain.model.DerivedExperience
import io.github.lopezjavier.appfitness.domain.model.DerivedExperienceValue
import io.github.lopezjavier.appfitness.domain.model.DerivedIntake
import io.github.lopezjavier.appfitness.domain.model.DerivedSource
import io.github.lopezjavier.appfitness.domain.model.DerivedTotalExpenditure
import io.github.lopezjavier.appfitness.domain.model.DerivedWeightTrend
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Params of `coach_derived_estimates`; [activityLevel] is omitted when null (encodeDefaults is off). */
@Serializable
internal data class CoachDerivedParams(@SerialName("p_today") val today: LocalDate)

/** Params of `confirm_derived_values`; a null [activityLevel] is omitted from the body. */
@Serializable
internal data class ConfirmDerivedParams(
    @SerialName("p_today") val today: LocalDate,
    @SerialName("p_activity_level") val activityLevel: ActivityLevelDto? = null,
) {
    companion object {
        fun of(today: LocalDate, level: ActivityLevel?) =
            ConfirmDerivedParams(today, level?.let { l -> ActivityLevelDto.entries.first { it.domain == l } })
    }
}

@Serializable
internal data class ActivitySourcesDto(
    @SerialName("steps_avg") val stepsAvg: Double? = null,
    @SerialName("steps_days") val stepsDays: Int? = null,
    @SerialName("active_kcal_avg") val activeKcalAvg: Double? = null,
    @SerialName("active_kcal_days") val activeKcalDays: Int? = null,
    @SerialName("sessions_per_week") val sessionsPerWeek: Double? = null,
)

@Serializable
internal data class ActivityLevelValueDto(
    val value: ActivityLevelDto? = null,
    val estimate: ActivityLevelDto? = null,
    val factor: Double? = null,
    val source: String? = null,
    val provisional: Boolean = false,
    val confirmed: Boolean = false,
    val edited: Boolean = false,
    val reask: Boolean = false,
    val uncertainty: Double? = null,
    val sources: ActivitySourcesDto = ActivitySourcesDto(),
    val missing: List<String> = emptyList(),
)

@Serializable
internal data class TotalExpenditureDto(
    val value: Double? = null,
    val source: String? = null,
    val unreliable: Boolean = false,
    @SerialName("allow_calorie_changes") val allowCalorieChanges: Boolean = false,
    val provisional: Boolean = false,
    val confirmed: Boolean = false,
    val reask: Boolean = false,
    val uncertainty: Double? = null,
    val missing: List<String> = emptyList(),
)

@Serializable
internal data class WeightTrendDto(
    @SerialName("slope_kg_week") val slopeKgWeek: Double? = null,
    @SerialName("interval_low") val intervalLow: Double? = null,
    @SerialName("interval_high") val intervalHigh: Double? = null,
    val weeks: Int? = null,
    @SerialName("low_confidence") val lowConfidence: Boolean = false,
    val provisional: Boolean = false,
    val confirmed: Boolean = false,
    val reask: Boolean = false,
    val uncertainty: Double? = null,
    val missing: List<String> = emptyList(),
)

@Serializable
internal data class IntakeDto(
    @SerialName("logged_days") val loggedDays: Int? = null,
    @SerialName("window_days") val windowDays: Int? = null,
    val ratio: Double? = null,
    val reliable: Boolean = false,
)

@Serializable
internal data class ExperienceDto(
    val value: String? = null,
    val sessions: Int? = null,
    val weeks: Int? = null,
    val provisional: Boolean = false,
    val confirmed: Boolean = false,
    val reask: Boolean = false,
    val uncertainty: Double? = null,
    val missing: List<String> = emptyList(),
)

/** Response of `coach_derived_estimates` and `confirm_derived_values` (one jsonb object). */
@Serializable
internal data class CoachDerivedDto(
    @SerialName("as_of") val asOf: LocalDate,
    @SerialName("activity_level") val activityLevel: ActivityLevelValueDto = ActivityLevelValueDto(),
    @SerialName("total_expenditure") val totalExpenditure: TotalExpenditureDto = TotalExpenditureDto(),
    @SerialName("weight_trend") val weightTrend: WeightTrendDto = WeightTrendDto(),
    val intake: IntakeDto = IntakeDto(),
    val experience: ExperienceDto = ExperienceDto(),
) {
    fun toDomain(): CoachDerivedValues = CoachDerivedValues(
        asOf = asOf,
        activityLevel = DerivedActivityLevel(
            value = activityLevel.value?.domain,
            estimate = activityLevel.estimate?.domain,
            factor = activityLevel.factor,
            source = activityLevel.source.toSource(),
            provisional = activityLevel.provisional,
            confirmed = activityLevel.confirmed,
            edited = activityLevel.edited,
            reask = activityLevel.reask,
            uncertainty = activityLevel.uncertainty,
            sources = with(activityLevel.sources) {
                ActivitySources(stepsAvg, stepsDays, activeKcalAvg, activeKcalDays, sessionsPerWeek)
            },
            missing = activityLevel.missing.toMissing(),
        ),
        totalExpenditure = DerivedTotalExpenditure(
            kcal = totalExpenditure.value,
            source = totalExpenditure.source.toSource(),
            unreliable = totalExpenditure.unreliable,
            allowCalorieChanges = totalExpenditure.allowCalorieChanges,
            provisional = totalExpenditure.provisional,
            confirmed = totalExpenditure.confirmed,
            reask = totalExpenditure.reask,
            uncertainty = totalExpenditure.uncertainty,
            missing = totalExpenditure.missing.toMissing(),
        ),
        weightTrend = DerivedWeightTrend(
            slopeKgWeek = weightTrend.slopeKgWeek,
            intervalLow = weightTrend.intervalLow,
            intervalHigh = weightTrend.intervalHigh,
            weeks = weightTrend.weeks,
            lowConfidence = weightTrend.lowConfidence,
            provisional = weightTrend.provisional,
            confirmed = weightTrend.confirmed,
            reask = weightTrend.reask,
            uncertainty = weightTrend.uncertainty,
            missing = weightTrend.missing.toMissing(),
        ),
        intake = DerivedIntake(intake.loggedDays, intake.windowDays, intake.ratio, intake.reliable),
        experience = DerivedExperienceValue(
            value = experience.value.toExperience(),
            sessions = experience.sessions,
            weeks = experience.weeks,
            provisional = experience.provisional,
            confirmed = experience.confirmed,
            reask = experience.reask,
            uncertainty = experience.uncertainty,
            missing = experience.missing.toMissing(),
        ),
    )
}

private fun List<String>.toMissing(): List<CoachMissing> = map(CoachMissing::fromWire)

private fun String?.toSource(): DerivedSource = when (this) {
    "data" -> DerivedSource.Data
    "adaptive" -> DerivedSource.Adaptive
    "formula" -> DerivedSource.Formula
    else -> DerivedSource.Profile
}

private fun String?.toExperience(): DerivedExperience? = when (this) {
    "beginner" -> DerivedExperience.Beginner
    "intermediate" -> DerivedExperience.Intermediate
    "advanced" -> DerivedExperience.Advanced
    else -> null
}
