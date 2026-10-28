package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.PeriodTraining
import io.github.lopezjavier.appfitness.domain.model.WeeklySummary
import io.github.lopezjavier.appfitness.domain.model.WeeklyWeightTrend
import io.github.lopezjavier.appfitness.domain.repository.BodyMeasurementRepository
import io.github.lopezjavier.appfitness.domain.repository.TrainingStatsRepository
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus

private const val DAYS_PER_WEEK = 7

/**
 * The closed-week figures of a check-in: the Monday-Sunday week containing the check-in day against the previous one.
 * It never depends on today. Training and records use UTC dates (same rule as the other statistics); weight uses the
 * local days of [timeZone] through [DailyWeights]. Any failed read fails the call; it is independent of the check-in
 * save, so a failure never blocks answering.
 */
class LoadCheckInSummary(
    private val stats: TrainingStatsRepository,
    private val measurements: BodyMeasurementRepository,
    private val timeZone: TimeZone,
    private val dailyWeights: DailyWeights = DailyWeights(),
) {
    suspend operator fun invoke(day: LocalDate): Result<WeeklySummary> = runCatching {
        val monday = day.mondayOfWeek()
        val sunday = monday.plus(DatePeriod(days = DAYS_PER_WEEK - 1))
        val previousMonday = monday.plus(DatePeriod(days = -DAYS_PER_WEEK))
        val previousSunday = monday.plus(DatePeriod(days = -1))

        val current = stats.summary(monday, sunday).getOrThrow()
        val previous = stats.summary(previousMonday, previousSunday).getOrThrow()
        val records = stats.weeklyRecords(monday, sunday).getOrThrow()
        val rows = measurements.getMeasurements(
            previousMonday.atStartOfDayIn(timeZone),
            sunday.plus(DatePeriod(days = 1)).atStartOfDayIn(timeZone),
        ).getOrThrow()

        val daily = dailyWeights(rows, timeZone)
        val thisAverage = daily.filter { it.date >= monday }.map { it.kg }.averageOrNull()
        val lastAverage = daily.filter { it.date < monday }.map { it.kg }.averageOrNull()
        val trend = if (thisAverage != null && lastAverage != null) {
            WeeklyWeightTrend.Delta(thisAverage - lastAverage)
        } else {
            WeeklyWeightTrend.NotEnoughData
        }
        WeeklySummary(PeriodTraining(current, previous), records, trend)
    }

    private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()
}
