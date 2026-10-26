package io.github.lopezjavier.appfitness.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppHapticsContractTest {
    @Test
    fun perform_routesEveryEventToItsMethod() {
        val haptics = RecordingAppHaptics()

        HapticEvent.entries.forEach { haptics.perform(it) }

        assertEquals(HapticEvent.entries.toList(), haptics.calls)
    }

    @Test
    fun assertSingle_passesForExactlyOneMatchingEvent() {
        val haptics = RecordingAppHaptics()

        haptics.confirm()

        haptics.assertSingle(HapticEvent.Confirm)
    }

    @Test
    fun assertSingle_failsWhenTwoEventsFired() {
        val haptics = RecordingAppHaptics()
        haptics.confirm()
        haptics.confirm()

        val failed = runCatching { haptics.assertSingle(HapticEvent.Confirm) }.isFailure

        assertTrue(failed)
    }

    @Test
    fun noAppHaptics_doesNothing() {
        HapticEvent.entries.forEach { NoAppHaptics.perform(it) }
    }
}
