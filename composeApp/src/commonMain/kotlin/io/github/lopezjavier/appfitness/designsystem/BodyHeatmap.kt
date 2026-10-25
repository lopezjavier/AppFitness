package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import kotlin.math.ceil

/** One muscle of [BodyHeatmap]: its [sets], spoken [description] ("Pecho, 3 series"), [valueText] and [tag]. */
@Immutable
data class BodyRegion(
    val group: MuscleGroup,
    val sets: Double,
    val description: String,
    val valueText: String,
    val tag: String,
)

/** Heat steps and sizes of [BodyHeatmap]. */
object BodyHeatmapDefaults {
    /** Number of heat steps above zero (one per quartile of the maximum). */
    const val STEPS = 4
    val SilhouetteMaxWidth: Dp = 160.dp
    val MinRegionTarget: Dp = 48.dp
    val OutlineWidth: Dp = 1.dp

    /** Regions at least this wide (dp) show their value inside. */
    val ValueMinWidth: Dp = 28.dp

    /** Heat step of [sets] against [max]: 0 for nothing, 1..[STEPS] by quartile of the maximum. */
    fun step(sets: Double, max: Double): Int =
        if (sets <= 0.0 || max <= 0.0) 0 else ceil(sets / max * STEPS).toInt().coerceIn(1, STEPS)

    /** Region fill of a heat [step]: surfaceOverlay for 0, athleticCyan with one more alpha quarter per step. */
    fun color(appColors: AppColors, step: Int): Color =
        if (step <= 0) appColors.surfaceOverlay else appColors.athleticCyan.copy(alpha = step / STEPS.toFloat())

    /** Step from which the value text switches to the dark colour to keep AA on the bright cyan. */
    const val DARK_TEXT_FROM = 3
}

/**
 * Front and back silhouettes side by side; each [regions] entry is tinted by its sets (single hue, 4 steps) and is a
 * button reporting [onMuscleClick]. A region is a hit box of at least 48 dp; only the first polygon of a symmetric
 * group carries the semantics, the other still reacts to touch. Tags: `<tagPrefix>_front`, `<tagPrefix>_back` and
 * each region's [BodyRegion.tag].
 */
@Composable
fun BodyHeatmap(
    regions: List<BodyRegion>,
    onMuscleClick: (MuscleGroup) -> Unit,
    tagPrefix: String,
    modifier: Modifier = Modifier,
) {
    val max = regions.maxOfOrNull { it.sets } ?: 0.0
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        BodyView.entries.forEach { view ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                Silhouette(view, regions, max, onMuscleClick, Modifier.testTag("${tagPrefix}_${view.name.lowercase()}"))
            }
        }
    }
}

@Composable
private fun Silhouette(
    view: BodyView,
    regions: List<BodyRegion>,
    max: Double,
    onMuscleClick: (MuscleGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val shapes = BodyMapPaths.regions(view)
    BoxWithConstraints(
        modifier.widthIn(max = BodyHeatmapDefaults.SilhouetteMaxWidth).fillMaxWidth()
            .aspectRatio(BodyMapPaths.WIDTH / BodyMapPaths.HEIGHT),
    ) {
        val unit = maxWidth / BodyMapPaths.WIDTH
        Canvas(Modifier.fillMaxWidth().aspectRatio(BodyMapPaths.WIDTH / BodyMapPaths.HEIGHT)) {
            val scale = size.width / BodyMapPaths.WIDTH
            val outline = Stroke(BodyHeatmapDefaults.OutlineWidth.toPx())
            BodyMapPaths.outline().forEach { drawShape(it, scale, colors.surfaceBase, colors.borderSubtle, outline) }
            shapes.forEach { (group, polygons) ->
                val step = BodyHeatmapDefaults.step(regions.firstOrNull { it.group == group }?.sets ?: 0.0, max)
                polygons.forEach {
                    drawShape(it, scale, BodyHeatmapDefaults.color(colors, step), colors.borderSubtle, outline)
                }
            }
        }
        shapes.forEach { (group, polygons) ->
            val region = regions.firstOrNull { it.group == group } ?: return@forEach
            val step = BodyHeatmapDefaults.step(region.sets, max)
            // A group drawn on both sides keeps its plain tag on the front and a "_back" one on the back.
            val regionTag = if (view == BodyView.Back && group in BodyMapPaths.regions(BodyView.Front)) {
                "${region.tag}_back"
            } else {
                region.tag
            }
            polygons.forEachIndexed { index, polygon ->
                RegionHitBox(polygon, unit, region, step, index == 0, regionTag) { onMuscleClick(group) }
            }
        }
    }
}

private fun DrawScope.drawShape(points: List<Offset>, scale: Float, fill: Color, line: Color, stroke: Stroke) {
    val path = Path().apply {
        points.forEachIndexed { i, p ->
            if (i ==
                0
            ) {
                moveTo(p.x * scale, p.y * scale)
            } else {
                lineTo(p.x * scale, p.y * scale)
            }
        }
        close()
    }
    drawPath(path, fill, style = Fill)
    drawPath(path, line, style = stroke)
}

// Private helper: every input is one fact of the hit box.
@Suppress("LongParameterList")
@Composable
private fun RegionHitBox(
    polygon: List<Offset>,
    unit: Dp,
    region: BodyRegion,
    step: Int,
    primary: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    val left = polygon.minOf { it.x }
    val right = polygon.maxOf { it.x }
    val top = polygon.minOf { it.y }
    val bottom = polygon.maxOf { it.y }
    val width = unit * (right - left)
    val height = unit * (bottom - top)
    val boxWidth = maxOf(width, BodyHeatmapDefaults.MinRegionTarget)
    val boxHeight = maxOf(height, BodyHeatmapDefaults.MinRegionTarget)
    val semantics = if (primary) {
        Modifier.semantics { contentDescription = region.description }.testTag(tag)
    } else {
        Modifier.clearAndSetSemantics {}
    }
    Box(
        Modifier
            .offset(unit * left - (boxWidth - width) / 2, unit * top - (boxHeight - height) / 2)
            .size(boxWidth, boxHeight)
            .then(semantics)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (primary && region.sets > 0.0 && width >= BodyHeatmapDefaults.ValueMinWidth) {
            val dark = step >= BodyHeatmapDefaults.DARK_TEXT_FROM
            Text(
                text = region.valueText,
                style = AppTheme.typography.labelTechnical,
                color = if (dark) AppTheme.colors.surfaceBase else AppTheme.colors.textPrimary,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

/** Four heat swatches with [lessLabel] before and [moreLabel] after: the key of [BodyHeatmap]. */
@Composable
fun BodyHeatLegend(lessLabel: String, moreLabel: String, modifier: Modifier = Modifier) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(lessLabel, style = AppTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
        for (step in 1..BodyHeatmapDefaults.STEPS) {
            Box(Modifier.size(Spacing.lg).background(BodyHeatmapDefaults.color(AppTheme.colors, step), AppShapes.sm))
        }
        Text(moreLabel, style = AppTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
    }
}
