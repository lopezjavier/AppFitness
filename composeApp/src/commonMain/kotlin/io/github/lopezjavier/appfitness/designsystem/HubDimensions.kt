package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Fixed sizes of the hub roots (Trainer root skeleton, empty-state glyph, list rows), kept out of the features. */
object HubDimensions {
    /** Height of one row of the root links group. */
    val RootRow = 56.dp

    /** Diameter of the disc behind an empty-state glyph. */
    val EmptyGlyphCircle = 72.dp

    /** Skeleton height of the first (plan) hub card. */
    val PlanCardSkeleton = 218.dp

    /** Skeleton height of the second (block) hub card. */
    val BlockCardSkeleton = 206.dp

    /** Minimum card height when two hub cards share a row. */
    val WideCardMin = 220.dp
}

/** [AppTypography.bodyMedium] in semibold, for the figure of a key-value line. */
val AppTypography.bodyMediumStrong: TextStyle
    get() = bodyMedium.copy(fontWeight = FontWeight.W600)
