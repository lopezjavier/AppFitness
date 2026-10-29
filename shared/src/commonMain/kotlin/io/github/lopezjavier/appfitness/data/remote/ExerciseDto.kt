package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.Exercise
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.MovementPattern
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
    @SerialName("per_hand") val perHand: Boolean = false,
    @SerialName("movement_pattern") val movementPattern: MovementPattern? = null,
) {
    fun toDomain(): Exercise = Exercise(
        id, key, name, muscleGroup, secondaryMuscles, equipment, metricType, isCustom, perHand, movementPattern,
    )

    companion object {
        const val COLUMNS =
            "id,key,name,muscle_group,secondary_muscles,equipment,metric_type,is_custom,per_hand,movement_pattern"
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
    @SerialName("per_hand") val perHand: Boolean,
    // Omitted when null (encodeDefaults is off): the column stays null.
    @SerialName("movement_pattern") val movementPattern: MovementPattern? = null,
)

/** Params of the `update_custom_exercise` RPC. */
@Serializable
internal data class UpdateCustomExerciseParams(
    @SerialName("p_id") val id: String,
    @SerialName("p_name") val name: String,
    @SerialName("p_muscle_group") val muscleGroup: MuscleGroup,
    @SerialName("p_equipment") val equipment: Equipment,
    @SerialName("p_per_hand") val perHand: Boolean,
    // No default, so it is always encoded: null clears the pattern.
    @SerialName("p_movement_pattern") val movementPattern: MovementPattern?,
)

/** Params of the `delete_custom_exercise` RPC. */
@Serializable
internal data class DeleteCustomExerciseParams(@SerialName("p_id") val id: String)
