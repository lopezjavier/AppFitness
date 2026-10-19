package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.unit.dp

/** Spacing tokens: 8 dp grid with a 4 dp sub-grid (planning/05-design-system.md §4). */
object Spacing {
    /** No space: the default of optional extra padding parameters. */
    val none = 0.dp

    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp

    /** Screen side margin. */
    val screenMargin = lg

    /** Gap between cards in a group. */
    val cardGap = md

    /** Gap between functional blocks. */
    val blockGap = xl

    /** Card inner padding. */
    val cardPadding = lg

    /** Minimum height of a tappable row (the 48 dp touch target). */
    val minTouchTarget = 48.dp
}
