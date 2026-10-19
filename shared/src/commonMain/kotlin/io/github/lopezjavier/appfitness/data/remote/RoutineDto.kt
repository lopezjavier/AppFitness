package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.Routine
import io.github.lopezjavier.appfitness.domain.model.RoutineExercise
import io.github.lopezjavier.appfitness.domain.model.RoutineSummary
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `routines` row with PostgREST's embedded count of its exercises (`routine_exercises(count)`). */
@Serializable
internal data class RoutineSummaryDto(
    val id: String,
    val name: String,
    @SerialName("routine_exercises") val exercises: List<CountDto> = emptyList(),
) {
    fun toDomain() = RoutineSummary(id, name, exercises.firstOrNull()?.count ?: 0)

    companion object {
        const val COLUMNS = "id,name,routine_exercises(count)"
    }
}

@Serializable
internal data class CountDto(val count: Int)

/** `routines` row with its `routine_exercises`; the E6 columns are not read. */
@Serializable
internal data class RoutineDto(
    val id: String,
    val name: String,
    val notes: String? = null,
    val archived: Boolean = false,
    @SerialName("routine_exercises") val exercises: List<RoutineExerciseDto> = emptyList(),
) {
    fun toDomain() = Routine(id, name, notes, archived, exercises.sortedBy { it.position }.map { it.toDomain() })

    companion object {
        const val COLUMNS =
            "id,name,notes,archived," +
                "routine_exercises(exercise_id,position,superset_group,target_sets,target_reps,rest_seconds)"
    }
}

@Serializable
internal data class RoutineExerciseDto(
    @SerialName("exercise_id") val exerciseId: String,
    val position: Int,
    @SerialName("superset_group") val supersetGroup: Int? = null,
    @SerialName("target_sets") val targetSets: Int,
    @SerialName("target_reps") val targetReps: Int,
    @SerialName("rest_seconds") val restSeconds: Int,
) {
    fun toDomain() = RoutineExercise(exerciseId, position, supersetGroup, targetSets, targetReps, restSeconds)
}

/**
 * Argument `p_routine` of the `save_routine` RPC. The E6 columns are left out on purpose, so their defaults apply.
 * A null [id] is omitted (insert); null group and notes are omitted too (they default to null in SQL).
 */
@Serializable
internal data class SaveRoutineRequest(
    val id: String? = null,
    val name: String,
    val notes: String? = null,
    val exercises: List<SaveRoutineExerciseRequest>,
) {
    companion object {
        fun from(routine: Routine) = SaveRoutineRequest(
            id = routine.id,
            name = routine.name.trim(),
            notes = routine.notes,
            exercises = routine.exercises.mapIndexed { index, exercise ->
                SaveRoutineExerciseRequest(
                    exerciseId = exercise.exerciseId,
                    position = index,
                    supersetGroup = exercise.supersetGroup,
                    targetSets = exercise.targetSets,
                    targetReps = exercise.targetReps,
                    restSeconds = exercise.restSeconds,
                )
            },
        )
    }
}

@Serializable
internal data class SaveRoutineExerciseRequest(
    @SerialName("exercise_id") val exerciseId: String,
    val position: Int,
    @SerialName("superset_group") val supersetGroup: Int? = null,
    @SerialName("target_sets") val targetSets: Int,
    @SerialName("target_reps") val targetReps: Int,
    @SerialName("rest_seconds") val restSeconds: Int,
)

/** Arguments of the `save_routine` RPC. */
@Serializable
internal data class SaveRoutineParams(@SerialName("p_routine") val routine: SaveRoutineRequest)

/** Body of the `PATCH /routines` that archives a routine. */
@Serializable
internal data class ArchiveRoutineUpdateDto(val archived: Boolean)
