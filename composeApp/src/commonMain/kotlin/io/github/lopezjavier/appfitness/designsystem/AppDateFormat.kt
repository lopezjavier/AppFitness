package io.github.lopezjavier.appfitness.designsystem

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.minus

private val MONTHS =
    listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
    )
private val MONTHS_SHORT =
    listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

/** Monday first, matching `DayOfWeek.ordinal`. */
private val WEEKDAYS_SHORT = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")

/**
 * Spanish date formats of the app (glossary, planning/05-design-system.md section 10), without locale APIs.
 * Short month names carry no period ("oct").
 */
object AppDateFormat {
    /** List rows: "Hoy", "Ayer" or "lun, 24 oct" (no year). */
    fun list(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Hoy"
        today.minus(1, DateTimeUnit.DAY) -> "Ayer"
        else -> weekdayDayMonth(date.dayOfWeek, date.day, date.month)
    }

    /** "lun, 24 oct": the list form of a day that is neither today nor yesterday. */
    fun weekdayDayMonth(dayOfWeek: DayOfWeek, day: Int, month: Month): String =
        "${WEEKDAYS_SHORT[dayOfWeek.ordinal]}, $day ${MONTHS_SHORT[month.ordinal]}"

    /** Screen and sheet titles: "24 de octubre". */
    fun title(date: LocalDate): String = "${date.day} de ${MONTHS[date.month.ordinal]}"

    /** A span of days without year: "9 al 15 nov", or "30 oct al 5 nov" across two months. */
    fun dayRange(from: LocalDate, to: LocalDate): String = if (from.month == to.month) {
        "${from.day} al ${to.day} ${MONTHS_SHORT[to.month.ordinal]}"
    } else {
        "${from.day} ${MONTHS_SHORT[from.month.ordinal]} al ${to.day} ${MONTHS_SHORT[to.month.ordinal]}"
    }

    /** Month headers: "octubre 2026". */
    fun month(date: LocalDate): String = "${MONTHS[date.month.ordinal]} ${date.year}"
}
