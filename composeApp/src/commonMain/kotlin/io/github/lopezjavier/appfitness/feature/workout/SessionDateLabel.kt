package io.github.lopezjavier.appfitness.feature.workout

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

fun sessionDateLabel(date: LocalDate, today: LocalDate): DateLabel = when (date) {
    today -> DateLabel.Today
    today.minus(1, DateTimeUnit.DAY) -> DateLabel.Yesterday
    else -> DateLabel.Other(date.dayOfWeek, date.day, date.month)
}

/** The calendar day a session started on, in the user's time zone. */
fun sessionLocalDate(startedAt: Instant, zone: TimeZone = TimeZone.currentSystemDefault()): LocalDate =
    startedAt.toLocalDateTime(zone).date
