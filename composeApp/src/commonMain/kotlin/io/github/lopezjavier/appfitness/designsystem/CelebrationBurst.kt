@file:Suppress("MatchingDeclarationName", "MagicNumber")
// MatchingDeclarationName: the file holds the celebration graphics and their Defaults objects.
// MagicNumber: proportions of the illustration (ray lengths, sparkle positions), relative to its size.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_check
import io.github.lopezjavier.appfitness.composeapp.resources.ic_star_filled
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/** Geometry of [CelebrationBurst]. */
object CelebrationBurstDefaults {
    val Size: Dp = 176.dp
    val Medallion: Dp = 80.dp
    val IconSize: Dp = 40.dp
    const val RAYS = 16
}

/** A sparkle of the burst: its angle (degrees), its distance and size as shares of the radius, and its colour slot. */
private class Sparkle(val angle: Float, val distance: Float, val size: Float, val slot: Int)

private val Sparkles = listOf(
    Sparkle(-62f, 0.86f, 0.085f, 0),
    Sparkle(-18f, 0.70f, 0.055f, 1),
    Sparkle(38f, 0.90f, 0.070f, 2),
    Sparkle(118f, 0.74f, 0.060f, 0),
    Sparkle(162f, 0.92f, 0.080f, 1),
    Sparkle(-136f, 0.78f, 0.065f, 2),
)

/**
 * The illustration of an achievement (a finished workout, a saved goal): a medallion in the ramp of [color] holding
 * the [icon], on a burst of fading rays, two thin rings and a few sparkles. It pops in once. Decorative: the title
 * beside it says what was achieved.
 */
@Composable
fun CelebrationBurst(
    icon: DrawableResource,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.neonLime,
    size: Dp = CelebrationBurstDefaults.Size,
) {
    val pop = rememberFillProgress()
    val colors = AppTheme.colors
    val sparkColors = listOf(colors.athleticCyan, AppPaint.Gold, colors.macroFat)
    val scale = size / CelebrationBurstDefaults.Size
    Box(modifier.size(size).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = this.size.minDimension / 2f
            val grow = 0.6f + 0.4f * pop.value
            drawRadialGlow(color, center, radius, alpha = 0.26f * pop.value)
            drawRays(color, radius, grow)
            drawCircle(color.copy(alpha = 0.22f), radius * 0.60f * grow, style = Stroke(AppBorder.thin.toPx()))
            drawCircle(color.copy(alpha = 0.10f), radius * 0.80f * grow, style = Stroke(AppBorder.thin.toPx()))
            Sparkles.forEach { sparkle ->
                val angle = sparkle.angle * PI / 180.0
                val at = Offset(
                    center.x + radius * sparkle.distance * grow * cos(angle).toFloat(),
                    center.y + radius * sparkle.distance * grow * sin(angle).toFloat(),
                )
                drawSparkle(at, radius * sparkle.size, sparkColors[sparkle.slot].copy(alpha = pop.value))
            }
        }
        val medallion = CelebrationBurstDefaults.Medallion * scale
        Box(
            Modifier
                .size(medallion)
                .graphicsLayer {
                    scaleX = 0.8f + 0.2f * pop.value
                    scaleY = 0.8f + 0.2f * pop.value
                }
                .softGlow(color, cornerRadius = medallion / 2, radius = 16.dp, alpha = 0.36f, offsetY = 0.dp)
                .clip(AppShapes.full)
                .background(AppPaint.diagonal(color))
                .border(
                    AppBorder.focus,
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.65f), Color.White.copy(alpha = 0.08f))),
                    AppShapes.full,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = colors.onNeonLime,
                modifier = Modifier.size(CelebrationBurstDefaults.IconSize * scale),
            )
        }
    }
}

private fun DrawScope.drawRays(color: Color, radius: Float, grow: Float) {
    val inner = radius * 0.52f
    repeat(CelebrationBurstDefaults.RAYS) { index ->
        val angle = 2 * PI * index / CelebrationBurstDefaults.RAYS - PI / 2
        val long = index % 2 == 0
        val outer = radius * (if (long) 0.96f else 0.76f) * grow
        val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
        val start = center + direction * inner
        val end = center + direction * outer
        drawLine(
            brush = Brush.linearGradient(listOf(color.copy(alpha = 0.6f), color.copy(alpha = 0f)), start, end),
            start = start,
            end = end,
            strokeWidth = (if (long) 2.5.dp else 1.5.dp).toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/** A four-point star of half-size [radius] at [center]. */
private fun DrawScope.drawSparkle(center: Offset, radius: Float, color: Color) {
    val waist = radius * 0.28f
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        lineTo(center.x + waist, center.y - waist)
        lineTo(center.x + radius, center.y)
        lineTo(center.x + waist, center.y + waist)
        lineTo(center.x, center.y + radius)
        lineTo(center.x - waist, center.y + waist)
        lineTo(center.x - radius, center.y)
        lineTo(center.x - waist, center.y - waist)
        close()
    }
    drawPath(path, color)
}

/** Geometry of [RecordBadge]. */
object RecordBadgeDefaults {
    val MinHeight: Dp = 28.dp
    val IconSize: Dp = 14.dp
}

/**
 * The badge of a personal record: a gold star and the [text] ("Récord de peso") on a gold-tinted pill. The star and
 * the words carry the meaning; the gold only dresses it. One accessibility node that reads the text.
 */
@Composable
fun RecordBadge(text: String, modifier: Modifier = Modifier) {
    val gold = AppPaint.Gold
    Row(
        modifier
            .heightIn(min = RecordBadgeDefaults.MinHeight)
            .clip(AppShapes.full)
            .background(Brush.horizontalGradient(listOf(gold.copy(alpha = 0.24f), gold.copy(alpha = 0.08f))))
            .border(
                AppBorder.thin,
                Brush.verticalGradient(listOf(gold.copy(alpha = 0.75f), gold.copy(alpha = 0.2f))),
                AppShapes.full,
            )
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_star_filled),
            contentDescription = null,
            tint = gold,
            modifier = Modifier.size(RecordBadgeDefaults.IconSize),
        )
        Text(
            text = text,
            style = AppTheme.typography.bodySmall.copy(fontWeight = FontWeight.W700),
            color = lerp(gold, Color.White, 0.35f),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Geometry of [ActivityHeatmap]. */
object ActivityHeatmapDefaults {
    const val ROWS = 7
    const val MAX_LEVEL = 4
    val Gap: Dp = 2.dp
    val MaxCell: Dp = 14.dp
    val Corner: Dp = 3.dp
}

/**
 * A contribution grid: one small square per day, weeks as columns and the seven weekdays as rows. [levels] holds one
 * entry per day in order: 0 is a day with nothing, 1 to 4 a rising intensity of [color], null leaves the cell out. The
 * first day sits on row [startRow] (0 is the first weekday). The cell at [highlightIndex] (today) gets an outline.
 * The cells size themselves to the width, up to 14 dp. Decorative: the caller writes the totals as text.
 */
@Composable
fun ActivityHeatmap(
    levels: List<Int?>,
    modifier: Modifier = Modifier,
    startRow: Int = 0,
    color: Color = AppTheme.colors.neonLime,
    highlightIndex: Int? = null,
) {
    val empty = AppTheme.colors.trackInactive.copy(alpha = 0.45f)
    val outline = AppTheme.colors.textPrimary
    val rows = ActivityHeatmapDefaults.ROWS
    val columns = ceil((startRow + levels.size) / rows.toFloat()).toInt().coerceAtLeast(1)
    BoxWithConstraints(modifier.fillMaxWidth().clearAndSetSemantics {}) {
        val gap = ActivityHeatmapDefaults.Gap
        val cell = minOf((maxWidth - gap * (columns - 1)) / columns, ActivityHeatmapDefaults.MaxCell)
        val width = cell * columns + gap * (columns - 1)
        val height = cell * rows + gap * (rows - 1)
        Canvas(Modifier.size(width, height)) {
            val cellPx = cell.toPx()
            val step = cellPx + gap.toPx()
            val corner = CornerRadius(minOf(ActivityHeatmapDefaults.Corner.toPx(), cellPx / 2f))
            levels.forEachIndexed { index, level ->
                if (level == null) return@forEachIndexed
                val slot = startRow + index
                val topLeft = Offset((slot / rows) * step, (slot % rows) * step)
                val clamped = level.coerceIn(0, ActivityHeatmapDefaults.MAX_LEVEL)
                val fill = if (clamped == 0) {
                    empty
                } else {
                    color.copy(alpha = 0.22f + 0.78f * clamped / ActivityHeatmapDefaults.MAX_LEVEL)
                }
                drawRoundRect(fill, topLeft, Size(cellPx, cellPx), corner)
                if (index == highlightIndex) {
                    drawRoundRect(outline, topLeft, Size(cellPx, cellPx), corner, style = Stroke(AppBorder.thin.toPx()))
                }
            }
        }
    }
}

@Preview
@Composable
private fun CelebrationBurstPreview() {
    AppTheme {
        Box(Modifier.background(AppTheme.colors.surfaceBase).padding(Spacing.lg)) {
            CelebrationBurst(Res.drawable.ic_check)
        }
    }
}

@Preview
@Composable
private fun RecordBadgePreview() {
    AppTheme { RecordBadge("Récord de peso") }
}

@Preview
@Composable
private fun ActivityHeatmapPreview() {
    AppTheme {
        @Suppress("MagicNumber")
        ActivityHeatmap(List(120) { (it * 7 + it / 3) % 5 }, highlightIndex = 119)
    }
}
