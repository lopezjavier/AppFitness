package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.CheckIn
import io.github.lopezjavier.appfitness.domain.model.CheckInDay
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResolveCheckInDayTest {
    private val resolve = ResolveCheckInDay()

    // 2026-11-15 is a Sunday; 2026-11-09 the Monday of that week; 2026-11-16 the next Monday.
    private val sunday = LocalDate(2026, 11, 15)
    private val monday = LocalDate(2026, 11, 9)
    private val longAgo = LocalDate(2026, 1, 1)

    private fun resolveDay(today: LocalDate, weekday: Int = 7, firstDay: LocalDate? = longAgo) =
        resolve(today, weekday, firstDay)

    @Test
    fun todayIsTheChosenDay_isDueToday() {
        assertEquals(CheckInDay(sunday, monday, null), resolveDay(sunday))
    }

    @Test
    fun todayBeforeTheChosenDayInTheWeek_usesPreviousOccurrence() {
        val wednesday = LocalDate(2026, 11, 11)

        assertEquals(CheckInDay(LocalDate(2026, 11, 8), LocalDate(2026, 11, 2), null), resolveDay(wednesday))
    }

    @Test
    fun mondayAfterSunday_keepsTheClosedSundayWindow() {
        assertEquals(CheckInDay(sunday, monday, null), resolveDay(LocalDate(2026, 11, 16)))
    }

    @Test
    fun everyWeekdayAsChosenDay_resolvesToItsLatestOccurrence() {
        val today = LocalDate(2026, 11, 18) // Wednesday
        val expectedDays = listOf(16, 17, 18, 12, 13, 14, 15) // ISO weekday 1..7 -> latest day of November
        for (weekday in 1..7) {
            val day = resolveDay(today, weekday)?.day
            assertEquals(LocalDate(2026, 11, expectedDays[weekday - 1]), day, "weekday $weekday")
        }
    }

    @Test
    fun mondayAsChosenDay_weekStartIsTheDayItself() {
        val result = resolveDay(LocalDate(2026, 11, 16), weekday = 1)

        assertEquals(CheckInDay(LocalDate(2026, 11, 16), LocalDate(2026, 11, 16), null), result)
    }

    @Test
    fun fridayAsChosenDay_belongsToItsMondaySundayWeek() {
        val result = resolveDay(LocalDate(2026, 11, 13), weekday = 5)

        assertEquals(LocalDate(2026, 11, 13), result?.day)
        assertEquals(monday, result?.weekStart)
    }

    @Test
    fun beforeTheFirstDay_isNotListed() {
        assertNull(resolveDay(sunday, firstDay = LocalDate(2026, 11, 16)))
    }

    @Test
    fun firstDayEqualToTheCheckInDay_isListed() {
        assertEquals(sunday, resolveDay(sunday, firstDay = sunday)?.day)
    }

    @Test
    fun withoutFirstDay_isNotListed() {
        assertNull(resolveDay(sunday, firstDay = null))
    }

    @Test
    fun changingTheDayMidWindow_resolvesTheNewDay() {
        val wednesday = LocalDate(2026, 11, 11)

        assertEquals(LocalDate(2026, 11, 8), resolveDay(wednesday, weekday = 7)?.day)
        assertEquals(LocalDate(2026, 11, 11), resolveDay(wednesday, weekday = 3)?.day)
    }

    @Test
    fun answerIsAttachedByTheCaller_notByResolve() {
        val answered = resolveDay(sunday)?.copy(answer = CheckIn(monday, 4, "ok"))

        assertEquals(4, answered?.answer?.energy)
    }
}
