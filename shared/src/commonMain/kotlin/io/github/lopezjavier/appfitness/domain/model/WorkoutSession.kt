package io.github.lopezjavier.appfitness.domain.model

import kotlin.time.Instant

/** One set of a session. A set is pending until [completedAt] is set; finishing the session drops pending sets. */
data class WorkoutSet(
    val setIndex: Int,
    val setType: SetType,
    val entry: SetEntry,
    val completedAt: Instant?,
    val rpe: Double? = null,
    /** Free-text note of the set (1-200 chars, [NoteText]); null when none. */
    val notes: String? = null,
) {
    val isCompleted: Boolean get() = completedAt != null
}

/** An exercise of a session with its sets ordered by [WorkoutSet.setIndex]. [position] is 0-based. */
data class SessionExercise(
    val exerciseId: String,
    val position: Int,
    val supersetGroup: Int?,
    val sets: List<WorkoutSet>,
)

data class WorkoutSession(
    val id: String,
    val routineId: String?,
    val name: String,
    val status: SessionStatus,
    val startedAt: Instant,
    val endedAt: Instant?,
    val exercises: List<SessionExercise>,
    /** Set while the session is paused; the paused span is not part of the duration. */
    val pausedAt: Instant? = null,
    /** Seconds of already closed pauses. */
    val pausedTotalSeconds: Long = 0,
    /** Rest seconds changed during the session, by exercise position (original, not renumbered). */
    val exerciseRests: Map<Int, Int> = emptyMap(),
)
