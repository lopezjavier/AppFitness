package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Colours of one segment state plus the [track] behind all segments. */
@Immutable
data class SegmentedSelectorColors(val track: Color, val segment: Color, val content: Color)

/** Sizes, colours and label style of [SegmentedSelector]. */
object SegmentedSelectorDefaults {
    val SegmentMinHeight: Dp = 48.dp
    val TrackPadding: Dp = Spacing.xs

    /** Horizontal padding inside a segment; labels are measured against the width left after it. */
    val SegmentPadding: Dp = Spacing.xs

    /**
     * Floor of the auto-sized label: the smallest type token (`labelTechnical`, 11 sp). Below it a label is
     * unreadable at arm's length in a gym, so past this size labels wrap to [OVERFLOW_MAX_LINES] lines.
     */
    val MinLabelFontSize: TextUnit = 11.sp

    /** Alpha of the whole control while disabled (for example during a save). */
    const val DISABLED_ALPHA = 0.38f

    /** Granularity of the shared label size search. */
    val LabelSizeStep: TextUnit = 0.5.sp

    /** Lines a label may use once [MinLabelFontSize] is reached and it still does not fit. */
    const val OVERFLOW_MAX_LINES = 2

    /**
     * Largest size from [maxSize] down to [minSize] (in [step] decrements) for which [fits] is true, or null
     * when none fits. One shared size keeps every label equal, so one long word never shrinks alone.
     */
    fun sharedLabelSize(maxSize: TextUnit, minSize: TextUnit, step: TextUnit, fits: (TextUnit) -> Boolean): TextUnit? {
        require(step.value > 0f) { "step must be positive" }
        var size = maxSize.value
        while (size > minSize.value) {
            if (fits(size.sp)) return size.sp
            size -= step.value
        }
        return minSize.takeIf { fits(it) }
    }

    /** Track radius 12 and segment radius 8 with a 4 dp gap between them: concentric corners. */
    val TrackShape = AppShapes.md
    val SegmentShape = AppShapes.base

    /**
     * The track is `surfaceRaised` with no border. If the selector ever sits inside an
     * [AppCard] (also `surfaceRaised`), use `surfaceBase` for the track instead.
     */
    fun colors(selected: Boolean, appColors: AppColors): SegmentedSelectorColors = if (selected) {
        SegmentedSelectorColors(appColors.surfaceRaised, appColors.neonLime, appColors.onNeonLime)
    } else {
        SegmentedSelectorColors(appColors.surfaceRaised, Color.Transparent, appColors.textSecondary)
    }

    /** Selected label is bold (W700): a non-colour cue. */
    fun labelStyle(base: TextStyle, selected: Boolean): TextStyle =
        if (selected) base.copy(fontWeight = FontWeight.W700) else base
}

/**
 * Equal-weight single-select control (for example Déficit / Mantenimiento / Superávit). The
 * selected segment is lime with dark bold text. All labels share one size: the largest (up to `bodyMedium`)
 * at which the widest label, measured in the selected weight, fits on one line. Below
 * [SegmentedSelectorDefaults.MinLabelFontSize] labels wrap to two lines (hyphenated on API 33+) and the segments
 * grow together; a label is never clipped. [selectedIndex] is null (no segment active) or valid for [options];
 * tapping the active segment does not call
 * [onSelect].
 *
 * Restriction: the control is built on `BoxWithConstraints` (it needs the available width to pick the label
 * size), which does not support intrinsic measurements. Do not place it inside a parent that queries
 * intrinsic sizes (for example `IntrinsicSize.Min/Max` containers); give it a bounded width instead.
 */
@Composable
fun SegmentedSelector(
    options: List<AppOption>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SingleSelection.requireValid(selectedIndex, options.size)
    val track = SegmentedSelectorDefaults.colors(selected = false, appColors = AppTheme.colors).track
    val baseStyle = AppTheme.typography.bodyMedium
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val segmentCount = options.size.coerceAtLeast(1)
        val gapPx = with(density) { SegmentedSelectorDefaults.TrackPadding.toPx() }
        val paddingPx = with(density) { SegmentedSelectorDefaults.SegmentPadding.toPx() }
        val contentWidthPx =
            (constraints.maxWidth - gapPx * (segmentCount + 1)) / segmentCount - paddingPx * 2
        val labels = options.map { it.label }
        // Measured in the selected weight (the widest), so selecting a segment never reflows the labels.
        val measureStyle = SegmentedSelectorDefaults.labelStyle(baseStyle, selected = true)
        val labelSize =
            remember(labels, contentWidthPx, density, measureStyle) {
                SegmentedSelectorDefaults.sharedLabelSize(
                    maxSize = baseStyle.fontSize,
                    minSize = SegmentedSelectorDefaults.MinLabelFontSize,
                    step = SegmentedSelectorDefaults.LabelSizeStep,
                ) { size ->
                    labels.all { label ->
                        measurer.measure(
                            text = label,
                            style = measureStyle.copy(fontSize = size),
                            softWrap = false,
                            maxLines = 1,
                        ).size.width <= contentWidthPx
                    }
                }
            }
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else SegmentedSelectorDefaults.DISABLED_ALPHA)
                .height(IntrinsicSize.Min)
                .clip(SegmentedSelectorDefaults.TrackShape)
                .background(track)
                .padding(SegmentedSelectorDefaults.TrackPadding)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(SegmentedSelectorDefaults.TrackPadding),
        ) {
            options.forEachIndexed { index, option ->
                Segment(
                    option = option,
                    selected = index == selectedIndex,
                    labelSize = labelSize,
                    enabled = enabled,
                    onClick = { SingleSelection.onTap(selectedIndex, index)?.let(onSelect) },
                )
            }
        }
    }
}

@Composable
private fun RowScope.Segment(
    option: AppOption,
    selected: Boolean,
    labelSize: TextUnit?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = SegmentedSelectorDefaults.colors(selected, AppTheme.colors)
    Box(
        modifier =
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .heightIn(min = SegmentedSelectorDefaults.SegmentMinHeight)
            .clip(SegmentedSelectorDefaults.SegmentShape)
            .background(colors.segment)
            .testTag(option.testTag)
            .semantics { option.contentDescription?.let { contentDescription = it } }
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = SegmentedSelectorDefaults.SegmentPadding),
        contentAlignment = Alignment.Center,
    ) {
        val base = SegmentedSelectorDefaults.labelStyle(AppTheme.typography.bodyMedium, selected)
        val fits = labelSize != null
        // Fits: one line at the shared size. Otherwise the floor size wraps to two lines, hyphenated where the
        // platform supports it; a label is never ellipsized or clipped.
        val style =
            base.copy(
                color = colors.content,
                textAlign = TextAlign.Center,
                fontSize = labelSize ?: SegmentedSelectorDefaults.MinLabelFontSize,
                hyphens = if (fits) Hyphens.None else Hyphens.Auto,
            )
        BasicText(
            text = option.label,
            style = style,
            maxLines = if (fits) 1 else SegmentedSelectorDefaults.OVERFLOW_MAX_LINES,
            softWrap = !fits,
        )
    }
}

@Preview
@Composable
private fun SegmentedSelectorPreview() {
    AppTheme {
        SegmentedSelector(
            options = listOf("Déficit", "Mantenimiento", "Superávit").map { AppOption(it, "segment_$it") },
            selectedIndex = 1,
            onSelect = {},
            modifier = Modifier.padding(Spacing.screenMargin),
        )
    }
}
