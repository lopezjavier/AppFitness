package io.github.lopezjavier.appfitness.domain.model

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** The record families of one exercise (04 §4.8), in the order the UI lists them. */
enum class PersonalRecordType(val key: String) {
    HeaviestLift("heaviest_lift"),
    BestSet("best_set"),
    MaxVolume("max_volume"),
    Best1Rm("best_1rm"),
    TotalReps("total_reps"),
    BestDuration("best_duration"),
    BestDistance("best_distance"),
    MostSteps("most_steps"),
    MostFloors("most_floors"),
    ;

    companion object {
        fun fromKey(key: String): PersonalRecordType? = entries.firstOrNull { it.key == key }
    }
}

/**
 * The current best of one family. [sessionId] and [date] identify the session that set it (the earliest on ties);
 * both are null for [PersonalRecordType.TotalReps], which accumulates over every session.
 */
data class PersonalRecord(
    val type: PersonalRecordType,
    val value: Double,
    val sessionId: String?,
    val date: LocalDate?,
)

/**
 * Aggregates of one completed session for one exercise (eligible sets only: completed, warm-up only when included).
 * Duration, distance, steps and floors are the best single set. Null means the family has no data.
 */
data class ExerciseSessionRecords(
    val sessionId: String,
    val startedAt: Instant,
    val heaviestLift: Double? = null,
    val bestSet: Double? = null,
    val sessionVolume: Double = 0.0,
    val bestOneRm: Double? = null,
    val totalReps: Long = 0,
    val bestDurationS: Int? = null,
    val bestDistanceM: Double? = null,
    val totalSteps: Int? = null,
    val totalFloors: Int? = null,
)
