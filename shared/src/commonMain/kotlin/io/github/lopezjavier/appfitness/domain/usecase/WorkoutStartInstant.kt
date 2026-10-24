package io.github.lopezjavier.appfitness.domain.usecase

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/**
 * The `started_at` sent for a workout logged on a given day: 12:00 local time, so the UTC date used by the history
 * and the volume aggregates equals the chosen date. Today before noon is capped to [now] (the server rejects a
 * start in the future).
 */
object WorkoutStartInstant {
    private val NOON = LocalTime(12, 0)

    fun of(date: LocalDate, now: Instant, zone: TimeZone): Instant = minOf(date.atTime(NOON).toInstant(zone), now)
}

/** Rules of "Guardar como rutina" (US-37) that the client checks before calling the server. */
object SaveWorkoutAsRoutine {
    const val NAME_MAX_LENGTH = 100
    const val SETS_MAX = 10

    /** The name the sheet starts with: the workout's own name, cut to the routine name limit. */
    fun defaultName(workoutName: String): String = workoutName.trim().take(NAME_MAX_LENGTH)

    fun isValidName(name: String): Boolean = name.trim().length in 1..NAME_MAX_LENGTH

    /** True when any exercise has more sets than a routine can hold; the server refuses such a workout. */
    fun exceedsSetLimit(setsPerExercise: List<Int>): Boolean = setsPerExercise.any { it > SETS_MAX }
}
