package io.github.lopezjavier.appfitness.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals

class WheelPickerColorsTest {
    private val c = KineticDarkColors

    @Test
    fun itemColor_centreIsPrimary_nextIsSecondary_farNeighboursAreTertiaryHints() {
        assertEquals(c.textPrimary, WheelPickerDefaults.itemColor(0, c))
        assertEquals(c.textSecondary, WheelPickerDefaults.itemColor(1, c))
        assertEquals(c.textTertiary, WheelPickerDefaults.itemColor(2, c))
        assertEquals(c.textTertiary, WheelPickerDefaults.itemColor(3, c))
    }
}
