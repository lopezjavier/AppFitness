package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class PillTest {
    @Test
    fun minHeight32() = assertEquals(32.dp, PillDefaults.MinHeight)

    @Test
    fun shape_isFull() = assertEquals(AppShapes.full, PillDefaults.Shape)

    @Test
    fun container_isOverlay() =
        assertEquals(KineticDarkColors.surfaceOverlay, PillDefaults.containerColor(KineticDarkColors))

    @Test
    fun padding_matchesTrendPill() {
        assertEquals(Spacing.md, PillDefaults.HorizontalPadding)
        assertEquals(Spacing.xs, PillDefaults.VerticalPadding)
    }

    @Test
    fun trendPill_usesSharedPill() {
        assertEquals(PillDefaults.MinHeight, TrendPillDefaults.MinHeight)
        assertEquals(PillDefaults.Shape, TrendPillDefaults.Shape)
    }
}
