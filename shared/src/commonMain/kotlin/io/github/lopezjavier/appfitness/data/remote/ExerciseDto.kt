package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.Exercise
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Row of `public.exercises` limited to the columns the app reads. */
@Serializable
internal data class ExerciseDto(
    val id: String,
    val key: String? = null,
    val name: String? = null,
    @SerialName("muscle_group") val muscleGroup: MuscleGroup,
    @SerialName("secondary_muscles") val secondaryMuscles: List<MuscleGroup> = emptyList(),
    val equipment: Equipment,
    @SerialName("metric_type") val metricType: ExerciseMetricType = ExerciseMetricType.WeightReps,
    @SerialName("is_custom") val isCustom: Boolean = false,
) {
    fun toDomain(): Exercise = Exercise(id, key, name, muscleGroup, secondaryMuscles, equipment, metricType, isCustom)

    companion object {
        const val COLUMNS = "id,key,name,muscle_group,secondary_muscles,equipment,metric_type,is_custom"
    }
}

/** Body of `POST /exercises`: `created_by` takes its default (the caller); catalog rows can never be written. */
@Serializable
internal data class ExerciseInsertDto(
    val name: String,
    @SerialName("muscle_group") val muscleGroup: MuscleGroup,
    val equipment: Equipment,
    @SerialName("metric_type") val metricType: ExerciseMetricType,
    @SerialName("is_custom") val isCustom: Boolean,
)
