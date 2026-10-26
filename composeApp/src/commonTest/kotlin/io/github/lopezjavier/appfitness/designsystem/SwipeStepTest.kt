package io.github.lopezjavier.appfitness.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SwipeStepTest {
    @Test
    fun belowTheArmThreshold_staysUnarmedWithoutEvent() {
        assertEquals(SwipeStep(armed = false, event = null), swipeStep(0.45f, armed = false))
    }

    @Test
    fun atTheArmThreshold_armsWithOneArmedEvent() {
        assertEquals(SwipeStep(armed = true, event = HapticEvent.Armed), swipeStep(0.5f, armed = false))
    }

    @Test
    fun betweenTheThresholdsWhileArmed_staysArmed() {
        assertEquals(SwipeStep(armed = true, event = null), swipeStep(0.45f, armed = true))
    }

    @Test
    fun belowTheDisarmThresholdWhileArmed_disarmsWithOneDisarmedEvent() {
        assertEquals(SwipeStep(armed = false, event = HapticEvent.Disarmed), swipeStep(0.39f, armed = true))
    }

    @Test
    fun repeatedFramesAtTheSameFraction_emitOnce() {
        val first = swipeStep(0.6f, armed = false)
        val second = swipeStep(0.6f, armed = first.armed)

        assertEquals(HapticEvent.Armed, first.event)
        assertNull(second.event)
    }

    @Test
    fun boundaries_zeroIsUnarmedAndOneIsArmed() {
        assertEquals(SwipeStep(armed = false, event = null), swipeStep(0f, armed = false))
        assertEquals(SwipeStep(armed = true, event = null), swipeStep(1f, armed = true))
        assertEquals(HapticEvent.Armed, swipeStep(1f, armed = false).event)
    }
}
