package io.github.lopezjavier.appfitness.feature.workout

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class SessionDateLabelTest {
    private val today = LocalDate(2026, 10, 24)

    @Test
    fun theSameDayIsToday() {
        assertEquals(DateLabel.Today, sessionDateLabel(today, today))
    }

    @Test
    fun thePreviousDayIsYesterday() {
        assertEquals(DateLabel.Yesterday, sessionDateLabel(LocalDate(2026, 10, 23), today))
    }

    @Test
    fun theFirstOfAMonthIsYesterdayOfTheLastDayOfTheMonthBefore() {
        assertEquals(DateLabel.Yesterday, sessionDateLabel(LocalDate(2026, 10, 31), LocalDate(2026, 11, 1)))
    }

    @Test
    fun anyOtherDayCarriesWeekdayDayAndMonth() {
        val label = sessionDateLabel(LocalDate(2026, 10, 19), today)

        assertEquals(DateLabel.Other(DayOfWeek.MONDAY, 19, Month.OCTOBER), label)
    }

    @Test
    fun aFutureDayIsNotTodayOrYesterday() {
        assertEquals(
            DateLabel.Other(DayOfWeek.SUNDAY, 25, Month.OCTOBER),
            sessionDateLabel(LocalDate(2026, 10, 25), today),
        )
    }

    @Test
    fun theSessionDayUsesTheGivenTimeZoneNotUtc() {
        val startedAt = Instant.parse("2026-10-19T23:30:00Z")

        assertEquals(LocalDate(2026, 10, 20), sessionLocalDate(startedAt, TimeZone.of("Europe/Madrid")))
        assertEquals(LocalDate(2026, 10, 19), sessionLocalDate(startedAt, TimeZone.UTC))
    }
}
