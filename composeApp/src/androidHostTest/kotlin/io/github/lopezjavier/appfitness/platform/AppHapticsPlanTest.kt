package io.github.lopezjavier.appfitness.platform

import android.view.HapticFeedbackConstants
import io.github.lopezjavier.appfitness.designsystem.HapticEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class AppHapticsPlanTest {
    private fun plan(event: HapticEvent, sdk: Int) = AppHapticsPlan.constantFor(event, sdk)

    @Test
    fun confirm_usesLongPressBelow30AndConfirmFrom30() {
        assertEquals(HapticFeedbackConstants.LONG_PRESS, plan(HapticEvent.Confirm, 26))
        assertEquals(HapticFeedbackConstants.LONG_PRESS, plan(HapticEvent.Confirm, 29))
        assertEquals(HapticFeedbackConstants.CONFIRM, plan(HapticEvent.Confirm, 30))
        assertEquals(HapticFeedbackConstants.CONFIRM, plan(HapticEvent.Confirm, 33))
        assertEquals(HapticFeedbackConstants.CONFIRM, plan(HapticEvent.Confirm, 34))
    }

    @Test
    fun reject_usesLongPressBelow30AndRejectFrom30() {
        assertEquals(HapticFeedbackConstants.LONG_PRESS, plan(HapticEvent.Reject, 29))
        assertEquals(HapticFeedbackConstants.REJECT, plan(HapticEvent.Reject, 30))
        assertEquals(HapticFeedbackConstants.REJECT, plan(HapticEvent.Reject, 34))
    }

    @Test
    fun armed_usesVirtualKeyBelow34AndThresholdActivateFrom34() {
        assertEquals(HapticFeedbackConstants.VIRTUAL_KEY, plan(HapticEvent.Armed, 26))
        assertEquals(HapticFeedbackConstants.VIRTUAL_KEY, plan(HapticEvent.Armed, 33))
        assertEquals(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE, plan(HapticEvent.Armed, 34))
    }

    @Test
    fun disarmed_usesVirtualKeyBelow34AndThresholdDeactivateFrom34() {
        assertEquals(HapticFeedbackConstants.VIRTUAL_KEY, plan(HapticEvent.Disarmed, 30))
        assertEquals(HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE, plan(HapticEvent.Disarmed, 34))
    }

    @Test
    fun tick_usesClockTickBelow34AndSegmentFrequentTickFrom34() {
        assertEquals(HapticFeedbackConstants.CLOCK_TICK, plan(HapticEvent.Tick, 29))
        assertEquals(HapticFeedbackConstants.CLOCK_TICK, plan(HapticEvent.Tick, 33))
        assertEquals(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK, plan(HapticEvent.Tick, 34))
    }
}
