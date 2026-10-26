package io.github.lopezjavier.appfitness.designsystem

import kotlin.test.assertEquals

/** Test double of [AppHaptics] that records every event in order. */
class RecordingAppHaptics : AppHaptics {
    val calls = mutableListOf<HapticEvent>()

    override fun armed() {
        calls += HapticEvent.Armed
    }

    override fun disarmed() {
        calls += HapticEvent.Disarmed
    }

    override fun confirm() {
        calls += HapticEvent.Confirm
    }

    override fun reject() {
        calls += HapticEvent.Reject
    }

    override fun tick() {
        calls += HapticEvent.Tick
    }

    /** Asserts that exactly one event happened and it was [event] (R-29: one vibration per action). */
    fun assertSingle(event: HapticEvent) {
        assertEquals(listOf(event), calls)
    }
}
