package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.ExerciseSetCount
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.MuscleGroupSets
import io.github.lopezjavier.appfitness.domain.model.SetCountPoint
import io.github.lopezjavier.appfitness.domain.model.TrainingSummary
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Params of `training_summary`, `weekly_set_counts` and `muscle_group_sets`; null is an open bound (sent as null). */
@Serializable
internal data class StatsWindowParams(
    @SerialName("p_from") val from: LocalDate?,
    @SerialName("p_to") val to: LocalDate?,
)

/** Params of `exercise_set_counts`. */
@Serializable
internal data class ExerciseSetCountsParams(
    @SerialName("p_from") val from: LocalDate?,
    @SerialName("p_to") val to: LocalDate?,
    @SerialName("p_muscle_group") val muscleGroup: MuscleGroup?,
)

@Serializable
internal data class TrainingSummaryRowDto(
    val workouts: Long = 0,
    val sets: Long = 0,
    val volume: Double = 0.0,
    @SerialName("duration_s") val durationSeconds: Long = 0,
) {
    fun toDomain() = TrainingSummary(workouts.toInt(), sets.toInt(), volume, durationSeconds)
}

@Serializable
internal data class WeeklySetCountRowDto(@SerialName("week_start") val weekStart: LocalDate, val sets: Long = 0) {
    fun toDomain() = SetCountPoint(weekStart, sets.toInt())
}

@Serializable
internal data class MuscleGroupSetsRowDto(
    @SerialName("muscle_group") val muscleGroup: MuscleGroup,
    val sets: Double = 0.0,
) {
    fun toDomain() = MuscleGroupSets(muscleGroup, sets)
}

@Serializable
internal data class ExerciseSetCountRowDto(
    @SerialName("exercise_id") val exerciseId: String,
    val sets: Double = 0.0,
    val sessions: Long = 0,
) {
    fun toDomain() = ExerciseSetCount(exerciseId, sets, sessions.toInt())
}
