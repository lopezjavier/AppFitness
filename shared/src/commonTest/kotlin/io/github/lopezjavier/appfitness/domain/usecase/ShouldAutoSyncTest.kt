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

    private fun rule(last: LocalDate?, available: Boolean = true, online: Boolean = true, canRead: Boolean = true) =
        ShouldAutoSync(today, last, available, online, canRead)

    @Test
    fun lastSyncYesterday_isDue() = assertTrue(rule(LocalDate(2026, 10, 21)))

    @Test
    fun lastSyncToday_isNotDue() = assertFalse(rule(today))

    @Test
    fun neverSynced_isDue() = assertTrue(rule(null))

    @Test
    fun offline_isNotDue() = assertFalse(rule(null, online = false))

    @Test
    fun unavailable_isNotDue() = assertFalse(rule(null, available = false))

    @Test
    fun noReadPermission_isNotDue() = assertFalse(rule(null, canRead = false))

    @Test
    fun localMidnightInMadrid_changesTheDay() {
        val madrid = TimeZone.of("Europe/Madrid")
        val last = LocalDate(2026, 10, 21)
        // 21:59 UTC = 23:59 local (CEST); 22:00 UTC = 00:00 local on the next day.
        val before = Instant.parse("2026-10-21T21:59:00Z").toLocalDateTime(madrid).date
        val after = Instant.parse("2026-10-21T22:00:00Z").toLocalDateTime(madrid).date

        assertEquals(last, before)
        assertFalse(ShouldAutoSync(before, last, available = true, online = true, canRead = true))
        assertTrue(ShouldAutoSync(after, last, available = true, online = true, canRead = true))
    }
}
