package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Colours of [MetricTile]. */
@Immutable
data class MetricTileColors(
    val container: Color,
    val border: Color,
    val label: Color,
    val value: Color,
    val unit: Color,
)

object MetricTileDefaults {
    val Shape = AppShapes.md
    val Padding: Dp = Spacing.md
    val BorderWidth: Dp = AppBorder.thin

    fun colors(appColors: AppColors): MetricTileColors = MetricTileColors(
        container = appColors.surfaceOverlay,
        border = appColors.borderStrong,
        label = appColors.textSecondary,
        value = appColors.textPrimary,
        unit = appColors.textSecondary,
    )

    fun labelStyle(typography: AppTypography): TextStyle = typography.labelTechnical

    fun valueStyle(typography: AppTypography): TextStyle = typography.headlineMetric

    fun unitStyle(typography: AppTypography): TextStyle = typography.bodySmall
}

/**
 * Read-only tile with an uppercase [label], a [value] on one line (the tile grows vertically at large
 * font scales), an optional [unit] and an optional [supporting] slot (e.g. a TrendPill). [value] is
 * formatted by the caller.
 */
@Composable
fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    supporting: (@Composable () -> Unit)? = null,
) {
    val colors = MetricTileDefaults.colors(AppTheme.colors)
    Column(
        modifier =
        modifier
            .semantics(mergeDescendants = true) {}
            .clip(MetricTileDefaults.Shape)
            .background(colors.container)
            .border(MetricTileDefaults.BorderWidth, colors.border, MetricTileDefaults.Shape)
            .padding(MetricTileDefaults.Padding),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = label.uppercase(),
            style = MetricTileDefaults.labelStyle(AppTheme.typography),
            color = colors.label,
            modifier = Modifier.semantics { contentDescription = label },
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MetricTileDefaults.valueStyle(AppTheme.typography),
                color = colors.value,
                maxLines = 1,
                softWrap = false,
            )
            unit?.let {
                Text(
                    text = it,
                    style = MetricTileDefaults.unitStyle(AppTheme.typography),
                    color = colors.unit,
                    modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.xs),
                )
            }
        }
        supporting?.invoke()
    }
}

@Preview
@Composable
private fun MetricTilePreview() {
    AppTheme {
        Row(Modifier.padding(Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            MetricTile("Grasa estimada", "14,8", unit = "%")
            MetricTile("Meta objetivo", "72,0", unit = "kg", modifier = Modifier.width(160.dp))
        }
    }
}
