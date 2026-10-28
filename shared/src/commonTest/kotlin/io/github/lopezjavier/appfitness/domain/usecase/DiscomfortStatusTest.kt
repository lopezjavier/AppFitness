package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.Discomfort
import io.github.lopezjavier.appfitness.domain.model.DiscomfortLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class DiscomfortStatusTest {
    private val logged = Instant.parse("2026-11-01T10:00:00Z")
    private val discomfort = Discomfort("d1", "e1", logged, DiscomfortLevel.Mild, null)

    @Test
    fun sixDaysOldIsOpen() {
        assertTrue(DiscomfortStatus.isOpen(discomfort, logged + 6.days))
    }

    @Test
    fun justBeforeSevenDaysIsOpen() {
        assertTrue(DiscomfortStatus.isOpen(discomfort, logged + 7.days - 1.seconds))
    }

    @Test
    fun exactlySevenDaysIsExpired() {
        assertFalse(DiscomfortStatus.isOpen(discomfort, logged + 7.days))
    }

    @Test
    fun eightDaysOldIsExpired() {
        assertFalse(DiscomfortStatus.isOpen(discomfort, logged + 8.days))
    }

    @Test
    fun resolvedIsNeverOpen() {
        val resolved = discomfort.copy(resolvedAt = logged + 1.days)
        assertFalse(DiscomfortStatus.isOpen(resolved, logged + 2.days))
    }

    @Test
    fun highestOpenLevelIsTheMaximumAmongOpenRows() {
        val rows = listOf(
            discomfort.copy(id = "a", level = DiscomfortLevel.Tension),
            discomfort.copy(id = "b", level = DiscomfortLevel.SwellingOrNumbness),
            discomfort.copy(id = "c", level = DiscomfortLevel.PinchOrSnap),
        )

        assertEquals(DiscomfortLevel.SwellingOrNumbness, DiscomfortStatus.highestOpenLevel(rows, logged + 1.days))
    }

    @Test
    fun highestOpenLevelIgnoresExpiredAndResolvedRows() {
        val rows = listOf(
            discomfort.copy(id = "a", level = DiscomfortLevel.SharpOrInjury, loggedAt = logged - 8.days),
            discomfort.copy(id = "b", level = DiscomfortLevel.SharpOrInjury, resolvedAt = logged + 1.days),
            discomfort.copy(id = "c", level = DiscomfortLevel.Tension),
        )

        assertEquals(DiscomfortLevel.Tension, DiscomfortStatus.highestOpenLevel(rows, logged + 2.days))
    }

    @Test
    fun highestOpenLevelIsNullWhenNothingIsOpen() {
        assertNull(DiscomfortStatus.highestOpenLevel(listOf(discomfort), logged + 7.days))
    }
}
