package io.github.lopezjavier.appfitness.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DiscomfortLevelTest {
    @Test
    fun levelsAreOrderedFromOneToFive() {
        assertEquals(listOf(1, 2, 3, 4, 5), DiscomfortLevel.entries.map { it.value })
    }

    @Test
    fun fromValueRoundTripsEveryLevel() {
        DiscomfortLevel.entries.forEach { assertEquals(it, DiscomfortLevel.fromValue(it.value)) }
    }

    @Test
    fun fromValueRejectsOutOfRange() {
        assertNull(DiscomfortLevel.fromValue(0))
        assertNull(DiscomfortLevel.fromValue(6))
        assertNull(DiscomfortLevel.fromValue(-1))
    }

    @Test
    fun onlyTheTwoHighestLevelsAreSevere() {
        assertEquals(listOf(false, false, false, true, true), DiscomfortLevel.entries.map { it.severe })
    }
}
