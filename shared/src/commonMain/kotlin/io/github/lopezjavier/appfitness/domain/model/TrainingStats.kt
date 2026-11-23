package io.github.lopezjavier.appfitness.domain.model

import kotlinx.datetime.LocalDate

/** Totals of a window: completed workouts, eligible sets, volume (kg) and active time in seconds. */
data class TrainingSummary(val workouts: Int, val sets: Int, val volume: Double, val durationSeconds: Long)

/** Eligible sets of the week starting on [weekStart] (a UTC Monday). */
data class SetCountPoint(val weekStart: LocalDate, val sets: Int)

/** Sets of a muscle group; secondary muscles count 0.5 per set. */
data class MuscleGroupSets(val muscleGroup: MuscleGroup, val sets: Double)

/** Verdict of an exercise over a range (planning/07-coach-design.md section 3 D1); [Same] is the display margin. */
enum class RangeProgressState { Up, Same, Down, Uncompared }

/** Progress of one exercise over a range: first/last e1RM (kg); none of last/change when uncompared. */
data class RangeExerciseProgress(
    val exerciseId: String,
    val mainLift: Boolean,
    val sessions: Int,
    val firstKg: Double,
    val lastKg: Double?,
    val changeKg: Double?,
    val changePct: Double?,
    val state: RangeProgressState,
)

/** Exercises of a range, main lifts first, then most sessions first (the server order). */
data class RangeProgress(val exercises: List<RangeExerciseProgress>)

enum class RangeRecordKind { E1rm, Heaviest }

/** A record of the range: [valueKg] against the best earlier [previousKg]. */
data class RangeRecord(
    val exerciseId: String,
    val kind: RangeRecordKind,
    val valueKg: Double,
    val previousKg: Double,
    val mainLift: Boolean,
)

/** At most three [items] with the [total] number of records in the range (planning/07 section 3 D2). */
data class RangeRecords(val items: List<RangeRecord>, val total: Int)

/** Both answers of the single `training_range_progress` call. */
data class RangeProgressResult(val progress: RangeProgress, val records: RangeRecords)

/** Sets per muscle group of the ISO week starting on [weekStart] (a UTC Monday); empty weeks have no groups. */
data class WeeklyMuscleSets(val weekStart: LocalDate, val groups: List<MuscleGroupSets>)

/** Sets (weighted when a muscle group is requested) and distinct sessions of one exercise. */
data class ExerciseSetCount(val exerciseId: String, val sets: Double, val sessions: Int)
