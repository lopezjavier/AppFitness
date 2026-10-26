package io.github.lopezjavier.appfitness.designsystem

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
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
        else -> "${WEEKDAYS_SHORT[date.dayOfWeek.ordinal]}, ${date.day} ${MONTHS_SHORT[date.month.ordinal]}"
    }

    /** Screen and sheet titles: "24 de octubre". */
    fun title(date: LocalDate): String = "${date.day} de ${MONTHS[date.month.ordinal]}"

    /** Month headers: "octubre 2026". */
    fun month(date: LocalDate): String = "${MONTHS[date.month.ordinal]} ${date.year}"
}
