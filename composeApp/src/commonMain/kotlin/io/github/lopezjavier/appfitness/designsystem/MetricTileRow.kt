package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/** Font scale from which [MetricTileRow] stacks its tiles into one column (C-160, extends R-93). */
const val METRIC_TILE_STACK_FONT_SCALE = 1.3f

/**
 * Side-by-side [MetricTile]s of equal width and height (C-160). Each entry of [tiles] receives the modifier it must
 * apply to its tile (weight and height, or full width when stacked). At a font scale of
 * [METRIC_TILE_STACK_FONT_SCALE] or more the tiles stack into one column. [columns] above the tile count leaves
 * empty slots so a short last row keeps the width of its tiles.
 */
@Composable
fun MetricTileRow(
    tiles: List<@Composable (Modifier) -> Unit>,
    modifier: Modifier = Modifier,
    spacing: Dp = Spacing.sm,
    columns: Int = tiles.size,
) {
    if (LocalDensity.current.fontScale >= METRIC_TILE_STACK_FONT_SCALE) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing)) {
            tiles.forEach { it(Modifier.fillMaxWidth()) }
        }
    } else {
        Row(
            modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(spacing),
        ) {
            tiles.forEach { it(Modifier.weight(1f).fillMaxHeight()) }
            repeat(columns - tiles.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}
