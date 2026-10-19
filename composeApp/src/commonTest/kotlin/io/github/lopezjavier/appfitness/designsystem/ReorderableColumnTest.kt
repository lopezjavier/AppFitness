package io.github.lopezjavier.appfitness.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReorderableColumnTest {
    @Test
    fun dragStep_belowHalfTheNeighbour_doesNotSwap() {
        assertNull(dragStep(index = 1, offset = 40f, nextSlot = 100f, previousSlot = 100f))
        assertNull(dragStep(index = 1, offset = -40f, nextSlot = 100f, previousSlot = 100f))
    }

    @Test
    fun dragStep_pastHalfTheNextSlot_swapsDownAndRebasesTheOffset() {
        assertEquals(DragStep(2, -40f), dragStep(index = 1, offset = 60f, nextSlot = 100f, previousSlot = 100f))
    }

    @Test
    fun dragStep_pastHalfThePreviousSlot_swapsUpAndRebasesTheOffset() {
        assertEquals(DragStep(0, 40f), dragStep(index = 1, offset = -60f, nextSlot = 100f, previousSlot = 100f))
    }

    @Test
    fun dragStep_atTheEnds_neverSwapsOutward() {
        assertNull(dragStep(index = 0, offset = -500f, nextSlot = 100f, previousSlot = null))
        assertNull(dragStep(index = 2, offset = 500f, nextSlot = null, previousSlot = 100f))
    }

    @Test
    fun state_dragPastTwoNeighbours_endsThreeSlotsDownAndReportsOneMove() {
        val state = ReorderableState<String> { it }
        state.gapPx = 10f
        listOf("A", "B", "C", "D").forEach { state.onHeight(it, 90) }
        state.start(listOf("A", "B", "C", "D"), "A")

        state.dragBy(220f)
        val move = state.drop()

        assertEquals(listOf("B", "C", "A", "D"), state.order)
        assertEquals(0 to 2, move)
    }

    @Test
    fun state_dragBackToTheStart_reportsNoMove() {
        val state = ReorderableState<String> { it }
        listOf("A", "B", "C").forEach { state.onHeight(it, 100) }
        state.start(listOf("A", "B", "C"), "B")

        state.dragBy(80f)
        state.dragBy(-80f)

        assertNull(state.drop())
        assertNull(state.order)
    }
}
