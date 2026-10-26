package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_delete
import io.github.lopezjavier.appfitness.composeapp.resources.ic_delete
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** Share of the row width at which the swipe arms (Armed haptic, active layer). */
internal const val SWIPE_ARM_FRACTION = 0.5f

/** Share of the row width below which an armed swipe disarms again (hysteresis against flicker). */
internal const val SWIPE_DISARM_FRACTION = 0.4f

private const val SLIDE_OUT_MILLIS = 200
private const val COLLAPSE_MILLIS = 200
private const val SPRING_DAMPING = 0.8f
private const val ARMED_ICON_SCALE = 1.2f

/** Time the row waits after [onDelete] before assuming the caller refused it and showing itself again. */
private const val REFUSED_RESTORE_MILLIS = 600L

/** Result of one swipe frame: the new armed state and the haptic to fire (null when nothing crossed). */
internal data class SwipeStep(val armed: Boolean, val event: HapticEvent?)

/** Pure arm/disarm decision of the swipe (section 9.1): arms at 50 %, disarms below 40 %, one event per crossing. */
internal fun swipeStep(fraction: Float, armed: Boolean): SwipeStep = when {
    !armed && fraction >= SWIPE_ARM_FRACTION -> SwipeStep(armed = true, event = HapticEvent.Armed)
    armed && fraction < SWIPE_DISARM_FRACTION -> SwipeStep(armed = false, event = HapticEvent.Disarmed)
    else -> SwipeStep(armed, event = null)
}

/**
 * Shared swipe-to-delete (05 section 9.1). Dragging [content] right-to-left past 50 % of its width arms the
 * action (one Armed vibration); going back under 40 % disarms it. Releasing armed slides the row out, collapses
 * its height, then calls [onDelete] and fires one Confirm vibration; releasing unarmed, or any fling that did not
 * arm, springs back without vibrating. The same removal runs from the TalkBack action [deleteLabel]
 * ("Eliminar <name>"). The layer behind shows an icon and the word [label] ("Eliminar", or "Quitar" in editors),
 * muted below 50 % and active (error colour, larger icon, bold word) once armed, tagged [backgroundTag]. With
 * reduced motion the row snaps instead of animating; vibrations stay. If the caller keeps the row after
 * [onDelete] (the delete was refused) it shows itself again; callers that remove it simply drop the composable.
 * Stable keys and item animations are the caller's job.
 */
@Suppress("CyclomaticComplexMethod", "LongMethod") // One gesture state machine kept in a single place.
@Composable
fun SwipeToDeleteRow(
    deleteLabel: String,
    enabled: Boolean,
    onDelete: () -> Unit,
    backgroundTag: String,
    modifier: Modifier = Modifier,
    containerColor: Color = AppTheme.colors.surfaceRaised,
    label: String = stringResource(Res.string.action_delete),
    content: @Composable () -> Unit,
) {
    val haptics by rememberUpdatedState(LocalAppHaptics.current)
    val animated = LocalAnimationsEnabled.current
    val currentOnDelete by rememberUpdatedState(onDelete)
    val scope = rememberCoroutineScope()
    var widthPx by remember { mutableFloatStateOf(0f) }
    var offset by remember { mutableFloatStateOf(0f) }
    var heightFactor by remember { mutableFloatStateOf(1f) }
    var armed by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var springJob by remember { mutableStateOf<Job?>(null) }

    suspend fun slideTo(target: Float, millis: Int) {
        if (animated) {
            animate(offset, target, animationSpec = tween(millis, easing = LinearEasing)) { v, _ -> offset = v }
        } else {
            offset = target
        }
    }

    suspend fun remove() {
        if (removing || widthPx <= 0f) return
        removing = true
        slideTo(-widthPx, SLIDE_OUT_MILLIS)
        if (animated) {
            animate(1f, 0f, animationSpec = tween(COLLAPSE_MILLIS)) { v, _ -> heightFactor = v }
        } else {
            heightFactor = 0f
        }
        haptics.confirm()
        currentOnDelete()
        delay(REFUSED_RESTORE_MILLIS)
        offset = 0f
        heightFactor = 1f
        armed = false
        removing = false
    }

    fun springBack() {
        springJob = scope.launch {
            val spec = spring<Float>(dampingRatio = SPRING_DAMPING, stiffness = Spring.StiffnessMedium)
            if (animated) animate(offset, 0f, animationSpec = spec) { v, _ -> offset = v } else offset = 0f
        }
    }

    val dragState = rememberDraggableState { delta ->
        if (!removing) {
            offset = (offset + delta).coerceIn(-widthPx, 0f)
            val fraction = if (widthPx > 0f) -offset / widthPx else 0f
            val step = swipeStep(fraction, armed)
            armed = step.armed
            step.event?.let { haptics.perform(it) }
        }
    }
    Box(
        modifier
            .collapseHeight { heightFactor }
            .clipToBounds()
            .onSizeChanged { widthPx = it.width.toFloat() }
            .semantics {
                if (enabled) {
                    customActions = listOf(
                        CustomAccessibilityAction(deleteLabel) {
                            scope.launch { remove() }
                            true
                        },
                    )
                }
            }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                enabled = enabled && !removing,
                onDragStarted = { springJob?.cancel() },
                onDragStopped = { if (armed) scope.launch { remove() } else springBack() },
            ),
    ) {
        if (offset < 0f) SwipeLayer(label, armed, animated, backgroundTag, Modifier.matchParentSize())
        Box(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(offset.roundToInt(), 0) }
                .background(containerColor),
        ) { content() }
    }
}

private fun Modifier.collapseHeight(factor: () -> Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, (placeable.height * factor()).roundToInt()) { placeable.place(0, 0) }
}

/**
 * The layer behind the swipe: muted (container colours, normal icon and word) until armed, then active (error
 * colour, icon scaled up, bold word), so the state is not conveyed by colour alone.
 */
@Composable
private fun SwipeLayer(
    label: String,
    armed: Boolean,
    animated: Boolean,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (armed) ARMED_ICON_SCALE else 1f,
        animationSpec = if (animated) spring(dampingRatio = SPRING_DAMPING) else snap(),
        label = "swipeIconScale",
    )
    val container = if (armed) AppTheme.colors.error else AppTheme.colors.errorContainer
    val onContainer = if (armed) AppTheme.colors.onError else AppTheme.colors.onErrorContainer
    Row(
        modifier
            .background(container)
            .padding(horizontal = Spacing.lg)
            .testTag(testTag),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_delete),
            contentDescription = null,
            tint = onContainer,
            modifier = Modifier.size(Spacing.xl).graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        )
        Text(
            text = label,
            style = AppTheme.typography.labelNumeric,
            fontWeight = if (armed) FontWeight.Bold else FontWeight.Normal,
            color = onContainer,
        )
    }
}
