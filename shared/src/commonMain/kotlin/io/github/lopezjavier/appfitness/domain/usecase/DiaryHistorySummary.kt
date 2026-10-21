package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.DiaryDaySummary
import io.github.lopezjavier.appfitness.domain.model.MealTotals
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlin.math.floor
import kotlin.math.roundToLong

/** Average per logged day over the last 7 days. */
data class PeriodAverage(
    val loggedDays: Int,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
)

/** Total energy of a month plus the average per logged day. */
data class MonthSummary(
    val loggedDays: Int,
    val totalKcal: Double,
    val avgKcalPerLoggedDay: Double,
    val avgProteinG: Double,
    val avgCarbsG: Double,
    val avgFatG: Double,
)

/**
 * Diary history figures (planning/04 §2.1). Averages count only days with entries, never the empty ones. Sums run in
 * integer tenths so 0.1 values never drift; a null result means "Sin registros".
 */
object DiaryHistorySummary {
    private const val TENTHS = 10.0
    private const val HALF = 0.5
    private const val WEEK_DAYS = 7

    fun day(summaries: List<DiaryDaySummary>, date: LocalDate): DiaryDaySummary? = summaries.firstOrNull {
        it.date == date
    }

    /** Average per logged day over `today-6..today`. */
    fun week(summaries: List<DiaryDaySummary>, today: LocalDate): PeriodAverage? {
        val from = today.minus(WEEK_DAYS - 1, DateTimeUnit.DAY)
        val logged = summaries.filter { it.date in from..today }
        if (logged.isEmpty()) return null
        val sum = Tenths.of(logged)
        val average = sum.averageOver(logged.size)
        return PeriodAverage(logged.size, average.kcal, average.proteinG, average.carbsG, average.fatG)
    }

    /** Total kcal of [month] and the average per logged day. */
    fun month(summaries: List<DiaryDaySummary>, month: YearMonth): MonthSummary? {
        val logged = summaries.filter { it.date.year == month.year && it.date.month == month.month }
        if (logged.isEmpty()) return null
        val sum = Tenths.of(logged)
        val average = sum.averageOver(logged.size)
        return MonthSummary(
            loggedDays = logged.size,
            totalKcal = sum.kcal / TENTHS,
            avgKcalPerLoggedDay = average.kcal,
            avgProteinG = average.proteinG,
            avgCarbsG = average.carbsG,
            avgFatG = average.fatG,
        )
    }

    private class Tenths(val kcal: Long, val protein: Long, val carbs: Long, val fat: Long) {
        fun averageOver(count: Int): MealTotals = MealTotals(
            average(kcal, count),
            average(protein, count),
            average(carbs, count),
            average(fat, count),
        )

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
