@file:Suppress("MatchingDeclarationName") // The file holds the WheelPicker composable and its Defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.round

/** Sizes and rules of [WheelPicker] (planning/05-design-system.md §6). */
object WheelPickerDefaults {
    /** Height of one item, also the touch target of a tap-to-select. */
    val ItemHeight: Dp = 48.dp

    /** Items visible at once: the centred one plus two on each side. */
    const val VISIBLE_ITEMS = 5

    /** Items shown beyond the centred one on each side. */
    const val ITEMS_PER_SIDE = VISIBLE_ITEMS / 2

    val MinWidth: Dp = 72.dp

    /** Default upper bound, so a wheel in an unweighted row leaves room for its siblings. */
    val MaxWidth: Dp = 96.dp

    /** Width of a one-digit wheel such as the weight decimals. */
    val CompactWidth: Dp = 48.dp

    /** Below this available width two wheel groups no longer fit side by side and stack vertically. */
    val SideBySideMinWidth: Dp = 320.dp

    /** The band behind the centred item; the same shape as inputs and rows. */
    val BandShape = AppShapes.base

    /** Height of the whole wheel. */
    val WheelHeight: Dp = ItemHeight * VISIBLE_ITEMS

    /** Index of the item nearest the centre for a scroll position (first visible item and its pixel offset). */
    fun centredIndex(firstVisibleIndex: Int, firstVisibleOffsetPx: Int, itemHeightPx: Int, lastIndex: Int): Int {
        val rounded = if (firstVisibleOffsetPx * 2 > itemHeightPx) firstVisibleIndex + 1 else firstVisibleIndex
        return rounded.coerceIn(0, lastIndex)
    }

    /** Colour of an item [distance] items away from the centre: centred is primary, then secondary, then ghost. */
    fun itemColor(distance: Int, appColors: AppColors): Color = when (distance) {
        0 -> appColors.textPrimary
        1 -> appColors.textSecondary
        else -> appColors.ghostData
    }

    /** Clamps a requested [progress] (as TalkBack or a test sends it) to [range]. */
    fun clampProgress(progress: Float, range: IntRange): Int = round(progress).toInt().coerceIn(range.first, range.last)
}

/**
 * Vertical snapping wheel over [range]. [value] is the selected number; [onValueChange] is called when the
 * wheel settles on another item, when an item is tapped and when assistive technology sets a progress.
 * An external [value] change scrolls the wheel. [label] renders an item; [contentDescription] names the field
 * and [stateDescription] speaks the selected value with its unit. Items are hidden from accessibility: only the
 * wheel node is exposed, with a range and a clamping set-progress action. Test tags: [testTagPrefix] on the
 * wheel and `<prefix>_item_<value>` on each item.
 */
@Suppress("LongParameterList")
@Composable
fun WheelPicker(
    range: IntRange,
    value: Int,
    onValueChange: (Int) -> Unit,
    label: (Int) -> String,
    contentDescription: String,
    stateDescription: (Int) -> String,
    testTagPrefix: String,
    modifier: Modifier = Modifier,
) {
    val count = range.last - range.first + 1
    val selected = value.coerceIn(range)
    val selectedIndex = selected - range.first
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val itemHeightPx =
        with(LocalDensity.current) { WheelPickerDefaults.ItemHeight.roundToPx() }
    val centredIndex by remember(listState, count, itemHeightPx) {
        derivedStateOf {
            WheelPickerDefaults.centredIndex(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
                itemHeightPx,
                count - 1,
            )
        }
    }
    val stateText = stateDescription(selected)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val scope = rememberCoroutineScope()
    WheelSync(listState, range, selected, { centredIndex }) { currentOnValueChange(it) }

    Box(
        modifier = modifier
            .widthIn(min = WheelPickerDefaults.MinWidth, max = WheelPickerDefaults.MaxWidth)
            .height(WheelPickerDefaults.WheelHeight),
        contentAlignment = Alignment.Center,
    ) {
        SelectionBand()
        WheelList(
            range = range,
            state = listState,
            centredIndex = centredIndex,
            label = label,
            testTagPrefix = testTagPrefix,
            onItemTap = { index ->
                currentOnValueChange(range.first + index)
                scope.launch { listState.animateScrollToItem(index) }
            },
            modifier = Modifier.semantics {
                this.contentDescription = contentDescription
                this.stateDescription = stateText
                progressBarRangeInfo =
                    ProgressBarRangeInfo(selected.toFloat(), range.first.toFloat()..range.last.toFloat(), count - 2)
                setProgress { target ->
                    val clamped = WheelPickerDefaults.clampProgress(target, range)
                    currentOnValueChange(clamped)
                    true
                }
            }.testTag(testTagPrefix),
        )
    }
}

/**
 * Keeps the list and the value in step: reports the item the wheel settled on, and scrolls the wheel when the
 * value (or the range) changes from outside, as with a prefill, a clamp or an accessibility action.
 */
@Composable
private fun WheelSync(
    listState: LazyListState,
    range: IntRange,
    selected: Int,
    centredIndex: () -> Int,
    onSettle: (Int) -> Unit,
) {
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSettle by rememberUpdatedState(onSettle)
    val currentCentredIndex by rememberUpdatedState(centredIndex)
    LaunchedEffect(listState, range) {
        snapshotFlow { if (listState.isScrollInProgress) null else currentCentredIndex() }
            .filter { it != null }
            .map { range.first + it!! }
            .distinctUntilChanged()
            .collect { settled -> if (settled != currentSelected) currentOnSettle(settled) }
    }
    LaunchedEffect(selected, range) {
        val index = selected - range.first
        if (!listState.isScrollInProgress && currentCentredIndex() != index) listState.scrollToItem(index)
    }
}

@Suppress("LongParameterList")
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelList(
    range: IntRange,
    state: LazyListState,
    centredIndex: Int,
    label: (Int) -> String,
    testTagPrefix: String,
    onItemTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = state,
        contentPadding = PaddingValues(vertical = WheelPickerDefaults.ItemHeight * WheelPickerDefaults.ITEMS_PER_SIDE),
        flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Center),
    ) {
        items(range.last - range.first + 1) { index ->
            val itemValue = range.first + index
            val distance = abs(index - centredIndex)
            Box(
                modifier = Modifier.fillMaxWidth().height(WheelPickerDefaults.ItemHeight)
                    .pointerInput(index) { detectTapGestures { onItemTap(index) } }
                    .clearAndSetSemantics { testTag = "${testTagPrefix}_item_$itemValue" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(itemValue),
                    style = if (distance == 0) AppTheme.typography.labelNumeric else AppTheme.typography.bodyLarge,
                    color = WheelPickerDefaults.itemColor(distance, colors),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SelectionBand() {
    val colors = AppTheme.colors
    val lineWidth = AppBorder.thin
    Box(
        Modifier.fillMaxWidth().height(WheelPickerDefaults.ItemHeight)
            .clip(WheelPickerDefaults.BandShape)
            .background(colors.surfaceOverlay)
            .drawBehind {
                val stroke = lineWidth.toPx()
                drawLine(colors.borderStrong, Offset(0f, stroke / 2), Offset(size.width, stroke / 2), stroke)
                drawLine(
                    colors.borderStrong,
                    Offset(0f, size.height - stroke / 2),
                    Offset(
                        size.width,
                        size.height - stroke / 2,
                    ),
                    stroke,
                )
            },
    )
}

@Preview
@Composable
private fun WheelPickerPreview() {
    AppTheme {
        WheelPicker(
            range = 100..250,
            value = 178,
            onValueChange = {},
            label = { it.toString() },
            contentDescription = "Altura",
            stateDescription = { "$it cm" },
            testTagPrefix = "preview_wheel",
        )
    }
}
