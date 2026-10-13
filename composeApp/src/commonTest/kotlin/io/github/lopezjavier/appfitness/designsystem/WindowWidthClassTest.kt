package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class WindowWidthClassTest {
    @Test
    fun windowWidthClassFor_boundary599IsCompact600IsWide() {
        assertEquals(WindowWidthClass.Compact, windowWidthClassFor(599.dp))
        assertEquals(WindowWidthClass.Wide, windowWidthClassFor(600.dp))
    }

    @Test
    fun windowWidthClassFor_fractionalBelowBoundaryIsCompact() {
        assertEquals(WindowWidthClass.Compact, windowWidthClassFor(599.9.dp))
    }

    @Test
    fun windowWidthClassFor_extremes() {
        assertEquals(WindowWidthClass.Compact, windowWidthClassFor(0.dp))
        assertEquals(WindowWidthClass.Compact, windowWidthClassFor(360.dp))
        assertEquals(WindowWidthClass.Wide, windowWidthClassFor(1280.dp))
    }

    @Test
    fun mapping_navigationTypeAndTypeScale() {
        assertEquals(AppNavigationType.Bar, WindowWidthClass.Compact.navigationType)
        assertEquals(AppNavigationType.Rail, WindowWidthClass.Wide.navigationType)
        assertEquals(TypeScale.Phone, WindowWidthClass.Compact.typeScale)
        assertEquals(TypeScale.Tablet, WindowWidthClass.Wide.typeScale)
    }
}
