package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

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

    /** The optional glyph before the label. */
    val IconSize: Dp = 14.dp

    /** Corner light of a tile with an accent. */
    const val GLOW_REACH = 0.9f
    const val GLOW_ALPHA = 0.18f

    /** [containerColor] replaces the overlay fill, e.g. a raised tile; null keeps the default. */
    fun colors(appColors: AppColors, containerColor: Color? = null): MetricTileColors = MetricTileColors(
        container = containerColor ?: appColors.surfaceOverlay,
        border = appColors.borderStrong,
        label = appColors.textSecondary,
        value = appColors.textPrimary,
        unit = appColors.textSecondary,
    )

    fun labelStyle(typography: AppTypography): TextStyle = typography.labelTechnical

    fun valueStyle(typography: AppTypography): TextStyle = typography.headlineMetric

    fun unitStyle(typography: AppTypography): TextStyle = typography.bodyMedium

    /** The one step-down style of a value that does not fit (C-160); never lower than this. */
    fun compactValueStyle(typography: AppTypography): TextStyle = typography.titleLarge
}

/**
 * The value of a [MetricTile] or [MetricCard] (C-160): `headlineMetric` plus the optional [unit] as a
 * `bodyMedium` span joined by U+00A0, on one line. When it does not fit it steps down once to `titleLarge` and never
 * lower.
 */
@Composable
internal fun MetricValueText(
    value: String,
    unit: String?,
    valueColor: Color,
    unitColor: Color,
    modifier: Modifier = Modifier,
) {
    var compact by remember(value, unit) { mutableStateOf(false) }
    val typography = AppTheme.typography
    val valueStyle =
        if (compact) MetricTileDefaults.compactValueStyle(typography) else MetricTileDefaults.valueStyle(typography)
    val unitStyle = MetricTileDefaults.unitStyle(typography)
    val text = buildAnnotatedString {
        append(value)
        if (unit != null) {
            append(NBSP)
            withStyle(
                SpanStyle(
                    color = unitColor,
                    fontSize = unitStyle.fontSize,
                    fontWeight = unitStyle.fontWeight,
                    letterSpacing = unitStyle.letterSpacing,
                ),
            ) { append(unit) }
        }
    }
    Text(
        text = text,
        style = valueStyle,
        color = valueColor,
        maxLines = 1,
        softWrap = false,
        onTextLayout = { if (it.hasVisualOverflow && !compact) compact = true },
        modifier = modifier,
    )
}

private const val NBSP = ' '

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
    containerColor: Color? = null,
    accent: Color? = null,
    icon: DrawableResource? = null,
) {
    val colors = MetricTileDefaults.colors(AppTheme.colors, containerColor)
    val container = if (containerColor != null) SolidColor(containerColor) else AppPaint.tileSurface(AppTheme.colors)
    val border = if (accent != null) AppPaint.accentBorder(accent) else AppPaint.tileBorder()
    Column(
        modifier =
        modifier
            .semantics(mergeDescendants = true) {}
            .clip(MetricTileDefaults.Shape)
            .background(container)
            .then(
                if (accent != null) {
                    Modifier.drawBehind {
                        drawRadialGlow(
                            accent,
                            Offset(size.width, 0f),
                            size.width * MetricTileDefaults.GLOW_REACH,
                            MetricTileDefaults.GLOW_ALPHA,
                        )
                    }
                } else {
                    Modifier
                },
            )
            .border(MetricTileDefaults.BorderWidth, border, MetricTileDefaults.Shape)
            .padding(MetricTileDefaults.Padding),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            if (icon != null) {
                // Decorative: the label names the metric.
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = accent ?: colors.label,
                    modifier = Modifier.size(MetricTileDefaults.IconSize),
                )
            }
            Text(
                text = label.uppercase(),
                style = MetricTileDefaults.labelStyle(AppTheme.typography),
                color = colors.label,
                modifier = Modifier.semantics { contentDescription = label },
            )
        }
        MetricValueText(value = value, unit = unit, valueColor = colors.value, unitColor = colors.unit)
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
