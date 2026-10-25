package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.ChartPoint
import io.github.lopezjavier.appfitness.domain.model.ExerciseHistoryPoint
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.ExerciseProgress
import io.github.lopezjavier.appfitness.domain.model.ExerciseSessionRecords
import io.github.lopezjavier.appfitness.domain.model.MaxRepsPoint
import io.github.lopezjavier.appfitness.domain.model.PersonalRecordType
import io.github.lopezjavier.appfitness.domain.model.ProgressMetric
import io.github.lopezjavier.appfitness.domain.model.ProgressRange
import io.github.lopezjavier.appfitness.domain.model.StrengthSummary
import io.github.lopezjavier.appfitness.domain.model.WorkoutSession
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
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
 * Series of an exercise inside [range], following its [ExerciseMetricType] (see [ChartMetricSelection]). Record
 * markers are the sessions that beat every earlier one in the family, judged over the whole history before the range
 * filter. The mode is [ProgressMetric.MaxReps] only when a weight-family exercise never had a 1RM in the whole
 * history but has repetitions logged (no markers: there is no record family for it).
 */
object BuildExerciseProgress {
    operator fun invoke(
        sessionRecords: List<ExerciseSessionRecords>,
        metricType: ExerciseMetricType,
        maxReps: List<MaxRepsPoint>,
        range: ProgressRange,
        today: LocalDate,
    ): ExerciseProgress {
        val from = range.from(today)
        val metric = ChartMetricSelection.metric(metricType)
        if (metric == ProgressMetric.OneRepMax && sessionRecords.none { it.bestOneRm != null } &&
            maxReps.isNotEmpty()
        ) {
            return ExerciseProgress(
                mode = ProgressMetric.MaxReps,
                primary = maxReps.filter { from == null || it.date >= from }.sortedBy { it.date }
                    .map { ChartPoint(it.date, it.maxReps.toDouble()) },
                volume = emptyList(),
            )
        }
        val builder = SeriesBuilder(sessionRecords.sortedBy { it.startedAt }, from)
        val primary = when (metric) {
            ProgressMetric.OneRepMax -> builder.series(PersonalRecordType.Best1Rm) { it.bestOneRm }
            ProgressMetric.Reps -> builder.series(null) { it.totalReps.toDouble() }
            ProgressMetric.Duration -> builder.series(PersonalRecordType.BestDuration) { it.bestDurationS?.toDouble() }
            ProgressMetric.Distance -> builder.series(PersonalRecordType.BestDistance) { it.bestDistanceM }
            ProgressMetric.Steps -> builder.series(PersonalRecordType.MostSteps) { it.totalSteps?.toDouble() }
            ProgressMetric.Floors -> builder.series(PersonalRecordType.MostFloors) { it.totalFloors?.toDouble() }
            ProgressMetric.MaxReps -> emptyList()
        }
        val volume = if (metric == ProgressMetric.OneRepMax) {
            builder.series(PersonalRecordType.MaxVolume) { it.sessionVolume }
        } else {
            emptyList()
        }
        return ExerciseProgress(mode = metric, primary = primary, volume = volume)
    }
}

/** Builds one chart series over [ordered] sessions (oldest first), keeping the dates on or after [from]. */
private class SeriesBuilder(private val ordered: List<ExerciseSessionRecords>, private val from: LocalDate?) {
    /** Sessions with a positive [value]; marked as record when they beat [family] over the whole history. */
    fun series(family: PersonalRecordType?, value: (ExerciseSessionRecords) -> Double?): List<ChartPoint> =
        ordered.mapNotNull { session ->
            val v = value(session)?.takeIf { it > 0.0 }
            val date = session.startedAt.toLocalDateTime(TimeZone.UTC).date
            if (v == null || (from != null && date < from)) {
                null
            } else {
                ChartPoint(
                    date,
                    v,
                    isRecord =
                    family != null && family in PersonalRecordCatalogue.beatenBy(session, ordered),
                )
            }
        }
}

/** Which metric the primary chart of an exercise plots, by its metric type. */
object ChartMetricSelection {
    fun metric(metricType: ExerciseMetricType): ProgressMetric = when (metricType) {
        ExerciseMetricType.WeightReps,
        ExerciseMetricType.Weighted,
        ExerciseMetricType.Bodyweight,
        ExerciseMetricType.Assisted,
        -> ProgressMetric.OneRepMax

        ExerciseMetricType.RepsOnly -> ProgressMetric.Reps

        ExerciseMetricType.Duration, ExerciseMetricType.WeightDuration -> ProgressMetric.Duration

        ExerciseMetricType.DistanceDuration -> ProgressMetric.Distance

        ExerciseMetricType.StepsDuration -> ProgressMetric.Steps

        ExerciseMetricType.FloorsDuration -> ProgressMetric.Floors
    }
}
