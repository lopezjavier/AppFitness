@file:Suppress("MatchingDeclarationName", "ktlint:standard:property-naming") // Defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_check
import org.jetbrains.compose.resources.painterResource

/** Geometry of [SegmentedProgress] and [SegmentedProgressPicker] (C-101). */
object SegmentedProgressDefaults {
    val Height: Dp = 8.dp
    val Gap: Dp = 4.dp

    /** Above this many segments the bar falls back to [AppLinearProgress]. */
    const val MaxSegments: Int = 12

    /** Visible height of a [SegmentedProgressPicker] tab (inside its 48 dp touch area). */
    val ButtonHeight: Dp = 36.dp

    /** Tabs are squared: 8 dp corners at most. */
    val TabShape = RoundedCornerShape(8.dp)

    /**
     * Up to this many tabs share the width (no scroll at 360 dp and font scale 1.3); more scroll at [ScrollTabWidth].
     */
    const val MaxTabs: Int = 6
    val ScrollTabWidth: Dp = 56.dp

    /** How much of the next tab shows at the edge of a scrolling row, so the scroll is evident. */
    val PeekWidth: Dp = 24.dp

    /** The done tab's Outlined check and its gap to the label. */
    val TickSize: Dp = 16.dp
    val TickGap: Dp = 2.dp

    /** The chosen step's indicator under its button: 3 dp high, 40 % of the button wide, 4 dp below. */
    val IndicatorHeight: Dp = 3.dp
    val IndicatorGap: Dp = 4.dp
    const val IndicatorWidthFraction: Float = 0.4f

    fun segmentTag(testTag: String, index: Int): String = "${testTag}_segment_$index"

    /** Tag of the picker item of the 1-based [step]. */
    fun itemTag(testTag: String, step: Int): String = "${testTag}_item_$step"
}

/**
 * Step progress as [total] rounded segments: the first [done] are `athleticCyan`, the one at 1-based [current] (when
 * given) is a track segment with a 1.5 dp cyan border, the rest are `trackInactive`. Above 12 segments it falls back
 * to [AppLinearProgress]. [stateDescription] is the spoken value ("Semana 2 de 4"); segments are decorative.
 * Segments fill once on first composition (R-97); [staggerOffset] shifts the 40 ms stagger of the first segment.
 */
@Composable
fun SegmentedProgress(
    total: Int,
    done: Int,
    stateDescription: String,
    modifier: Modifier = Modifier,
    current: Int? = null,
    testTag: String = "segmented_progress",
    staggerOffset: Int = 0,
) {
    if (total > SegmentedProgressDefaults.MaxSegments) {
        AppLinearProgress(
            fraction = if (total > 0) done.toFloat() / total else 0f,
            stateDescription = stateDescription,
            modifier = modifier.testTag(testTag),
        )
        return
    }
    Row(
        modifier
            .fillMaxWidth()
            .height(SegmentedProgressDefaults.Height)
            .testTag(testTag)
            .semantics(mergeDescendants = true) { this.stateDescription = stateDescription },
        horizontalArrangement = Arrangement.spacedBy(SegmentedProgressDefaults.Gap),
    ) {
        repeat(total) { index ->
            SegmentBar(
                isDone = index < done,
                isCurrent = index >= done && current == index + 1,
                staggerIndex = staggerOffset + index,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag(SegmentedProgressDefaults.segmentTag(testTag, index)),
            )
        }
    }
}

/**
 * Week tabs that are the progress line itself (R-106): equal-width squared tabs (8 dp corners, 4 dp apart, 36 dp
 * visible inside a 48 dp touch area, `Role.Tab`) with a short [label] ("S2"). The look comes from the step's state,
 * never from an animation: a done step is filled `athleticCyan` from the first frame with an Outlined check and its
 * label in `onNeonLime` (about 8.9:1); the [current] step is the dark track with a 1.5 dp cyan outline and a
 * `textPrimary` label (about 8.5:1 over the track on `surfaceRaised`); an upcoming step is the track with a
 * `textSecondary` label (about 5.1:1). The [selected] step adds a bold label and a 3 dp `textPrimary` bar under its tab
 * (shape and weight, never colour alone). Up to [SegmentedProgressDefaults.MaxTabs] steps share the full width (no
 * scroll at 360 dp and font scale 1.3); above that each tab is about 56 dp wide and the row scrolls with the last
 * visible tab cut by 24 dp (so the scroll is evident), brought to the
 * selection on first show. [description] is each step's spoken label ("Semana 2, actual"); [stateDescription] the
 * row's. Nothing animates, so a new selection replays nothing (R-97). Items are tagged `<testTag>_item_<step>`.
 */
@Composable
@Suppress("LongParameterList") // The progress figures, the selection and the copy of each step.
fun SegmentedProgressPicker(
    total: Int,
    done: Int,
    selected: Int,
    onSelect: (Int) -> Unit,
    label: (Int) -> String,
    description: (Int) -> String,
    stateDescription: String,
    modifier: Modifier = Modifier,
    current: Int? = null,
    testTag: String = "segmented_progress_picker",
) {
    if (total <= 0) return
    val shared = total <= SegmentedProgressDefaults.MaxTabs
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    var selectedX by remember { mutableStateOf<Int?>(null) }
    // A long block: bring the chosen tab into view once; later scrolling is the user's own.
    var scrolledOnce by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // The last visible tab is cut by about 24 dp, so the row visibly continues (R-106).
        val tabWidth = scrollTabWidth(maxWidth)
        LaunchedEffect(selectedX, tabWidth) {
            val x = selectedX
            if (!shared && !scrolledOnce && x != null) {
                scroll.scrollTo((x - with(density) { tabWidth.roundToPx() }).coerceAtLeast(0))
                scrolledOnce = true
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .testTag(testTag)
                .let { if (shared) it else it.horizontalScroll(scroll) }
                .semantics { this.stateDescription = stateDescription },
            horizontalArrangement = Arrangement.spacedBy(SegmentedProgressDefaults.Gap),
        ) {
            repeat(total) { index ->
                val step = index + 1
                val state = when {
                    index < done -> StepState.Done
                    current == step -> StepState.Current
                    else -> StepState.Upcoming
                }
                val tabModifier = if (shared) Modifier.weight(1f) else Modifier.width(tabWidth)
                WeekTab(
                    label = label(step),
                    state = state,
                    selected = selected == step,
                    description = description(step),
                    onSelect = { onSelect(step) },
                    modifier = tabModifier
                        .onPlaced { if (selected == step) selectedX = it.positionInParent().x.toInt() }
                        .testTag(SegmentedProgressDefaults.itemTag(testTag, step)),
                )
            }
        }
    }
}

/**
 * The width of a scrolling tab: whole tabs of about [SegmentedProgressDefaults.ScrollTabWidth] that fit [available],
 * stretched so a further tab shows by [SegmentedProgressDefaults.PeekWidth] at the edge.
 */
private fun scrollTabWidth(available: Dp): Dp {
    val gap = SegmentedProgressDefaults.Gap
    val peek = SegmentedProgressDefaults.PeekWidth
    val whole = ((available - peek + gap) / (SegmentedProgressDefaults.ScrollTabWidth + gap)).toInt().coerceAtLeast(1)
    // whole * (w + gap) + peek = available: the next tab starts [peek] before the edge.
    return ((available - peek) / whole - gap).coerceAtLeast(SegmentedProgressDefaults.ScrollTabWidth)
}

private enum class StepState { Done, Current, Upcoming }

@Composable
@Suppress("LongParameterList") // One tab: its state, its copy and its action.
private fun WeekTab(
    label: String,
    state: StepState,
    selected: Boolean,
    description: String,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val shape = SegmentedProgressDefaults.TabShape
    // Colours follow the state only: the fill is drawn in full, so the label always sits on the colour chosen for it.
    val (container, content) = when (state) {
        StepState.Done -> colors.athleticCyan to colors.onNeonLime
        StepState.Current -> colors.trackInactive to colors.textPrimary
        StepState.Upcoming -> colors.trackInactive to colors.textSecondary
    }
    Column(
        modifier
            .heightIn(min = Spacing.minTouchTarget)
            .selectable(selected = selected, role = Role.Tab, onClick = onSelect)
            .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SegmentedProgressDefaults.IndicatorGap),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(SegmentedProgressDefaults.ButtonHeight)
                .clip(shape)
                .background(container)
                .let { if (state == StepState.Current) it.border(AppBorder.focus, colors.athleticCyan, shape) else it }
                .padding(horizontal = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(
                SegmentedProgressDefaults.TickGap,
                Alignment.CenterHorizontally,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state == StepState.Done) {
                Icon(
                    painter = painterResource(Res.drawable.ic_check),
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(SegmentedProgressDefaults.TickSize),
                )
            }
            Text(
                text = label,
                style = if (selected) {
                    AppTheme.typography.bodySmall.copy(fontWeight = FontWeight.W700)
                } else {
                    AppTheme.typography.bodySmall
                },
                color = content,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
        Box(
            Modifier
                .fillMaxWidth(SegmentedProgressDefaults.IndicatorWidthFraction)
                .height(SegmentedProgressDefaults.IndicatorHeight)
                .clip(AppShapes.full)
                .background(if (selected) colors.textPrimary else Color.Transparent),
        )
    }
}

/** One rounded segment: cyan fill when done (fills once, R-97), a cyan 1.5 dp outline when current, else the track. */
@Composable
private fun SegmentBar(isDone: Boolean, isCurrent: Boolean, staggerIndex: Int, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val fill = rememberFillProgress(staggerIndex)
    Box(
        modifier
            .clip(AppShapes.full)
            .background(colors.trackInactive)
            .let { if (isCurrent) it.border(AppBorder.focus, colors.athleticCyan, AppShapes.full) else it }
            .clearAndSetSemantics {},
    ) {
        if (isDone) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fill.value)
                    .background(colors.athleticCyan),
            )
        }
    }
}

@Preview
@Composable
private fun SegmentedProgressPreview() {
    AppTheme { SegmentedProgress(total = 4, done = 1, current = 2, stateDescription = "Semana 2 de 4") }
}

@Preview
@Composable
private fun SegmentedProgressPickerPreview() {
    AppTheme {
        SegmentedProgressPicker(
            total = 4,
            done = 1,
            current = 2,
            selected = 1,
            onSelect = {},
            label = { "S$it" },
            description = { "Semana $it" },
            stateDescription = "Semana 2 de 4",
        )
    }
}
