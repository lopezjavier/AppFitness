@file:Suppress("MatchingDeclarationName") // The file holds the column, its item scope and the drag handle.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_drag_handle
import org.jetbrains.compose.resources.painterResource

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

    fun dragBy(deltaY: Float) {
        val current = order ?: return
        val key = draggedKey ?: return
        offset += deltaY
        var index = current.indexOfFirst { keyOf(it) == key }
        var list = current
        while (true) {
            val step = dragStep(index, offset, slot(list.getOrNull(index + 1)), slot(list.getOrNull(index - 1)))
                ?: break
            list = list.toMutableList().apply { add(step.index, removeAt(index)) }
            index = step.index
            offset = step.offset
        }
        order = list
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
class ReorderableItemScope internal constructor(
    private val onDragStart: () -> Unit,
    private val onDragBy: (Float) -> Unit,
    private val onDragEnd: () -> Unit,
    private val onDragCancel: () -> Unit,
) {
    /** Starts a drag on long press and follows the finger vertically; apply it to a [DragHandle]. */
    fun Modifier.dragHandle(): Modifier = pointerInput(Unit) {
        detectDragGesturesAfterLongPress(
            onDragStart = { onDragStart() },
            onDrag = { change, amount ->
                change.consume()
                onDragBy(amount.y)
            },
            onDragEnd = { onDragEnd() },
            onDragCancel = { onDragCancel() },
        )
    }
}

/**
 * A plain [Column] whose items the user can reorder by long-pressing and dragging their [DragHandle] (own
 * implementation, no third-party library). Items are identified by [key] (a stable id, never the index), so their
 * state survives a move. The column reorders locally while dragging and reports the finished move once, on drop:
 * [onMove] receives the item's start and end index and the caller applies it to its list. There is no auto-scroll:
 * callers keep a non-drag alternative (move up / move down) for long lists.
 * [itemContent] receives the item, its index and whether it is being dragged.
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    key: (T) -> Any,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = Spacing.cardGap,
    itemContent: @Composable ReorderableItemScope.(item: T, index: Int, dragging: Boolean) -> Unit,
) {
    val state = remember { ReorderableState(key) }
    state.gapPx = with(LocalDensity.current) { spacing.toPx() }
    val currentItems by rememberUpdatedState(items)
    val currentOnMove by rememberUpdatedState(onMove)
    LaunchedEffect(state.settling) {
        if (state.settling) {
            // The dropped order stays on screen until the caller's list has caught up with it.
            repeat(SETTLE_FRAMES) { withFrameNanos { } }
            state.settled()
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
        (state.order ?: items).forEachIndexed { index, item ->
            val itemKey = key(item)
            key(itemKey) {
                val scope =
                    remember(state, itemKey) {
                        ReorderableItemScope(
                            onDragStart = { state.start(currentItems, itemKey) },
                            onDragBy = state::dragBy,
                            onDragEnd = { state.drop()?.let { (from, to) -> currentOnMove(from, to) } },
                            onDragCancel = state::cancel,
                        )
                    }
                val dragging = state.draggedKey == itemKey
                Box(
                    Modifier
                        .onSizeChanged { state.onHeight(itemKey, it.height) }
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) state.offset else 0f },
                ) {
                    scope.itemContent(item, index, dragging)
                }
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
