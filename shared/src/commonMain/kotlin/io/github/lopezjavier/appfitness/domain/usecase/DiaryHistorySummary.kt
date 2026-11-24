package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.DiaryDaySummary
import io.github.lopezjavier.appfitness.domain.model.MealTotals
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Average per complete day of a period. [loggedDays] counts every day with entries, [completeDays] the ones kept by
 * [CompleteDays]; with `completeDays == 0` the figures are zero ("Sin días completos").
 */
data class PeriodAverage(
    val loggedDays: Int,
    val completeDays: Int,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
)

/** Total energy of the complete days of a month plus their average. */
data class MonthSummary(
    val loggedDays: Int,
    val completeDays: Int,
    val totalKcal: Double,
    val avgKcalPerCompleteDay: Double,
    val avgProteinG: Double,
    val avgCarbsG: Double,
    val avgFatG: Double,
)

/**
 * Diary history figures (planning/04 §2.1, 07 §3 C1). Averages count only complete days (see [CompleteDays]) and never
 * today. Sums run in integer tenths so 0.1 values never drift; a null result means "Sin registros".
 */
object DiaryHistorySummary {
    private const val TENTHS = 10.0
    private const val HALF = 0.5
    private const val WEEK_DAYS = 7

    fun day(summaries: List<DiaryDaySummary>, date: LocalDate): DiaryDaySummary? = summaries.firstOrNull {
        it.date == date
    }

    /** Average per complete day over the 7 days before [today] (`today-7..today-1`). */
    fun week(summaries: List<DiaryDaySummary>, today: LocalDate): PeriodAverage? {
        val last = today.minus(1, DateTimeUnit.DAY)
        val from = today.minus(WEEK_DAYS, DateTimeUnit.DAY)
        val result = CompleteDays.window(summaries, last, from)
        if (result.logged.isEmpty()) return null
        val average = Tenths.of(result.complete).averageOver(result.complete.size)
        return PeriodAverage(
            result.logged.size,
            result.complete.size,
            average.kcal,
            average.proteinG,
            average.carbsG,
            average.fatG,
        )
    }

    /** Figures of the complete days of [month] up to `min(month end, today-1)`. */
    fun month(summaries: List<DiaryDaySummary>, month: YearMonth, today: LocalDate): MonthSummary? {
        val yesterday = today.minus(1, DateTimeUnit.DAY)
        val last = if (month.lastDay < yesterday) month.lastDay else yesterday
        val result = CompleteDays.window(summaries, last, month.firstDay)
        if (last < month.firstDay || result.logged.isEmpty()) return null
        val sum = Tenths.of(result.complete)
        val average = sum.averageOver(result.complete.size)
        return MonthSummary(
            loggedDays = result.logged.size,
            completeDays = result.complete.size,
            totalKcal = sum.kcal / TENTHS,
            avgKcalPerCompleteDay = average.kcal,
            avgProteinG = average.proteinG,
            avgCarbsG = average.carbsG,
            avgFatG = average.fatG,
        )
    }

    private class Tenths(val kcal: Long, val protein: Long, val carbs: Long, val fat: Long) {
        fun averageOver(count: Int): MealTotals = if (count == 0) {
            MealTotals.ZERO
        } else {
            MealTotals(
                average(kcal, count),
                average(protein, count),
                average(carbs, count),
                average(fat, count),
            )
        }

        private fun average(tenths: Long, count: Int): Double = floor(tenths.toDouble() / count + HALF) / TENTHS

        companion object {
            fun of(days: List<DiaryDaySummary>) = Tenths(
                days.sumOf { toTenths(it.totals.kcal) },
                days.sumOf { toTenths(it.totals.proteinG) },
                days.sumOf { toTenths(it.totals.carbsG) },
                days.sumOf { toTenths(it.totals.fatG) },
            )

            private fun toTenths(value: Double): Long = (value * TENTHS).roundToLong()
        }
    }
}
