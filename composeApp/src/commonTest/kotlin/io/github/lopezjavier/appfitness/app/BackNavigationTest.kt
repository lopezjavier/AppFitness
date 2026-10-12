package io.github.lopezjavier.appfitness.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackNavigationTest {
    @Test
    fun shouldPopFullScreen_topIsDrawnEntryAndFullScreen_pops() {
        assertTrue(shouldPopFullScreen("a", "a", topIsFullScreen = true))
    }

    @Test
    fun shouldPopFullScreen_topEntryChanged_doesNotPop() {
        assertFalse(shouldPopFullScreen("b", "a", topIsFullScreen = true))
    }

    @Test
    fun shouldPopFullScreen_topIsTab_doesNotPop() {
        assertFalse(shouldPopFullScreen("a", "a", topIsFullScreen = false))
    }

    @Test
    fun shouldPopFullScreen_noEntries_doesNotPop() {
        assertFalse(shouldPopFullScreen(null, null, topIsFullScreen = true))
        assertFalse(shouldPopFullScreen("a", null, topIsFullScreen = true))
    }
}
