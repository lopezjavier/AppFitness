package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertTrue

private const val AA = 4.5f

/** WCAG 1.4.11 minimum for graphics and UI components. */
private const val NON_TEXT = 3f
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

    @Test
    fun macroPercentText_macroColoursOnRaised_isAa() {
        assertAa(c.macroProtein, c.surfaceRaised, "protein/surfaceRaised")
        assertAa(c.macroCarbs, c.surfaceRaised, "carbs/surfaceRaised")
        assertAa(c.macroFat, c.surfaceRaised, "fat/surfaceRaised")
    }

    @Test
    fun ringStatusPill_limeAndAmberOnOverlay_isAa() {
        assertAa(c.neonLime, c.surfaceOverlay, "lime/surfaceOverlay")
        assertAa(c.energyAmber, c.surfaceOverlay, "amber/surfaceOverlay")
    }

    @Test
    fun trendPill_allToneColoursOnOverlay_isAa() {
        assertAa(c.electricGreen, c.surfaceOverlay, "electricGreen/surfaceOverlay")
        assertAa(c.error, c.surfaceOverlay, "error/surfaceOverlay")
        assertAa(c.energyAmber, c.surfaceOverlay, "amber/surfaceOverlay")
        assertAa(c.textSecondary, c.surfaceOverlay, "textSecondary/surfaceOverlay")
    }

    @Test
    fun metricTile_textPrimaryAndSecondaryOnOverlay_isAa() {
        assertAa(c.textPrimary, c.surfaceOverlay, "textPrimary/surfaceOverlay")
        assertAa(c.textSecondary, c.surfaceOverlay, "textSecondary/surfaceOverlay")
    }

    @Test
    fun ringAndBarFill_macroAndAmberVsOverlayTrack_atLeast3to1() {
        listOf(c.neonLime, c.athleticCyan, c.energyAmber).forEach { fill ->
            val ratio = contrastRatio(fill, c.surfaceOverlay)
            assertTrue(ratio >= NON_TEXT, "fill $fill vs track: $ratio < $NON_TEXT")
        }
    }

    @Test
    fun sliderThumb_macroColoursOnRaised_atLeast3to1() {
        listOf(c.macroProtein, c.macroCarbs, c.macroFat).forEach { thumb ->
            val ratio = contrastRatio(thumb, c.surfaceRaised)
            assertTrue(ratio >= NON_TEXT, "thumb $thumb vs raised: $ratio < $NON_TEXT")
        }
    }

    @Test
    fun numberInput_valueTextOnOverlay_isAa() {
        assertAa(c.textPrimary, c.surfaceOverlay, "textPrimary/surfaceOverlay")
    }

    @Test
    fun numberInput_focusBorderLimeVsOverlayAndRaised_atLeast3to1() {
        listOf(c.surfaceOverlay, c.surfaceRaised).forEach { bg ->
            val ratio = contrastRatio(c.neonLime, bg)
            assertTrue(ratio >= NON_TEXT, "lime vs $bg: $ratio < $NON_TEXT")
        }
    }

    @Test
    fun ghostData_onOverlayAndRaised_isSupportingOnly() {
        // Documents the carry-over: ghostData is above 3:1 but below AA, so it is never the only cue.
        listOf(c.surfaceOverlay, c.surfaceRaised).forEach { bg ->
            val ratio = contrastRatio(c.ghostData, bg)
            assertTrue(ratio >= NON_TEXT, "ghostData vs $bg: $ratio < $NON_TEXT")
            assertTrue(ratio < AA, "ghostData vs $bg: $ratio >= $AA, revisit the supporting-only rule")
        }
    }

    @Test
    fun setCheckbox_checkedLimeVsRaised_atLeast3to1() {
        val ratio = contrastRatio(c.neonLime, c.surfaceRaised)
        assertTrue(ratio >= NON_TEXT, "lime vs raised: $ratio < $NON_TEXT")
    }

    @Test
    fun setCheckbox_checkGlyphOnLime_isAa() {
        assertAa(c.onNeonLime, c.neonLime, "onNeonLime/neonLime")
    }

    @Test
    fun setCheckbox_uncheckedGhostGlyphOnOverlay_atLeast3to1() {
        val ratio = contrastRatio(c.ghostData, c.surfaceOverlay)
        assertTrue(ratio >= NON_TEXT, "ghost glyph vs overlay: $ratio < $NON_TEXT")
    }

    @Test
    fun setHeaderAndIndex_textSecondaryOnRaisedAndOverlay_isAa() {
        assertAa(c.textSecondary, c.surfaceRaised, "textSecondary/surfaceRaised")
        assertAa(c.textSecondary, c.surfaceOverlay, "textSecondary/surfaceOverlay")
    }

    @Test
    fun restTimer_cyanCountdownOnOverlay_isAa() {
        assertAa(c.athleticCyan, c.surfaceOverlay, "cyan/surfaceOverlay")
    }

    @Test
    fun restTimer_cyanLineVsBaseTrack_atLeast3to1() {
        val ratio = contrastRatio(c.athleticCyan, c.surfaceBase)
        assertTrue(ratio >= NON_TEXT, "cyan vs base track: $ratio < $NON_TEXT")
    }

    @Test
    fun restTimer_actionTextsOnRaised_isAa() {
        assertAa(c.textPrimary, c.surfaceRaised, "textPrimary/surfaceRaised")
        assertAa(c.textSecondary, c.surfaceRaised, "textSecondary/surfaceRaised")
    }

    @Test
    fun errorState_errorIconOnBaseAndRaised_isAa() {
        assertAa(c.error, c.surfaceBase, "error/surfaceBase")
        assertAa(c.error, c.surfaceRaised, "error/surfaceRaised")
    }

    @Test
    fun stateMessage_textColoursOnBaseAndRaised_isAa() {
        listOf(c.surfaceBase, c.surfaceRaised).forEach { bg ->
            assertAa(c.textPrimary, bg, "textPrimary")
            assertAa(c.textSecondary, bg, "textSecondary")
        }
    }

    @Test
    fun macroSlider_disabledThumbVsRaised_documented() {
        // Disabled controls are exempt from contrast; only opacity is asserted (no track showing through).
        val colors = MacroSliderDefaults.colors(Macro.Protein, enabled = false, appColors = c)
        assertTrue(colors.thumb.alpha == 1f && colors.activeTrack.alpha == 1f)
    }
}
