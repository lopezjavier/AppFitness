package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.ExerciseHistoryPoint
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.ExerciseSessionRecords
import io.github.lopezjavier.appfitness.domain.model.PersonalRecord
import io.github.lopezjavier.appfitness.domain.model.PersonalRecordType
import io.github.lopezjavier.appfitness.domain.model.PreviousSet
import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import io.github.lopezjavier.appfitness.domain.model.SessionStatus
import io.github.lopezjavier.appfitness.domain.model.SetColumns
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.WorkoutSession
import io.github.lopezjavier.appfitness.domain.model.WorkoutSet
import kotlinx.datetime.LocalDate
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** The `exercises(metric_type)` embed of a `workout_sets` row. */
@Serializable
internal data class ExerciseMetricDto(@SerialName("metric_type") val metricType: ExerciseMetricType)

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
    @SerialName("duration_s") val durationS: Int? = null,
    @SerialName("distance_m") val distanceM: Double? = null,
    val steps: Int? = null,
    val floors: Int? = null,
    val rpe: Double? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    // Embedded `exercises(metric_type)`; absent means the default weight and reps.
    val exercises: ExerciseMetricDto? = null,
) {
    fun toDomain() = WorkoutSet(
        setIndex = setIndex,
        setType = setType,
        entry = SetColumns(weightKg, reps, durationS, distanceM, steps, floors)
            .toEntry(exercises?.metricType ?: ExerciseMetricType.WeightReps),
        completedAt = completedAt?.let(Instant::parse),
        rpe = rpe,
    )

    companion object {
        const val COLUMNS =
            "exercise_id,exercise_position,superset_group,set_index,set_type,weight_kg,reps,duration_s,distance_m," +
                "steps,floors,rpe,completed_at,exercises(metric_type)"
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
    // Always sent, even when null, so a re-upsert after a metric change clears the columns that no longer apply.
    @EncodeDefault @SerialName("duration_s") val durationS: Int? = null,
    @EncodeDefault @SerialName("distance_m") val distanceM: Double? = null,
    @EncodeDefault val steps: Int? = null,
    @EncodeDefault val floors: Int? = null,
    // Always sent, even when null, so clearing the RPE reaches the server.
    @EncodeDefault val rpe: Double? = null,
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
    val rpe: Double? = null,
    @SerialName("duration_s") val durationS: Int? = null,
    @SerialName("distance_m") val distanceM: Double? = null,
    val steps: Int? = null,
    val floors: Int? = null,
) {
    fun toDomain() = PreviousSet(setIndex, weightKg, reps, rpe, durationS, distanceM, steps, floors)
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

/** Row returned by the `personal_records` RPC; unknown record types are dropped. */
@Serializable
internal data class PersonalRecordDto(
    @SerialName("record_type") val recordType: String,
    val value: Double,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("session_date") val sessionDate: LocalDate? = null,
) {
    fun toDomain(): PersonalRecord? =
        PersonalRecordType.fromKey(recordType)?.let { PersonalRecord(it, value, sessionId, sessionDate) }
}

/** Row returned by the `exercise_session_records` RPC. */
@Serializable
internal data class ExerciseSessionRecordsDto(
    @SerialName("session_id") val sessionId: String,
    @SerialName("started_at") val startedAt: String,
    @SerialName("heaviest_lift") val heaviestLift: Double? = null,
    @SerialName("best_set") val bestSet: Double? = null,
    @SerialName("session_volume") val sessionVolume: Double = 0.0,
    @SerialName("best_one_rm") val bestOneRm: Double? = null,
    @SerialName("total_reps") val totalReps: Long = 0,
    @SerialName("best_duration_s") val bestDurationS: Int? = null,
    @SerialName("best_distance_m") val bestDistanceM: Double? = null,
    @SerialName("total_steps") val totalSteps: Int? = null,
    @SerialName("total_floors") val totalFloors: Int? = null,
) {
    fun toDomain() = ExerciseSessionRecords(
        sessionId, Instant.parse(startedAt), heaviestLift, bestSet, sessionVolume, bestOneRm, totalReps,
        bestDurationS, bestDistanceM, totalSteps, totalFloors,
    )
}
