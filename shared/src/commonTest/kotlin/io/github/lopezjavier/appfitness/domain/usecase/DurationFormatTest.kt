package io.github.lopezjavier.appfitness.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DurationFormatTest {
    @Test
    fun format_padsMinutesAndSeconds() {
        assertEquals("01:30", DurationFormat.formatMmSs(90))
        assertEquals("00:00", DurationFormat.formatMmSs(0))
        assertEquals("59:59", DurationFormat.formatMmSs(3599))
    }

    @Test
    fun format_switchesToHoursPastAnHour() {
        assertEquals("1:00:00", DurationFormat.formatMmSs(3600))
        assertEquals("1:01:05", DurationFormat.formatMmSs(3665))
    }

    @Test
    fun format_clampsNegativeToZero() {
        assertEquals("00:00", DurationFormat.formatMmSs(-5))
    }

    @Test
    fun parse_readsMinutesAndSeconds() {
        assertEquals(90, DurationFormat.parseMmSs("1:30"))
        assertEquals(90, DurationFormat.parseMmSs("01:30"))
        assertEquals(0, DurationFormat.parseMmSs("0:00"))
    }

    @Test
    fun parse_readsHoursMinutesSeconds() {
        assertEquals(3665, DurationFormat.parseMmSs("1:01:05"))
    }

    @Test
    fun parse_readsAPlainNumberAsSeconds() {
        assertEquals(90, DurationFormat.parseMmSs("90"))
        assertEquals(0, DurationFormat.parseMmSs("0"))
    }

    @Test
    fun parse_trimsWhitespace() {
        assertEquals(90, DurationFormat.parseMmSs(" 1:30 "))
    }

    @Test
    fun parse_rejectsSecondsOrMinutesOutOfRange() {
        assertNull(DurationFormat.parseMmSs("1:75"))
        assertNull(DurationFormat.parseMmSs("1:60:00"))
    }

    @Test
    fun parse_rejectsEmptyAndMalformedInput() {
        assertNull(DurationFormat.parseMmSs(""))
        assertNull(DurationFormat.parseMmSs("  "))
        assertNull(DurationFormat.parseMmSs("a:30"))
        assertNull(DurationFormat.parseMmSs("1:"))
        assertNull(DurationFormat.parseMmSs(":30"))
        assertNull(DurationFormat.parseMmSs("-1:30"))
        assertNull(DurationFormat.parseMmSs("1:2:3:4"))
    }
}
