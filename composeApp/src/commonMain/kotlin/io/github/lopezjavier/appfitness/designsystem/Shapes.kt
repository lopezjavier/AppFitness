package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Corner radius tokens (planning/05-design-system.md §4). */
object AppRadius {
    val sm = 4.dp

    /** Inputs, rows. */
    val base = 8.dp
    val md = 12.dp

    /** Cards. */
    val lg = 16.dp

    /** Bottom sheet top corners. */
    val xl = 24.dp
}

/** Shapes built from [AppRadius]. */
object AppShapes {
    val sm = RoundedCornerShape(AppRadius.sm)
    val base = RoundedCornerShape(AppRadius.base)
    val md = RoundedCornerShape(AppRadius.md)
    val lg = RoundedCornerShape(AppRadius.lg)
    val xl = RoundedCornerShape(AppRadius.xl)

    /** Chips, pills, round checkbox. */
    val full = CircleShape

    /** Bottom sheet: only the top corners are rounded. */
    val sheetTop = RoundedCornerShape(topStart = AppRadius.xl, topEnd = AppRadius.xl)
}

/** Material 3 shapes aligned with [AppShapes], so stock components use the same radii. */
internal fun kineticMaterialShapes(): Shapes = Shapes(
    extraSmall = AppShapes.sm,
    small = AppShapes.base,
    medium = AppShapes.md,
    large = AppShapes.lg,
    extraLarge = AppShapes.xl,
)
