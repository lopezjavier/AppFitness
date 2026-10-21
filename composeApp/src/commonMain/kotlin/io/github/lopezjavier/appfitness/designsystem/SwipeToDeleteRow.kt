@file:OptIn(ExperimentalMaterial3Api::class) // SwipeToDismissBox is experimental in this Material3 version.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.diary_entry_delete
import io.github.lopezjavier.appfitness.composeapp.resources.ic_delete
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Share of the travel a drag must cover before the delete is confirmed (heavy swipe: only the end counts). */
private const val CONFIRM_FRACTION = 0.9f

/**
 * Shared slow/heavy swipe-to-delete (ADR-45). Dragging [content] from the end calls [onDelete] only once the drag
 * reaches the end of the travel; a shorter drag or fling snaps back. The row stays until the data update removes it.
 * A swipe is not accessible by itself, so the same action is exposed as the custom accessibility action
 * [deleteLabel] ("Eliminar <name>"). The background is the error colour with an icon and the word "Eliminar" (not
 * colour alone), tagged [backgroundTag].
 */
@Composable
fun SwipeToDeleteRow(
    deleteLabel: String,
    enabled: Boolean,
    onDelete: () -> Unit,
    backgroundTag: String,
    modifier: Modifier = Modifier,
    containerColor: Color = AppTheme.colors.surfaceRaised,
    content: @Composable () -> Unit,
) {
    val currentOnDelete by rememberUpdatedState(onDelete)
    val holder = remember { StateHolder() }
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { target ->
            // A fast fling can settle on the end anchor from a short drag; only the end of the travel confirms.
            if (target == SwipeToDismissBoxValue.EndToStart && holder.consumeEnd()) currentOnDelete()
            false
        },
        positionalThreshold = { total -> total * CONFIRM_FRACTION },
    )
    holder.state = state
    // The row is armed again once it is back at rest, so one gesture deletes once.
    SideEffect { if (state.dismissDirection == SwipeToDismissBoxValue.Settled) holder.armed = true }
    SwipeToDismissBox(
        state = state,
        modifier = modifier.onSizeChanged { holder.widthPx = it.width.toFloat() }.semantics {
            if (enabled) customActions = listOf(CustomAccessibilityAction(deleteLabel) { onDelete().let { true } })
        },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = enabled,
        backgroundContent = {
            if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) DeleteBackground(backgroundTag)
        },
        content = { Box(Modifier.background(containerColor)) { content() } },
    )
}

private class StateHolder {
    var state: SwipeToDismissBoxState? = null
    var widthPx: Float = 0f
    var armed: Boolean = true

    fun consumeEnd(): Boolean {
        if (!armed || !reachedEnd()) return false
        armed = false
        return true
    }

    private fun reachedEnd(): Boolean {
        val current = state ?: return false
        val offset = runCatching { current.requireOffset() }.getOrDefault(0f)
        return offset <= 0f && -offset >= widthPx * CONFIRM_FRACTION
    }
}

/** The swipe-to-delete background (error colour, icon and the word "Eliminar"), tagged [testTag]. */
@Composable
private fun DeleteBackground(testTag: String) {
    Row(
        Modifier
            .fillMaxSize()
            .background(AppTheme.colors.error)
            .padding(horizontal = Spacing.lg)
            .testTag(testTag),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_delete),
            contentDescription = null,
            tint = AppTheme.colors.onError,
            modifier = Modifier.size(Spacing.xl),
        )
        Text(
            text = stringResource(Res.string.diary_entry_delete),
            style = AppTheme.typography.labelNumeric,
            color = AppTheme.colors.onError,
        )
    }
}
