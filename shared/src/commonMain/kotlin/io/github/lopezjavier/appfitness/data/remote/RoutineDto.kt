package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.PlannedSet
import io.github.lopezjavier.appfitness.domain.model.Routine
import io.github.lopezjavier.appfitness.domain.model.RoutineBounds
import io.github.lopezjavier.appfitness.domain.model.RoutineExercise
import io.github.lopezjavier.appfitness.domain.model.RoutineFolder
import io.github.lopezjavier.appfitness.domain.model.RoutineSummary
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.usecase.plannedSetsFromLegacy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `routines` row with PostgREST's embedded count of its exercises (`routine_exercises(count)`). */
@Serializable
internal data class RoutineSummaryDto(
    val id: String,
    val name: String,
    @SerialName("folder_id") val folderId: String? = null,
    @SerialName("is_next") val isNext: Boolean = false,
    @SerialName("routine_exercises") val exercises: List<CountDto> = emptyList(),
) {
    fun toDomain() = RoutineSummary(id, name, exercises.firstOrNull()?.count ?: 0, folderId, isNext)

    companion object {
        const val COLUMNS = "id,name,folder_id,is_next,routine_exercises(count)"
    }
}

/** `routine_folders` row. */
@Serializable
internal data class RoutineFolderDto(val id: String, val name: String) {
    fun toDomain() = RoutineFolder(id, name)

    companion object {
        const val COLUMNS = "id,name"
    }
}

@Serializable
internal data class CountDto(val count: Int)

/** `routines` row with its `routine_exercises` and their planned sets. */
@Serializable
internal data class RoutineDto(
    val id: String,
    val name: String,
    val notes: String? = null,
    val archived: Boolean = false,
    @SerialName("track_as_routine") val trackAsRoutine: Boolean = true,
    @SerialName("routine_exercises") val exercises: List<RoutineExerciseDto> = emptyList(),
) {
    fun toDomain() = Routine(
        id,
        name,
        notes,
        archived,
        exercises.sortedBy { it.position }.map { it.toDomain() },
        trackAsRoutine,
    )

    companion object {
        const val COLUMNS =
            "id,name,notes,archived,track_as_routine," +
                "routine_exercises(exercise_id,position,superset_group,target_sets,target_reps," +
                "target_weight_kg,planned_set_type,target_rpe,rest_seconds," +
                "routine_exercise_sets(target_weight_kg,target_reps,set_type,set_index))"
    }
}

/** A row of `routine_exercise_sets` as read. */
@Serializable
internal data class PlannedSetRowDto(
    @SerialName("target_weight_kg") val weight: Double? = null,
    @SerialName("target_reps") val reps: Int,
    @SerialName("set_type") val setType: SetType = SetType.Working,
    @SerialName("set_index") val setIndex: Int = 0,
)

/**
 * `routine_exercises` row. Exercises that still have no child rows (legacy) are mapped from the summary columns:
 * `target_sets` identical planned sets.
 */
@Serializable
internal data class RoutineExerciseDto(
    @SerialName("exercise_id") val exerciseId: String,
    val position: Int,
    @SerialName("superset_group") val supersetGroup: Int? = null,
    @SerialName("target_sets") val targetSets: Int = 1,
    @SerialName("target_reps") val targetReps: Int = RoutineBounds.DEFAULT_REPS,
    @SerialName("target_weight_kg") val targetWeightKg: Double? = null,
    @SerialName("planned_set_type") val plannedSetType: SetType = SetType.Working,
    @SerialName("target_rpe") val targetRpe: Double? = null,
    @SerialName("rest_seconds") val restSeconds: Int,
    @SerialName("routine_exercise_sets") val sets: List<PlannedSetRowDto> = emptyList(),
) {
    fun toDomain() = RoutineExercise(
        exerciseId = exerciseId,
        position = position,
        supersetGroup = supersetGroup,
        plannedSets = if (sets.isEmpty()) {
            plannedSetsFromLegacy(targetSets, targetReps, targetWeightKg, plannedSetType)
        } else {
            sets.sortedBy { it.setIndex }.map { PlannedSet(it.weight, it.reps, it.setType) }
        },
        restSeconds = restSeconds,
        targetRpe = targetRpe,
    )
}

/**
 * Argument `p_routine` of the `save_routine` RPC. A null [id] is omitted (insert); null group, notes, rpe and set
 * weights are omitted too (they default to null in SQL).
 */
@Serializable
internal data class SaveRoutineRequest(
    val id: String? = null,
    val name: String,
    val notes: String? = null,
    @SerialName("track_as_routine") val trackAsRoutine: Boolean,
    val exercises: List<SaveRoutineExerciseRequest>,
) {
    companion object {
        fun from(routine: Routine) = SaveRoutineRequest(
            id = routine.id,
            name = routine.name.trim(),
            notes = routine.notes,
            trackAsRoutine = routine.trackAsRoutine,
            exercises = routine.exercises.mapIndexed { index, exercise ->
                SaveRoutineExerciseRequest(
                    exerciseId = exercise.exerciseId,
                    position = index,
                    supersetGroup = exercise.supersetGroup,
                    restSeconds = exercise.restSeconds,
                    targetRpe = exercise.targetRpe,
                    sets = exercise.plannedSets.map { PlannedSetDto(it.weightKg, it.reps, it.type) },
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
    @SerialName("rest_seconds") val restSeconds: Int,
    @SerialName("target_rpe") val targetRpe: Double? = null,
    val sets: List<PlannedSetDto>,
)

/** One planned set of the `save_routine` payload. */
@Serializable
internal data class PlannedSetDto(
    @SerialName("weight_kg") val weightKg: Double? = null,
    val reps: Int,
    @SerialName("set_type") val setType: SetType,
)

/** Arguments of the `save_routine` RPC. */
@Serializable
internal data class SaveRoutineParams(@SerialName("p_routine") val routine: SaveRoutineRequest)

// Arguments of the folder/order RPCs. Nullable ids have NO default so that null is always sent explicitly
// (PostgREST resolves the function by its named arguments; an omitted one would not match).

@Serializable
internal data class CreateRoutineFolderParams(@SerialName("p_name") val name: String)

@Serializable
internal data class RenameRoutineFolderParams(
    @SerialName("p_id") val id: String,
    @SerialName("p_name") val name: String,
)

@Serializable
internal data class DeleteRoutineFolderParams(@SerialName("p_id") val id: String)

@Serializable
internal data class MoveRoutineToFolderParams(
    @SerialName("p_routine_id") val routineId: String,
    @SerialName("p_folder_id") val folderId: String?,
)

@Serializable
internal data class SaveRoutineOrderParams(
    @SerialName("p_folder_id") val folderId: String?,
    @SerialName("p_routine_ids") val routineIds: List<String>,
)

@Serializable
internal data class SetNextRoutineParams(@SerialName("p_routine_id") val routineId: String?)
