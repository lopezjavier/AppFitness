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

    @Test
    fun state_dragBy_returnsTheNumberOfSwapsOfThatDrag() {
        val state = ReorderableState<String> { it }
        listOf("A", "B", "C", "D").forEach { state.onHeight(it, 100) }
        state.start(listOf("A", "B", "C", "D"), "A")

        assertEquals(0, state.dragBy(30f))
        assertEquals(2, state.dragBy(190f))
    }

    @Test
    fun autoScrollDelta_inTheMiddle_isZero() {
        assertEquals(
            0f,
            autoScrollDelta(
                top = 300f,
                bottom = 400f,
                viewportTop = 0f,
                viewportBottom = 800f,
                edge = 100f,
                maxStep = 20f,
            ),
        )
    }

    @Test
    fun autoScrollDelta_nearTheTop_scrollsUpProportionally() {
        val near =
            autoScrollDelta(
                top = 50f,
                bottom = 150f,
                viewportTop = 0f,
                viewportBottom = 800f,
                edge = 100f,
                maxStep = 20f,
            )
        val past =
            autoScrollDelta(
                top = -50f,
                bottom = 50f,
                viewportTop = 0f,
                viewportBottom = 800f,
                edge = 100f,
                maxStep = 20f,
            )

        assertEquals(-10f, near)
        assertEquals(-20f, past)
    }

    @Test
    fun autoScrollDelta_nearTheBottom_scrollsDown() {
        val delta =
            autoScrollDelta(
                top = 650f,
                bottom = 750f,
                viewportTop = 0f,
                viewportBottom = 800f,
                edge = 100f,
                maxStep = 20f,
            )

        assertEquals(10f, delta)
    }

    @Test
    fun reorderTargets_atTheEnds_onlyOffersTheValidDirection() {
        assertEquals(listOf(1), reorderTargets(index = 0, count = 3))
        assertEquals(listOf(0, 2), reorderTargets(index = 1, count = 3))
        assertEquals(listOf(1), reorderTargets(index = 2, count = 3))
        assertEquals(emptyList(), reorderTargets(index = 0, count = 1))
    }
}
