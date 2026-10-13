package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SegmentedSelectorTest {
    private val c = KineticDarkColors

    @Test
    fun segmentMinHeight_atLeast48() {
        assertTrue(SegmentedSelectorDefaults.SegmentMinHeight >= 48.dp)
    }

    @Test
    fun colors_selected_limeFillDarkText() {
        val colors = SegmentedSelectorDefaults.colors(selected = true, appColors = c)
        assertEquals(c.neonLime, colors.segment)
        assertEquals(c.onNeonLime, colors.content)
        assertEquals(c.surfaceRaised, colors.track)
    }

    @Test
    fun colors_unselected_transparentSecondaryText() {
        val colors = SegmentedSelectorDefaults.colors(selected = false, appColors = c)
        assertEquals(Color.Transparent, colors.segment)
        assertEquals(c.textSecondary, colors.content)
        assertEquals(c.surfaceRaised, colors.track)
    }

    @Test
    fun labelStyle_selectedIsBold_unselectedKeepsBase() {
        val base = appTypography(FontFamily.Default).bodyMedium
        assertEquals(FontWeight.W700, SegmentedSelectorDefaults.labelStyle(base, selected = true).fontWeight)
        assertEquals(base, SegmentedSelectorDefaults.labelStyle(base, selected = false))
    }

    @Test
    fun shapesAndPadding_areConcentric() {
        assertEquals(AppShapes.md, SegmentedSelectorDefaults.TrackShape)
        assertEquals(AppShapes.base, SegmentedSelectorDefaults.SegmentShape)
        assertEquals(Spacing.xs, SegmentedSelectorDefaults.TrackPadding)
    }

    @Test
    fun minLabelFontSize_isSmallestTypeToken() {
        val t = appTypography(FontFamily.Default)
        assertEquals(t.labelTechnical.fontSize, SegmentedSelectorDefaults.MinLabelFontSize)
        assertEquals(11.sp, SegmentedSelectorDefaults.MinLabelFontSize)
    }

    private fun shared(fitsAtMost: TextUnit?) = SegmentedSelectorDefaults.sharedLabelSize(
        maxSize = 14.sp,
        minSize = SegmentedSelectorDefaults.MinLabelFontSize,
        step = SegmentedSelectorDefaults.LabelSizeStep,
        fits = { size -> fitsAtMost != null && size.value <= fitsAtMost.value },
    )

    @Test
    fun sharedLabelSize_maxFits_returnsMax() = assertEquals(14.sp, shared(20.sp))

    @Test
    fun sharedLabelSize_shrinksToLargestFittingStep() = assertEquals(13.sp, shared(13.2.sp))

    @Test
    fun sharedLabelSize_minBoundary_returnsMin() = assertEquals(11.sp, shared(11.sp))

    @Test
    fun sharedLabelSize_noneFits_returnsNull() = assertNull(shared(10.sp))

    @Test
    fun sharedLabelSize_stepsInHalfSp() = assertEquals(12.5.sp, shared(12.7.sp))
}
