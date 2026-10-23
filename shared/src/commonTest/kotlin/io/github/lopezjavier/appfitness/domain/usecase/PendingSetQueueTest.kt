package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class PendingSetQueueTest {
    private val at = Instant.parse("2026-10-19T10:00:00Z")

    private fun write(position: Int, index: Int, weight: Double = 60.0, rpe: Double? = null) = SetWrite(
        sessionId = "s1",
        exerciseId = "e$position",
        exercisePosition = position,
        supersetGroup = null,
        setIndex = index,
        entry = SetEntry.WeightReps(weight, 10),
        completedAt = at,
        setType = SetType.Working,
        rpe = rpe,
    )

    @Test
    fun anEmptyQueueIsEmpty() {
        assertTrue(PendingSetQueue().isEmpty)
    }

    @Test
    fun twoWritesOfTheSameSetKeepOnlyTheLast() {
        val queue = PendingSetQueue()

        queue.enqueue(write(0, 1, weight = 60.0))
        queue.enqueue(write(0, 1, weight = 65.0))

        assertEquals(listOf(write(0, 1, weight = 65.0)), queue.snapshot())
    }

    @Test
    fun writesOfDifferentSetsKeepTheirInsertionOrder() {
        val queue = PendingSetQueue()

        queue.enqueue(write(1, 1))
        queue.enqueue(write(0, 2))

        assertEquals(listOf(1 to 1, 0 to 2), queue.snapshot().map { it.exercisePosition to it.setIndex })
    }

    @Test
    fun removingASentWriteKeepsANewerWriteOfTheSameSet() {
        val queue = PendingSetQueue()
        val sent = write(0, 1, weight = 60.0)
        queue.enqueue(sent)
        queue.enqueue(write(0, 1, weight = 70.0))

        queue.remove(sent)

        assertEquals(1, queue.size)
    }

    @Test
    fun removingTheSentWriteEmptiesTheQueue() {
        val queue = PendingSetQueue()
        val sent = write(0, 1)
        queue.enqueue(sent)

        queue.remove(sent)

        assertTrue(queue.isEmpty)
        assertFalse(queue.contains(0, 1))
    }

    @Test
    fun theQueuedWriteKeepsItsRpe() {
        val queue = PendingSetQueue()
        queue.enqueue(write(0, 1, rpe = 7.5))

        assertEquals(7.5, queue.snapshot().single().rpe)
    }

    @Test
    fun aNewerWriteWithoutRpeReplacesTheOldOne() {
        val queue = PendingSetQueue()
        queue.enqueue(write(0, 1, rpe = 7.5))
        queue.enqueue(write(0, 1, rpe = null))

        assertNull(queue.snapshot().single().rpe)
    }
}
