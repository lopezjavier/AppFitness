package io.github.lopezjavier.appfitness.domain.usecase

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class IsoWeekdayTest {
    @Test
    fun monday_is1() = assertEquals(1, IsoWeekday.of(LocalDate(2026, 10, 26)))

    @Test
    fun sunday_is7() = assertEquals(7, IsoWeekday.of(LocalDate(2026, 10, 25)))

    @Test
    fun monthBoundary_followsTheCalendar() {
        assertEquals(6, IsoWeekday.of(LocalDate(2026, 10, 31)))
        assertEquals(7, IsoWeekday.of(LocalDate(2026, 11, 1)))
    }

    @Test
    fun yearBoundary_followsTheCalendar() {
        assertEquals(4, IsoWeekday.of(LocalDate(2026, 12, 31)))
        assertEquals(5, IsoWeekday.of(LocalDate(2027, 1, 1)))
    }

    @Test
    fun leapDay_isCovered() = assertEquals(2, IsoWeekday.of(LocalDate(2028, 2, 29)))

    @Test
    fun all_listsTheSevenDaysMondayFirst() = assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), IsoWeekday.ALL)
}
