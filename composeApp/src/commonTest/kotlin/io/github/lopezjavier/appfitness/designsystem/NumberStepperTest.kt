package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NumberStepperTest {
    private val c = KineticDarkColors

    @Test
    fun touchTargets_are48dp() = assertEquals(48.dp, NumberStepperDefaults.TouchTarget)

    @Test
    fun visualCircle_fitsInTheTarget() =
        assertTrue(NumberStepperDefaults.VisualSize <= NumberStepperDefaults.TouchTarget)

    @Test
    fun icon_fitsInTheCircle() = assertTrue(NumberStepperDefaults.IconSize < NumberStepperDefaults.VisualSize)

    @Test
    fun enabledIcon_isPrimary_disabledIsGhost() {
        assertEquals(c.textPrimary, NumberStepperDefaults.iconColor(true, c))
        assertEquals(c.ghostData, NumberStepperDefaults.iconColor(false, c))
    }

    @Test
    fun circle_isOverlay() = assertEquals(c.surfaceOverlay, NumberStepperDefaults.buttonColor(c))
}
