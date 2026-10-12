package io.github.lopezjavier.appfitness.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AppOptionTest {
    @Test
    fun onTap_otherOption_returnsItsIndex() {
        assertEquals(2, SingleSelection.onTap(selectedIndex = 0, tappedIndex = 2))
    }

    @Test
    fun onTap_activeOption_returnsNull() {
        assertNull(SingleSelection.onTap(selectedIndex = 1, tappedIndex = 1))
    }

    @Test
    fun requireValid_inRange_passes() {
        SingleSelection.requireValid(selectedIndex = 0, size = 1)
        SingleSelection.requireValid(selectedIndex = 3, size = 4)
    }

    @Test
    fun requireValid_negativeOrTooLarge_throws() {
        assertFailsWith<IllegalArgumentException> { SingleSelection.requireValid(selectedIndex = -1, size = 3) }
        assertFailsWith<IllegalArgumentException> { SingleSelection.requireValid(selectedIndex = 3, size = 3) }
    }

    @Test
    fun requireValid_emptyOptions_throws() {
        assertFailsWith<IllegalArgumentException> { SingleSelection.requireValid(selectedIndex = 0, size = 0) }
    }

    @Test
    fun appOption_contentDescription_defaultsToNull() {
        assertNull(AppOption("Label", "tag").contentDescription)
        assertEquals("1 mes", AppOption("1M", "tag", "1 mes").contentDescription)
    }
}
