package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.TrainedSession
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth

/** Pure grouping and counting behind the training calendar (no streaks or rest days, ADR-52). */
object TrainingCalendar {
    /** Workouts by the local day they started on in [zone], each day ordered by start time. */
    fun daysOf(sessions: List<TrainedSession>, zone: TimeZone): Map<LocalDate, List<TrainedSession>> = sessions
        .sortedBy { it.startedAt }
        .groupBy { it.startedAt.toLocalDateTime(zone).date }

    /** Months with at least one workout. */
    fun monthsWithTraining(days: Map<LocalDate, List<TrainedSession>>): Set<YearMonth> =
        days.keys.map { it.yearMonth }.toSet()

    /** Workouts per year, ascending; only years with training appear. */
    fun yearCounts(days: Map<LocalDate, List<TrainedSession>>): Map<Int, Int> = count(days) { it.year }

    /** Workouts per month number (1-12) of [year], ascending; only months with training appear. */
    fun monthCounts(days: Map<LocalDate, List<TrainedSession>>, year: Int): Map<Int, Int> =
        count(days.filterKeys { it.year == year }) { it.month.ordinal + 1 }

    private fun count(days: Map<LocalDate, List<TrainedSession>>, key: (LocalDate) -> Int): Map<Int, Int> =
        days.entries.groupBy({ key(it.key) }, { it.value.size })
            .map { (k, sizes) -> k to sizes.sum() }
            .sortedBy { it.first }
            .toMap()
}
