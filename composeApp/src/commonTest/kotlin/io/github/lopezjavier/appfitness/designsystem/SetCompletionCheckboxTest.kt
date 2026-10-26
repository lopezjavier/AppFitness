package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SetCompletionCheckboxTest {
    private val c = KineticDarkColors

    @Test
    fun visualSize38_touchTarget48() {
        assertEquals(38.dp, SetCompletionCheckboxDefaults.VisualSize)
        assertEquals(48.dp, SetCompletionCheckboxDefaults.TouchTarget)
    }

    @Test
    fun touchTarget_atLeastVisual() =
        assertTrue(SetCompletionCheckboxDefaults.TouchTarget >= SetCompletionCheckboxDefaults.VisualSize)

    @Test
    fun iconSize_fitsInVisual() =
        assertTrue(SetCompletionCheckboxDefaults.IconSize < SetCompletionCheckboxDefaults.VisualSize)

    @Test
    fun colors_checked_limeFill_darkCheck() {
        val colors = SetCompletionCheckboxDefaults.colors(checked = true, appColors = c)
        assertEquals(c.neonLime, colors.container)
        assertEquals(c.onNeonLime, colors.icon)
    }

    @Test
    fun colors_unchecked_overlayFill_ghostGlyph() {
        val colors = SetCompletionCheckboxDefaults.colors(checked = false, appColors = c)
        assertEquals(c.surfaceOverlay, colors.container)
        assertEquals(c.ghostData, colors.icon)
    }

    @Test
    fun colors_containerOpaque_bothStates() {
        listOf(true, false).forEach {
            assertEquals(1f, SetCompletionCheckboxDefaults.colors(it, c).container.alpha)
        }
    }

    @Test
    fun hapticFor_check_isConfirm() = assertEquals(HapticEvent.Confirm, SetCompletionCheckboxDefaults.hapticFor(true))

    @Test
    fun hapticFor_uncheck_isNothing() = assertNull(SetCompletionCheckboxDefaults.hapticFor(false))

    @Test
    fun shape_isFull() = assertEquals(AppShapes.full, SetCompletionCheckboxDefaults.Shape)
}
