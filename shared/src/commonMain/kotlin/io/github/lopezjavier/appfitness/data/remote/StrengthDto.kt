package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.MaxRepsPoint
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** `workout_sets` row with the start of its (completed) session embedded. */
@Serializable
internal data class MaxRepsRowDto(
    @SerialName("session_id") val sessionId: String,
    val reps: Int = 0,
    @SerialName("workout_sessions") val session: SessionStartDto,
) {
    companion object {
        const val COLUMNS = "session_id,reps,workout_sessions!inner(started_at)"

        /** One point per session with its highest reps; the date is the UTC start day, like `exercise_history`. */
        fun toPoints(rows: List<MaxRepsRowDto>): List<MaxRepsPoint> = rows.groupBy { it.sessionId }.map { (id, sets) ->
            val started = Instant.parse(sets.first().session.startedAt)
            MaxRepsPoint(id, started.toLocalDateTime(TimeZone.UTC).date, sets.maxOf { it.reps })
        }.sortedBy { it.date }
    }
}

@Serializable
internal data class SessionStartDto(@SerialName("started_at") val startedAt: String)
