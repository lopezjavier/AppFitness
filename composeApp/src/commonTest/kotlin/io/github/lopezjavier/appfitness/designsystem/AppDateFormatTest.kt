package io.github.lopezjavier.appfitness.designsystem

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class AppDateFormatTest {
    private val today = LocalDate(2026, 10, 26)

    @Test
    fun list_today_isHoy() = assertEquals("Hoy", AppDateFormat.list(today, today))

    @Test
    fun list_yesterday_isAyer() = assertEquals("Ayer", AppDateFormat.list(LocalDate(2026, 10, 25), today))

    @Test
    fun list_otherDay_hasWeekdayDayAndShortMonthWithoutPeriodOrYear() =
        assertEquals("sáb, 24 oct", AppDateFormat.list(LocalDate(2026, 10, 24), today))

    @Test
    fun list_dateOfAnotherYear_hasNoYear() =
        assertEquals("jue, 1 ene", AppDateFormat.list(LocalDate(2026, 1, 1), today))

    @Test
    fun list_yesterdayAcrossTheYearBoundary_isAyer() =
        assertEquals("Ayer", AppDateFormat.list(LocalDate(2025, 12, 31), LocalDate(2026, 1, 1)))

    @Test
    fun list_31December_isShort() = assertEquals("jue, 31 dic", AppDateFormat.list(LocalDate(2026, 12, 31), today))

    @Test
    fun list_leapDay_isShort() = assertEquals("mar, 29 feb", AppDateFormat.list(LocalDate(2028, 2, 29), today))

    @Test
    fun list_monday_isLun() = assertEquals("lun, 19 oct", AppDateFormat.list(LocalDate(2026, 10, 19), today))

    @Test
    fun title_isDayDeMonth() = assertEquals("24 de octubre", AppDateFormat.title(LocalDate(2026, 10, 24)))

    @Test
    fun title_firstOfJanuary() = assertEquals("1 de enero", AppDateFormat.title(LocalDate(2026, 1, 1)))

    @Test
    fun title_leapDay() = assertEquals("29 de febrero", AppDateFormat.title(LocalDate(2028, 2, 29)))

    @Test
    fun month_isMonthAndYear() = assertEquals("octubre 2026", AppDateFormat.month(today))

    @Test
    fun month_december() = assertEquals("diciembre 2026", AppDateFormat.month(LocalDate(2026, 12, 31)))
}
