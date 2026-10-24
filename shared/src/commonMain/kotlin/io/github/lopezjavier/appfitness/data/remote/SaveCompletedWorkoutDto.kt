package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.DraftWorkoutSet
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.WorkoutDraft
import io.github.lopezjavier.appfitness.domain.model.columns
import io.github.lopezjavier.appfitness.domain.usecase.WorkoutStartInstant
import kotlinx.datetime.TimeZone
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

private const val SECONDS_PER_MINUTE = 60

/**
 * Argument `p_workout` of the `save_completed_workout` RPC. Null fields are omitted: an absent [id] creates, a
 * present one updates; [startedAt] is only sent on creation (an edit never moves the date).
 */
@Serializable
internal data class SaveCompletedWorkoutRequest(
    val id: String? = null,
    @SerialName("routine_id") val routineId: String? = null,
    val name: String,
    val description: String? = null,
    val gym: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("duration_s") val durationS: Int,
    val exercises: List<SaveCompletedExerciseRequest>,
) {
    companion object {
        fun from(draft: WorkoutDraft, sessionId: String?, now: Instant, zone: TimeZone) = SaveCompletedWorkoutRequest(
            id = sessionId,
            routineId = draft.routineId.takeIf { sessionId == null },
            name = draft.name.trim(),
            description = draft.description?.trim()?.ifEmpty { null },
            gym = draft.gym?.trim()?.ifEmpty { null },
            startedAt = if (sessionId == null) WorkoutStartInstant.of(draft.date, now, zone).toString() else null,
            durationS = draft.durationMinutes * SECONDS_PER_MINUTE,
            exercises = draft.exercises.map { exercise ->
                SaveCompletedExerciseRequest(
                    exerciseId = exercise.exerciseId,
                    supersetGroup = exercise.supersetGroup,
                    restSeconds = exercise.restSeconds,
                    sets = exercise.sets.map(SaveCompletedSetRequest::from),
                )
            },
        )
    }
}

@Serializable
internal data class SaveCompletedExerciseRequest(
    @SerialName("exercise_id") val exerciseId: String,
    @SerialName("superset_group") val supersetGroup: Int? = null,
    @SerialName("rest_seconds") val restSeconds: Int,
    val sets: List<SaveCompletedSetRequest>,
)

/** One set; only the columns of the exercise's metric type are present, the server stores 0 for weight and reps. */
@Serializable
internal data class SaveCompletedSetRequest(
    @SerialName("set_type") val setType: SetType,
    val rpe: Double? = null,
    @SerialName("weight_kg") val weightKg: Double? = null,
    val reps: Int? = null,
    @SerialName("duration_s") val durationS: Int? = null,
    @SerialName("distance_m") val distanceM: Double? = null,
    val steps: Int? = null,
    val floors: Int? = null,
) {
    companion object {
        fun from(set: DraftWorkoutSet): SaveCompletedSetRequest {
            val columns = set.entry.columns()
            return SaveCompletedSetRequest(
                setType = set.setType,
                rpe = set.rpe,
                weightKg = columns.weightKg,
                reps = columns.reps,
                durationS = columns.durationS,
                distanceM = columns.distanceM,
                steps = columns.steps,
                floors = columns.floors,
            )
        }
    }
}

/** Arguments of the `save_completed_workout` RPC. */
@Serializable
internal data class SaveCompletedWorkoutParams(@SerialName("p_workout") val workout: SaveCompletedWorkoutRequest)

/** Arguments of the `save_workout_as_routine` RPC. */
@Serializable
internal data class SaveWorkoutAsRoutineParams(
    @SerialName("p_session_id") val sessionId: String,
    @SerialName("p_name") val name: String,
)
