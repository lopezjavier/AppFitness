package io.github.lopezjavier.appfitness.domain.model

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus

/**
 * The single place for biometric bounds and defaults (US-06). The onboarding pickers, their tests and the
 * Profile edit screen all use these values. Weight is an integer number of tenths of a kilogram.
 */
object BiometricBounds {
    val AGE_YEARS: IntRange = 14..100
    val HEIGHT_CM: IntRange = 100..250
    val WEIGHT_TENTHS: IntRange = 200..4000

    const val DEFAULT_AGE_YEARS = 30
    const val DEFAULT_HEIGHT_CM = 170
    const val DEFAULT_WEIGHT_TENTHS = 700

    private const val MONTHS_IN_YEAR = 12
    private const val FEBRUARY_LEAP_DAYS = 29
    private const val FEBRUARY_DAYS = 28
    private const val LONG_MONTH_DAYS = 31
    private const val SHORT_MONTH_DAYS = 30

    /** Age in full years on [today]. A 29 February birthday is reached on 1 March in a non-leap year. */
    fun ageOn(birthDate: LocalDate, today: LocalDate): Int {
        val years = today.year - birthDate.year
        val birthdayPassed =
            today.month.number > birthDate.month.number ||
                (today.month.number == birthDate.month.number && today.day >= birthDate.day)
        return if (birthdayPassed) years else years - 1
    }

    /** Oldest to youngest allowed birth date: ages [AGE_YEARS] on [today]. */
    fun birthDateRange(today: LocalDate): ClosedRange<LocalDate> {
        val oldest = today.minus(DatePeriod(years = AGE_YEARS.last + 1)).plus(1, DateTimeUnit.DAY)
        val youngest = today.minus(DatePeriod(years = AGE_YEARS.first))
        return oldest..youngest
    }

    fun clampBirthDate(date: LocalDate, today: LocalDate): LocalDate = date.coerceIn(birthDateRange(today))

    fun clampHeight(heightCm: Int): Int = heightCm.coerceIn(HEIGHT_CM)

    fun clampWeightTenths(tenths: Int): Int = tenths.coerceIn(WEIGHT_TENTHS)

    /** The date of [year]/[month]/[day] with the day cut to the month length, then clamped to the allowed range. */
    fun composeBirthDate(year: Int, month: Int, day: Int, today: LocalDate): LocalDate {
        val safeDay = day.coerceIn(1, daysIn(year, month))
        return clampBirthDate(LocalDate(year, month, safeDay), today)
    }

    fun defaultBirthDate(today: LocalDate): LocalDate = today.minus(DatePeriod(years = DEFAULT_AGE_YEARS))

    /** Number of days of [month] (1-12) in [year]. */
    fun daysIn(year: Int, month: Int): Int {
        require(month in 1..MONTHS_IN_YEAR) { "month $month is outside 1..12" }
        return when (Month(month)) {
            Month.FEBRUARY -> if (isLeapYear(year)) FEBRUARY_LEAP_DAYS else FEBRUARY_DAYS
            Month.APRIL, Month.JUNE, Month.SEPTEMBER, Month.NOVEMBER -> SHORT_MONTH_DAYS
            else -> LONG_MONTH_DAYS
        }
    }

    private const val LEAP_CYCLE = 4
    private const val CENTURY = 100
    private const val FOUR_CENTURIES = 400

    private fun isLeapYear(year: Int): Boolean =
        year % LEAP_CYCLE == 0 && (year % CENTURY != 0 || year % FOUR_CENTURIES == 0)
}
