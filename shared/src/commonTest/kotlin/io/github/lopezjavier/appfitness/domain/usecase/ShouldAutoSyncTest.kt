package io.github.lopezjavier.appfitness.domain.usecase

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class ShouldAutoSyncTest {
    private val today = LocalDate(2026, 10, 22)

    @Test
    fun lastSyncYesterday_isDue() = assertTrue(ShouldAutoSync.isDue(today, LocalDate(2026, 10, 21)))

    @Test
    fun lastSyncToday_isNotDue() = assertFalse(ShouldAutoSync.isDue(today, today))

    @Test
    fun neverSynced_isDue() = assertTrue(ShouldAutoSync.isDue(today, null))

    @Test
    fun localMidnightInMadrid_changesTheDay() {
        val madrid = TimeZone.of("Europe/Madrid")
        val last = LocalDate(2026, 10, 21)
        // 21:59 UTC = 23:59 local (CEST); 22:00 UTC = 00:00 local on the next day.
        val before = Instant.parse("2026-10-21T21:59:00Z").toLocalDateTime(madrid).date
        val after = Instant.parse("2026-10-21T22:00:00Z").toLocalDateTime(madrid).date

        assertEquals(last, before)
        assertFalse(ShouldAutoSync.isDue(before, last))
        assertTrue(ShouldAutoSync.isDue(after, last))
    }
}
