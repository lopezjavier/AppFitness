package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val MinLabelFontSize = 8.sp

/** Colours of one segment state plus the [track] behind all segments. */
@Immutable
data class SegmentedSelectorColors(val track: Color, val segment: Color, val content: Color)

/** Sizes, colours and label style of [SegmentedSelector]. */
object SegmentedSelectorDefaults {
    val SegmentMinHeight: Dp = 48.dp
    val TrackPadding: Dp = Spacing.xs

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
 * selected segment is lime with dark bold text. Labels stay on one line and shrink (down to 8 sp) instead of wrapping.
 * [selectedIndex] must be valid for [options]; tapping the active segment does not call [onSelect].
 */
@Composable
fun SegmentedSelector(
    options: List<AppOption>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleSelection.requireValid(selectedIndex, options.size)
    val track = SegmentedSelectorDefaults.colors(selected = false, appColors = AppTheme.colors).track
    Row(
        modifier =
        modifier
            .fillMaxWidth()
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
                onClick = { SingleSelection.onTap(selectedIndex, index)?.let(onSelect) },
            )
        }
    }
}

@Composable
private fun RowScope.Segment(option: AppOption, selected: Boolean, onClick: () -> Unit) {
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
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        val style = SegmentedSelectorDefaults.labelStyle(AppTheme.typography.bodyMedium, selected)
        // One line, shrinking to fit: a label never breaks inside a word.
        BasicText(
            text = option.label,
            style = style.copy(color = colors.content, textAlign = TextAlign.Center),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = MinLabelFontSize, maxFontSize = style.fontSize),
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
