package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.ChartPoint
import io.github.lopezjavier.appfitness.domain.model.ExerciseHistoryPoint
import io.github.lopezjavier.appfitness.domain.model.ExerciseProgress
import io.github.lopezjavier.appfitness.domain.model.MaxRepsPoint
import io.github.lopezjavier.appfitness.domain.model.ProgressMode
import io.github.lopezjavier.appfitness.domain.model.ProgressRange
import io.github.lopezjavier.appfitness.domain.model.StrengthSummary
import io.github.lopezjavier.appfitness.domain.model.WorkoutSession
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.time.Instant

private const val TOP_LIMIT = 3
private const val DELTA_DAYS = 30

/** Ids of the most trained exercises (by completed sessions since [since]); ties go to the most recent one. */
object RankTopExercises {
    operator fun invoke(sessions: List<WorkoutSession>, since: Instant, limit: Int = TOP_LIMIT): List<String> {
        val lastSeen = mutableMapOf<String, Instant>()
        val counts = mutableMapOf<String, Int>()
        sessions.filter { it.startedAt >= since }.forEach { session ->
            session.exercises.filter { exercise -> exercise.sets.any { it.isCompleted } }
                .map { it.exerciseId }
                .distinct()
                .forEach { id ->
                    counts[id] = (counts[id] ?: 0) + 1
                    lastSeen[id] = maxOf(lastSeen[id] ?: session.startedAt, session.startedAt)
                }
        }
        return counts.keys
            .sortedWith(compareByDescending<String> { counts.getValue(it) }.thenByDescending { lastSeen.getValue(it) })
            .take(limit)
    }
}

/** 1RM record of the whole [history] and its growth against the best of [DELTA_DAYS] days ago. */
object BuildStrengthSummary {
    operator fun invoke(history: List<ExerciseHistoryPoint>, today: LocalDate): StrengthSummary {
        val record = history.mapNotNull { it.bestOneRepMax }.maxOrNull()
        val cutOff = today.minus(DELTA_DAYS, DateTimeUnit.DAY)
        val prior = history.filter { it.date <= cutOff }.mapNotNull { it.bestOneRepMax }.maxOrNull()
        return StrengthSummary(record, if (record != null && prior != null) record - prior else null)
    }
}

/**
 * Series of an exercise inside [range]. The mode is [ProgressMode.MaxReps] only when the exercise never had a
 * loaded set (no 1RM in the whole history) but has repetitions logged.
 */
object BuildExerciseProgress {
    operator fun invoke(
        history: List<ExerciseHistoryPoint>,
        maxReps: List<MaxRepsPoint>,
        range: ProgressRange,
        today: LocalDate,
    ): ExerciseProgress {
        val from = range.from(today)
        return if (history.none { it.bestOneRepMax != null } && maxReps.isNotEmpty()) {
            ExerciseProgress(
                mode = ProgressMode.MaxReps,
                primary = maxReps.filter { it.date >= from }.sortedBy { it.date }
                    .map { ChartPoint(it.date, it.maxReps.toDouble()) },
                volume = emptyList(),
            )
        } else {
            val inRange = history.filter { it.date >= from }.sortedBy { it.date }
            ExerciseProgress(
                mode = ProgressMode.OneRepMax,
                primary = inRange.mapNotNull { point -> point.bestOneRepMax?.let { ChartPoint(point.date, it) } },
                volume = inRange.map { ChartPoint(it.date, it.totalVolume) },
            )
        }
    }
}
