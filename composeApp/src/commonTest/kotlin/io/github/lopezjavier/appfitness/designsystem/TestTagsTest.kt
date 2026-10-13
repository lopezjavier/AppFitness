package io.github.lopezjavier.appfitness.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TestTagsTest {
    @Test
    fun childTestTag_withPrefix() =
        assertEquals("catalog_empty_state_title", childTestTag("catalog_empty_state", "title"))

    @Test
    fun childTestTag_nullPrefix_isNull() = assertNull(childTestTag(null, "title"))

    @Test
    fun setRowChildTag_delegates() = assertEquals("row_weight", SetRowDefaults.childTag("row", "weight"))
}
