package io.github.lopezjavier.appfitness.platform

import android.view.HapticFeedbackConstants
import io.github.lopezjavier.appfitness.designsystem.HapticEvent

private const val API_CONFIRM_REJECT = 30
private const val API_GESTURE_AND_SEGMENT = 34

/**
 * Pure mapping from a UI haptic event to the one `HapticFeedbackConstants` value to fire on a given API
 * level (planning/05-design-system.md section 9.2). Older versions fall back to constants that exist everywhere,
 * so each action still produces exactly one vibration.
 */
object AppHapticsPlan {
    fun constantFor(event: HapticEvent, sdkInt: Int): Int = when (event) {
        HapticEvent.Confirm ->
            if (sdkInt >= API_CONFIRM_REJECT) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS

        HapticEvent.Reject ->
            if (sdkInt >= API_CONFIRM_REJECT) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS

        HapticEvent.Armed ->
            if (sdkInt >= API_GESTURE_AND_SEGMENT) {
                HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }

        HapticEvent.Disarmed ->
            if (sdkInt >= API_GESTURE_AND_SEGMENT) {
                HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }

        HapticEvent.Tick ->
            if (sdkInt >= API_GESTURE_AND_SEGMENT) {
                HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
            } else {
                HapticFeedbackConstants.CLOCK_TICK
            }
    }
}
