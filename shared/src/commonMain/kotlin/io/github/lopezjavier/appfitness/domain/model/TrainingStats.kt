package io.github.lopezjavier.appfitness.domain.model

import kotlinx.datetime.LocalDate

/** Totals of a window: completed workouts, eligible sets, volume (kg) and active time in seconds. */
data class TrainingSummary(val workouts: Int, val sets: Int, val volume: Double, val durationSeconds: Long)

/** Eligible sets of the week starting on [weekStart] (a UTC Monday). */
data class SetCountPoint(val weekStart: LocalDate, val sets: Int)

/** Sets of a muscle group; secondary muscles count 0.5 per set. */
data class MuscleGroupSets(val muscleGroup: MuscleGroup, val sets: Double)

/** Sets (weighted when a muscle group is requested) and distinct sessions of one exercise. */
data class ExerciseSetCount(val exerciseId: String, val sets: Double, val sessions: Int)
