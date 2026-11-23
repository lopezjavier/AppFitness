package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.ExerciseSetCount
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.MuscleGroupSets
import io.github.lopezjavier.appfitness.domain.model.RangeExerciseProgress
import io.github.lopezjavier.appfitness.domain.model.RangeProgress
import io.github.lopezjavier.appfitness.domain.model.RangeProgressResult
import io.github.lopezjavier.appfitness.domain.model.RangeProgressState
import io.github.lopezjavier.appfitness.domain.model.RangeRecord
import io.github.lopezjavier.appfitness.domain.model.RangeRecordKind
import io.github.lopezjavier.appfitness.domain.model.RangeRecords
import io.github.lopezjavier.appfitness.domain.model.SetCountPoint
import io.github.lopezjavier.appfitness.domain.model.TrainingSummary
import io.github.lopezjavier.appfitness.domain.model.WeeklyMuscleSets
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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

/** Params of `weekly_muscle_sets`. */
@Serializable
internal data class WeeklyMuscleSetsParams(@SerialName("p_weeks") val weeks: Int)

@Serializable
private data class RangePointDto(@SerialName("e1rm_kg") val e1rmKg: Double = 0.0)

@Serializable
private data class RangeExerciseDto(
    @SerialName("exercise_id") val exerciseId: String,
    @SerialName("main_lift") val mainLift: Boolean = false,
    val sessions: Int = 0,
    val first: RangePointDto,
    val last: RangePointDto? = null,
    @SerialName("change_kg") val changeKg: Double? = null,
    @SerialName("change_pct") val changePct: Double? = null,
    val state: String,
) {
    fun toDomain(): RangeExerciseProgress? {
        val verdict = when (state) {
            "up" -> RangeProgressState.Up
            "margin" -> RangeProgressState.Same
            "down" -> RangeProgressState.Down
            "uncompared" -> RangeProgressState.Uncompared
            else -> return null
        }
        return RangeExerciseProgress(
            exerciseId,
            mainLift,
            sessions,
            first.e1rmKg,
            last?.e1rmKg,
            changeKg,
            changePct,
            verdict,
        )
    }
}

@Serializable
private data class RangeRecordDto(
    @SerialName("exercise_id") val exerciseId: String,
    val kind: String,
    @SerialName("value_kg") val valueKg: Double,
    @SerialName("previous_kg") val previousKg: Double,
    @SerialName("main_lift") val mainLift: Boolean = false,
) {
    fun toDomain(): RangeRecord? {
        val recordKind = when (kind) {
            "e1rm" -> RangeRecordKind.E1rm
            "heaviest" -> RangeRecordKind.Heaviest
            else -> return null
        }
        return RangeRecord(exerciseId, recordKind, valueKg, previousKg, mainLift)
    }
}

/**
 * Reads the `training_range_progress` answer. An exercise or record that cannot be read (unknown state or kind,
 * missing field) is dropped; an unreadable body is an empty result. Never throws.
 */
internal fun rangeProgressOf(element: JsonElement): RangeProgressResult {
    val root = element as? JsonObject
    val exercises = (root?.get("exercises") as? JsonArray).orEmpty().mapNotNull { item ->
        runCatching { coachPlanJson.decodeFromJsonElement(RangeExerciseDto.serializer(), item).toDomain() }.getOrNull()
    }
    val records = root?.get("records") as? JsonObject
    val items = (records?.get("items") as? JsonArray).orEmpty().mapNotNull { item ->
        runCatching { coachPlanJson.decodeFromJsonElement(RangeRecordDto.serializer(), item).toDomain() }.getOrNull()
    }
    val total = (records?.get("total") as? JsonPrimitive)?.intOrNull ?: items.size
    return RangeProgressResult(RangeProgress(exercises), RangeRecords(items, maxOf(total, items.size)))
}

/** Reads the `weekly_muscle_sets` answer; a group with an unknown name or an unreadable week is dropped. */
internal fun weeklyMuscleSetsOf(element: JsonElement): List<WeeklyMuscleSets> =
    (element as? JsonArray).orEmpty().mapNotNull { week ->
        runCatching {
            val obj = week.jsonObject
            val start = LocalDate.parse(obj.getValue("week_start").jsonPrimitive.content)
            val groups = (obj["groups"] as? JsonArray).orEmpty().mapNotNull { group ->
                runCatching {
                    coachPlanJson.decodeFromJsonElement(MuscleGroupSetsRowDto.serializer(), group).toDomain()
                }
                    .getOrNull()
            }
            WeeklyMuscleSets(start, groups)
        }.getOrNull()
    }
