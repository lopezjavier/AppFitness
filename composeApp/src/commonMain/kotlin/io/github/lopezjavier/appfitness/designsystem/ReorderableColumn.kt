@file:Suppress("MatchingDeclarationName") // The file holds the column, its item scope and the drag handle.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_move_down
import io.github.lopezjavier.appfitness.composeapp.resources.action_move_up
import io.github.lopezjavier.appfitness.composeapp.resources.ic_drag_handle
import kotlinx.coroutines.isActive
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Sizes of the [DragHandle]. */
object DragHandleDefaults {
    val TouchTarget: Dp = 48.dp
    val IconSize: Dp = 24.dp
}

/** Frames the dropped item stays pinned in its dropped slot while the caller's list catches up. */
private const val SETTLE_FRAMES = 2

/** Result of one reorder decision while dragging: the item now sits at [index] and the finger offset is [offset]. */
internal data class DragStep(val index: Int, val offset: Float)

/**
 * One swap decision. The dragged item is at [index] and displaced by [offset] px from its slot; [nextSlot] and
 * [previousSlot] are the sizes (height plus gap) of its neighbours, or null at the ends. The item swaps with a
 * neighbour once it has moved past half of that neighbour's slot, and the offset is re-based to the new slot.
 * Null means no swap yet.
 */
internal fun dragStep(index: Int, offset: Float, nextSlot: Float?, previousSlot: Float?): DragStep? = when {
    nextSlot != null && offset > nextSlot / 2 -> DragStep(index + 1, offset - nextSlot)
    previousSlot != null && offset < -previousSlot / 2 -> DragStep(index - 1, offset + previousSlot)
    else -> null
}

/** Distance from the viewport edge where auto-scroll starts, and its top speed per frame. */
private val AutoScrollEdge = 72.dp
private val AutoScrollMaxStep = 14.dp

/**
 * Auto-scroll step for a dragged item spanning [top]..[bottom] inside a viewport [viewportTop]..[viewportBottom]:
 * 0 in the middle, growing linearly to [maxStep] (negative upwards) as the item enters the [edge] band and beyond.
 */
@Suppress("LongParameterList") // Pure geometry: all six numbers are independent inputs.
internal fun autoScrollDelta(
    top: Float,
    bottom: Float,
    viewportTop: Float,
    viewportBottom: Float,
    edge: Float,
    maxStep: Float,
): Float = when {
    top < viewportTop + edge -> -maxStep * ((viewportTop + edge - top) / edge).coerceIn(0f, 1f)
    bottom > viewportBottom - edge -> maxStep * ((bottom - (viewportBottom - edge)) / edge).coerceIn(0f, 1f)
    else -> 0f
}

/** Indices an item at [index] of [count] can be moved to with the accessibility actions: up first, then down. */
internal fun reorderTargets(index: Int, count: Int): List<Int> =
    listOfNotNull((index - 1).takeIf { it >= 0 }, (index + 1).takeIf { it < count })

/** Drag bookkeeping of a [ReorderableColumn]: the order shown while dragging and the dragged item's offset. */
@Stable
internal class ReorderableState<T>(private val keyOf: (T) -> Any) {
    /** Order drawn instead of the caller's list while an item is dragged (and until a drop has been applied). */
    var order: List<T>? by mutableStateOf(null)
        private set
    var draggedKey: Any? by mutableStateOf(null)
        private set
    var offset: Float by mutableFloatStateOf(0f)
        private set
    var settling: Boolean by mutableStateOf(false)
        private set

    private var startIndex = -1
    private val heights = HashMap<Any, Float>()
    var gapPx: Float = 0f

    fun onHeight(key: Any, heightPx: Int) {
        heights[key] = heightPx.toFloat()
    }

    fun start(items: List<T>, key: Any) {
        val index = items.indexOfFirst { keyOf(it) == key }
        if (index < 0) return
        order = items
        startIndex = index
        draggedKey = key
        offset = 0f
        settling = false
    }

    /** Moves the dragged item by [deltaY] px; returns how many slots it swapped. */
    @Suppress("ReturnCount") // Two guards plus the result.
    fun dragBy(deltaY: Float): Int {
        val current = order ?: return 0
        val key = draggedKey ?: return 0
        var swaps = 0
        offset += deltaY
        var index = current.indexOfFirst { keyOf(it) == key }
        var list = current
        while (true) {
            val step = dragStep(index, offset, slot(list.getOrNull(index + 1)), slot(list.getOrNull(index - 1)))
                ?: break
            list = list.toMutableList().apply { add(step.index, removeAt(index)) }
            index = step.index
            offset = step.offset
            swaps++
        }
        order = list
        return swaps
    }

    /** Ends the drag; returns the (from, to) move to report, or null when the item is back where it started. */
    fun drop(): Pair<Int, Int>? {
        val key = draggedKey ?: return null
        val end = order?.indexOfFirst { keyOf(it) == key } ?: -1
        val from = startIndex
        draggedKey = null
        offset = 0f
        return if (end >= 0 && end != from) {
            settling = true
            from to end
        } else {
            order = null
            null
        }
    }

    fun cancel() {
        draggedKey = null
        offset = 0f
        order = null
    }

    fun settled() {
        settling = false
        order = null
    }

    private fun slot(item: T?): Float? = item?.let { (heights[keyOf(it)] ?: 0f) + gapPx }
}

/** Scope of one [ReorderableColumn] item: attach [dragHandle] to the element that starts the drag. */
@Stable
@Suppress("LongParameterList") // Gesture callbacks plus the accessibility data of one item.
class ReorderableItemScope internal constructor(
    private val onDragStart: () -> Unit,
    private val onDragBy: (Float) -> Unit,
    private val onDragEnd: () -> Unit,
    private val onDragCancel: () -> Unit,
    private val index: Int,
    private val count: Int,
    private val moveUpLabel: String,
    private val moveDownLabel: String,
    private val onReorder: (from: Int, to: Int) -> Unit,
) {
    /**
     * Starts a drag as soon as the finger passes the touch slop vertically and follows it; apply it to a
     * [DragHandle]. Only vertical movement is consumed, so a parent scroll keeps working elsewhere.
     */
    fun Modifier.dragHandle(): Modifier = pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragStart = { onDragStart() },
            onVerticalDrag = { change, amount ->
                change.consume()
                onDragBy(amount)
            },
            onDragEnd = { onDragEnd() },
            onDragCancel = { onDragCancel() },
        )
    }

    /** TalkBack actions "Subir" / "Bajar" (only the valid ones) so dragging is never the only way to reorder. */
    fun Modifier.reorderActions(): Modifier = semantics {
        customActions = reorderTargets(index, count).map { target ->
            CustomAccessibilityAction(if (target < index) moveUpLabel else moveDownLabel) {
                onReorder(index, target)
                true
            }
        }
    }
}

/**
 * A plain [Column] whose items the user can reorder by dragging their [DragHandle] at once (own implementation, no
 * third-party library). Items are identified by [key] (a stable id, never the index), so their state survives a
 * move. The column reorders locally while dragging and reports the finished move once, on drop: [onMove] receives
 * the item's start and end index and the caller applies it to its list. One Tick vibration marks the pick-up and
 * each swap. Inside a scroll container pass [onAutoScroll] (for example `scrollState::scrollBy`): while the dragged
 * item is near the top or bottom of the window it is called every frame with the pixels to scroll and returns the
 * pixels actually scrolled. Items that apply `Modifier.reorderActions()` expose "Subir"/"Bajar" TalkBack actions.
 * [itemContent] receives the item, its index and whether it is being dragged.
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    key: (T) -> Any,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = Spacing.cardGap,
    onAutoScroll: (suspend (Float) -> Float)? = null,
    itemContent: @Composable ReorderableItemScope.(item: T, index: Int, dragging: Boolean) -> Unit,
) {
    val state = remember { ReorderableState(key) }
    val density = LocalDensity.current
    state.gapPx = with(density) { spacing.toPx() }
    val haptics by rememberUpdatedState(LocalAppHaptics.current)
    val currentItems by rememberUpdatedState(items)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentAutoScroll = rememberUpdatedState(onAutoScroll)
    val coordinates = remember { HashMap<Any, LayoutCoordinates>() }
    val windowHeight = LocalWindowInfo.current.containerSize.height.toFloat()
    val upLabel = stringResource(Res.string.action_move_up)
    val downLabel = stringResource(Res.string.action_move_down)
    LaunchedEffect(state.settling) {
        if (state.settling) {
            // The dropped order stays on screen until the caller's list has caught up with it.
            repeat(SETTLE_FRAMES) { withFrameNanos { } }
            state.settled()
        }
    }
    AutoScrollEffect(state, coordinates, currentAutoScroll, windowHeight) { repeat(it) { haptics.tick() } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
        val shown = state.order ?: items
        shown.forEachIndexed { index, item ->
            val itemKey = key(item)
            key(itemKey) {
                val scope =
                    remember(state, itemKey, index, shown.size, upLabel, downLabel) {
                        ReorderableItemScope(
                            onDragStart = {
                                state.start(currentItems, itemKey)
                                haptics.tick()
                            },
                            onDragBy = { delta -> repeat(state.dragBy(delta)) { haptics.tick() } },
                            onDragEnd = { state.drop()?.let { (from, to) -> currentOnMove(from, to) } },
                            onDragCancel = state::cancel,
                            index = index,
                            count = shown.size,
                            moveUpLabel = upLabel,
                            moveDownLabel = downLabel,
                            onReorder = { from, to ->
                                currentOnMove(from, to)
                                haptics.tick()
                            },
                        )
                    }
                val dragging = state.draggedKey == itemKey
                Box(
                    Modifier
                        .onSizeChanged { state.onHeight(itemKey, it.height) }
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) state.offset else 0f }
                        .onGloballyPositioned { coordinates[itemKey] = it },
                ) {
                    scope.itemContent(item, index, dragging)
                }
            }
        }
    }
}

/**
 * While an item is dragged, scrolls the caller's container every frame when the item is near the window edge
 * ([autoScroll] returns the pixels it really scrolled) and keeps the dragged item under the finger; [onSwaps]
 * receives the swaps that scroll caused.
 */
@Composable
private fun <T> AutoScrollEffect(
    state: ReorderableState<T>,
    coordinates: Map<Any, LayoutCoordinates>,
    autoScroll: State<(suspend (Float) -> Float)?>,
    windowHeight: Float,
    onSwaps: (Int) -> Unit,
) {
    val density = LocalDensity.current
    val currentOnSwaps by rememberUpdatedState(onSwaps)
    LaunchedEffect(state.draggedKey) {
        val dragged = state.draggedKey ?: return@LaunchedEffect
        val edge = with(density) { AutoScrollEdge.toPx() }
        val maxStep = with(density) { AutoScrollMaxStep.toPx() }
        while (isActive) {
            withFrameNanos { }
            val scroll = autoScroll.value
            val node = coordinates[dragged]?.takeIf { it.isAttached }
            if (scroll != null && node != null) {
                val top = node.positionInWindow().y
                val delta = autoScrollDelta(top, top + node.size.height, 0f, windowHeight, edge, maxStep)
                val consumed = if (delta != 0f) scroll(delta) else 0f
                if (consumed != 0f) currentOnSwaps(state.dragBy(consumed))
            }
        }
    }
}

/**
 * The 48 dp grip of a reorderable row. It only draws the icon and names the action ([contentDescription], for
 * example "Reordenar Press de banca"); put `Modifier.dragHandle()` of the item scope on it to make it draggable.
 */
@Composable
fun DragHandle(contentDescription: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(DragHandleDefaults.TouchTarget)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_drag_handle),
            contentDescription = null,
            tint = AppTheme.colors.textSecondary,
            modifier = Modifier.size(DragHandleDefaults.IconSize),
        )
    }
}
