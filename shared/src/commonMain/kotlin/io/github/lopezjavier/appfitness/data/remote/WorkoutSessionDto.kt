package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.ExerciseHistoryPoint
import io.github.lopezjavier.appfitness.domain.model.PreviousSet
import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import io.github.lopezjavier.appfitness.domain.model.SessionStatus
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.WorkoutSession
import io.github.lopezjavier.appfitness.domain.model.WorkoutSet
import kotlinx.datetime.LocalDate
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** `workout_sets` row as read with its session; `numeric` decodes as Double. */
@Serializable
internal data class WorkoutSetDto(
    @SerialName("exercise_id") val exerciseId: String,
    @SerialName("exercise_position") val exercisePosition: Int,
    @SerialName("superset_group") val supersetGroup: Int? = null,
    @SerialName("set_index") val setIndex: Int,
    @SerialName("set_type") val setType: SetType = SetType.Working,
    @SerialName("weight_kg") val weightKg: Double = 0.0,
    val reps: Int = 0,
    @SerialName("completed_at") val completedAt: String? = null,
) {
    fun toDomain() = WorkoutSet(
        setIndex = setIndex,
        setType = setType,
        entry = SetEntry.WeightReps(weightKg, reps),
        completedAt = completedAt?.let(Instant::parse),
    )

    companion object {
        const val COLUMNS =
            "exercise_id,exercise_position,superset_group,set_index,set_type,weight_kg,reps,completed_at"
    }
}

/** `workout_sessions` row with its embedded `workout_sets`. */
@Serializable
internal data class WorkoutSessionDto(
    val id: String,
    @SerialName("routine_id") val routineId: String? = null,
    val name: String,
    val status: SessionStatus,
    @SerialName("started_at") val startedAt: String,
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("workout_sets") val sets: List<WorkoutSetDto> = emptyList(),
) {
    fun toDomain() = WorkoutSession(
        id = id,
        routineId = routineId,
        name = name,
        status = status,
        startedAt = Instant.parse(startedAt),
        endedAt = endedAt?.let(Instant::parse),
        exercises = sets.groupBy { it.exercisePosition }.toSortedMap().map { (position, rows) ->
            val ordered = rows.sortedBy { it.setIndex }
            SessionExercise(
                exerciseId = ordered.first().exerciseId,
                position = position,
                supersetGroup = ordered.first().supersetGroup,
                sets = ordered.map { it.toDomain() },
            )
        },
    )

    companion object {
        const val COLUMNS = "id,routine_id,name,status,started_at,ended_at,workout_sets(${WorkoutSetDto.COLUMNS})"
    }
}

/** Body of the set upsert (`on_conflict=session_id,exercise_position,set_index`). */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class WorkoutSetUpsertDto(
    @SerialName("session_id") val sessionId: String,
    @SerialName("exercise_id") val exerciseId: String,
    @SerialName("exercise_position") val exercisePosition: Int,
    // Always sent, even when null, so a re-upsert clears a previous group or completion.
    @EncodeDefault @SerialName("superset_group") val supersetGroup: Int? = null,
    @SerialName("set_index") val setIndex: Int,
    @SerialName("set_type") val setType: SetType,
    @SerialName("weight_kg") val weightKg: Double,
    val reps: Int,
    @EncodeDefault @SerialName("completed_at") val completedAt: String? = null,
)

@Serializable
internal data class StartSessionParams(@SerialName("p_routine_id") val routineId: String? = null)

@Serializable
internal data class SessionIdParams(@SerialName("p_session_id") val sessionId: String)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class ExerciseHistoryParams(
    @SerialName("p_exercise_id") val exerciseId: String,
    @EncodeDefault @SerialName("p_from") val from: LocalDate? = null,
)

@Serializable
internal data class LastPerformanceParams(@SerialName("p_exercise_id") val exerciseId: String)

/** Row returned by the `last_performance` RPC. */
@Serializable
internal data class PreviousSetDto(
    @SerialName("set_index") val setIndex: Int,
    @SerialName("weight_kg") val weightKg: Double = 0.0,
    val reps: Int = 0,
) {
    fun toDomain() = PreviousSet(setIndex, weightKg, reps)
}

/** Row returned by the `exercise_history` RPC. */
@Serializable
internal data class ExerciseHistoryDto(
    @SerialName("session_id") val sessionId: String,
    @SerialName("session_date") val sessionDate: LocalDate,
    @SerialName("best_one_rm") val bestOneRm: Double? = null,
    @SerialName("total_volume") val totalVolume: Double = 0.0,
) {
    fun toDomain() = ExerciseHistoryPoint(sessionId, sessionDate, bestOneRm, totalVolume)
}
