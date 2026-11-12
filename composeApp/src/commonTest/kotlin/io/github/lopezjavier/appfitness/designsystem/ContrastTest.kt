package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun plannedSetValues_textSecondaryOnTheFieldSurfaces_isAa_andLighterThanTyped() {
        assertAa(c.textSecondary, c.surfaceOverlay, "planned textSecondary/surfaceOverlay")
        assertAa(c.textSecondary, c.surfaceRaised, "planned textSecondary/surfaceRaised")
        assertTrue(NumberInputDefaults.PlaceholderWeight.weight < androidx.compose.ui.text.font.FontWeight.W400.weight)
    }

    @Test
    fun cardioTimerBar_limeAndAmberOnOverlay_areAa() {
        assertAa(c.neonLime, c.surfaceOverlay, "lime/surfaceOverlay")
        assertAa(c.energyAmber, c.surfaceOverlay, "amber/surfaceOverlay")
        assertAa(c.textSecondary, c.surfaceOverlay, "textSecondary/surfaceOverlay")
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
    fun disabledPrimary_labelOnContainer_isTheDocumentedExemption() {
        // Disabled controls are exempt from WCAG, but the figure of 05 section 2.1a (about 3.26:1) must hold.
        val disabled = AppButtonDefaults.colors(AppButtonVariant.Primary, enabled = false, appColors = c)
        val ratio = contrastRatio(disabled.content, disabled.container)
        assertEquals(3.26f, ratio, 0.1f)
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

    private val textSurfaces
        get() = mapOf(
            "base" to c.surfaceBase,
            "raised" to c.surfaceRaised,
            "overlay" to c.surfaceOverlay,
            "control" to c.surfaceControl,
        )

    @Test
    fun round0_textSecondaryAndTertiary_overEveryTextSurface_isAa() {
        textSurfaces.forEach { (name, surface) ->
            assertAa(c.textSecondary, surface, "textSecondary/$name")
            assertAa(c.textTertiary, surface, "textTertiary/$name")
        }
    }

    @Test
    fun round0_textTertiaryOnControl_keepsItsNarrowMargin() {
        // The placeholder on surfaceControl has about 0.15 of margin: darkening the token would break AA.
        assertTrue(contrastRatio(c.textTertiary, c.surfaceControl) < 4.8f)
    }

    @Test
    fun round0_selectedControl_labelAndBorderAreReadable() {
        assertAa(c.neonLime, c.selectedContainer, "neonLime/selectedContainer")
        assertTrue(contrastRatio(c.neonLime, c.surfaceBase) >= NON_TEXT, "border vs base")
        assertTrue(contrastRatio(c.neonLime, c.selectedContainer) >= NON_TEXT, "border vs fill")
    }

    @Test
    fun round0_selectedContainerFillAlone_failsNonText_soTheBorderCarriesTheState() {
        assertTrue(contrastRatio(c.selectedContainer, c.surfaceBase) < NON_TEXT)
    }

    @Test
    fun round0_borderControl_overTheControlSurfaces_atLeast3to1() {
        listOf(c.surfaceBase, c.surfaceRaised, c.surfaceOverlay, c.surfaceControl).forEach { bg ->
            val ratio = contrastRatio(c.borderControl, bg)
            assertTrue(ratio >= NON_TEXT, "borderControl vs $bg: $ratio < $NON_TEXT")
        }
    }

    @Test
    fun round0_trackInactive_isBelow3to1_soTheStopDotIsTextTertiary() {
        // Q1: the track alone is not a 3:1 graphic (value text and fill also carry the state, R-41).
        assertTrue(contrastRatio(c.trackInactive, c.surfaceRaised) < NON_TEXT)
        assertTrue(contrastRatio(c.textTertiary, c.surfaceRaised) >= NON_TEXT, "stop dot vs card")
        assertTrue(
            contrastRatio(c.textTertiary, c.trackInactive.compositeOver(c.surfaceRaised)) >= NON_TEXT,
            "stop dot vs track",
        )
    }

    /** US-46 accessibility: body text roles over every surface token (the app ships a single dark scheme). */
    @Test
    fun bodyText_textPrimaryAndSecondaryOverEverySurface_isAa() {
        mapOf("surfaceBase" to c.surfaceBase, "surfaceRaised" to c.surfaceRaised, "surfaceOverlay" to c.surfaceOverlay)
            .forEach { (name, surface) ->
                assertAa(c.textPrimary, surface, "textPrimary/$name")
                assertAa(c.textSecondary, surface, "textSecondary/$name")
            }
    }
}
