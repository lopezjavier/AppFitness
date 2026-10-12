package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertTrue

private const val AA = 4.5f
private const val LUMINANCE_OFFSET = 0.05f

/** WCAG contrast ratio of [fg] drawn over [bg]; translucent colours are composited first. */
internal fun contrastRatio(fg: Color, bg: Color): Float {
    val opaqueBg = bg.compositeOver(Color.Black)
    val opaqueFg = fg.compositeOver(opaqueBg)
    val a = opaqueFg.luminance() + LUMINANCE_OFFSET
    val b = opaqueBg.luminance() + LUMINANCE_OFFSET
    return maxOf(a, b) / minOf(a, b)
}

/** Disabled controls are exempt from WCAG contrast, so they are not asserted here. */
class ContrastTest {
    private val c = KineticDarkColors

    private fun assertAa(fg: Color, bg: Color, name: String) {
        val ratio = contrastRatio(fg, bg)
        assertTrue(ratio >= AA, "$name: contrast $ratio < $AA")
    }

    @Test
    fun primaryButtonAndSelectedSegment_onNeonLimeOnLime_isAa() {
        assertAa(c.onNeonLime, c.neonLime, "onNeonLime/neonLime")
    }

    @Test
    fun secondaryButton_textPrimaryOnOverlay_isAa() {
        assertAa(c.textPrimary, c.surfaceOverlay, "textPrimary/surfaceOverlay")
    }

    @Test
    fun ghostButton_cyanOnBaseAndRaised_isAa() {
        assertAa(c.athleticCyan, c.surfaceBase, "cyan/surfaceBase")
        assertAa(c.athleticCyan, c.surfaceRaised, "cyan/surfaceRaised")
    }

    @Test
    fun activeChip_cyanOnOverlay_isAa() {
        assertAa(c.athleticCyan, c.surfaceOverlay, "cyan/surfaceOverlay")
    }

    @Test
    fun inactiveChipAndSegment_secondaryOnRaisedAndOverlay_isAa() {
        assertAa(c.textSecondary, c.surfaceRaised, "textSecondary/surfaceRaised")
        assertAa(c.textSecondary, c.surfaceOverlay, "textSecondary/surfaceOverlay")
    }

    @Test
    fun borderStrongOverBase_isBelow3to1_soOutlineIsNeverTheOnlyCue() {
        assertTrue(contrastRatio(c.borderStrong, c.surfaceBase) < 3f)
    }
}
