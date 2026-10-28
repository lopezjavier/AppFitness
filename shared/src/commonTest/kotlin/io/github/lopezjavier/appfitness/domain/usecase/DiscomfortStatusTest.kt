package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.Discomfort
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class DiscomfortStatusTest {
    private val logged = Instant.parse("2026-11-01T10:00:00Z")
    private val discomfort = Discomfort("d1", "e1", logged, null, false)

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
}
