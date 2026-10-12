package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class SpacingShapesElevationTest {
    @Test
    fun spacing_matchesSection4() {
        assertEquals(4.dp, Spacing.xs)
        assertEquals(8.dp, Spacing.sm)
        assertEquals(12.dp, Spacing.md)
        assertEquals(16.dp, Spacing.lg)
        assertEquals(24.dp, Spacing.xl)
        assertEquals(16.dp, Spacing.screenMargin)
        assertEquals(12.dp, Spacing.cardGap)
        assertEquals(24.dp, Spacing.blockGap)
        assertEquals(16.dp, Spacing.cardPadding)
    }

    @Test
    fun radii_andMaterialShapes_matchSection4() {
        assertEquals(4.dp, AppRadius.sm)
        assertEquals(8.dp, AppRadius.base)
        assertEquals(12.dp, AppRadius.md)
        assertEquals(16.dp, AppRadius.lg)
        assertEquals(24.dp, AppRadius.xl)

        assertEquals(RoundedCornerShape(4.dp), AppShapes.sm)
        assertEquals(RoundedCornerShape(8.dp), AppShapes.base)
        assertEquals(RoundedCornerShape(12.dp), AppShapes.md)
        assertEquals(RoundedCornerShape(16.dp), AppShapes.lg)
        assertEquals(RoundedCornerShape(24.dp), AppShapes.xl)
        assertEquals(CircleShape, AppShapes.full)
        assertEquals(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), AppShapes.sheetTop)

        val material = kineticMaterialShapes()
        assertEquals(RoundedCornerShape(4.dp), material.extraSmall)
        assertEquals(RoundedCornerShape(8.dp), material.small)
        assertEquals(RoundedCornerShape(12.dp), material.medium)
        assertEquals(RoundedCornerShape(16.dp), material.large)
        assertEquals(RoundedCornerShape(24.dp), material.extraLarge)
    }

    @Test
    fun elevationTokens_matchSection4() {
        assertEquals(12.dp, AppElevation.sheetShadowOffsetY)
        assertEquals(32.dp, AppElevation.sheetShadowBlur)
        assertEquals((-4).dp, AppElevation.sheetShadowSpread)
        assertEquals(16.dp, AppElevation.glowBlur)
    }
}
