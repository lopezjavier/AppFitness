package io.github.lopezjavier.appfitness.domain.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoachBudgetTest {
    private val month = LocalDate(2026, 11, 1)

    private fun usage(cost: Double) = CoachUsage(month, 10, 5, cost)

    @Test
    fun empty_isZeroAndNotReached() {
        val empty = CoachUsage.empty(month)
        assertEquals(0.0, empty.costEur)
        assertEquals(0f, empty.fraction)
        assertFalse(empty.limitReached)
    }

    @Test
    fun fraction_isShareOfTheLimit() = assertEquals(0.14f, usage(0.42).fraction)

    @Test
    fun belowTheLimit_isNotReached() = assertFalse(usage(2.99).limitReached)

    @Test
    fun atTheLimit_isReached() {
        assertTrue(usage(3.0).limitReached)
        assertEquals(1f, usage(3.0).fraction)
    }

    @Test
    fun overTheLimit_fractionIsClamped() = assertEquals(1f, usage(4.5).fraction)
}
