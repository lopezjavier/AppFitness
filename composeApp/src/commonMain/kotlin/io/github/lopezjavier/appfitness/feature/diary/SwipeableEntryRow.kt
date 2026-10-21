@file:OptIn(ExperimentalMaterial3Api::class) // SwipeToDismissBox is experimental in this Material3 version.

package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.diary_entry_delete
import io.github.lopezjavier.appfitness.composeapp.resources.diary_entry_delete_cd
import io.github.lopezjavier.appfitness.composeapp.resources.ic_delete
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Wraps [content] so that swiping it from the end calls [onDelete] when [enabled] (the row then snaps back and leaves
 * with the data update). A swipe is not accessible by itself, so the same action is exposed as a custom accessibility
 * action "Eliminar <name>". The background is the error colour with an icon and the word "Eliminar" (not colour
 * alone).
 */
@Composable
internal fun SwipeableEntryRow(
    entry: FoodLogEntry,
    enabled: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val currentOnDelete by rememberUpdatedState(onDelete)
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { target ->
            if (target == SwipeToDismissBoxValue.EndToStart) currentOnDelete()
            false
        },
    )
    val deleteLabel = stringResource(Res.string.diary_entry_delete_cd, entry.foodName)
    SwipeToDismissBox(
        state = state,
        modifier = modifier.semantics {
            if (enabled) customActions = listOf(CustomAccessibilityAction(deleteLabel) { onDelete().let { true } })
        },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = enabled,
        backgroundContent = {
            if (state.dismissDirection ==
                SwipeToDismissBoxValue.EndToStart
            ) {
                DeleteBackground("diary_entry_delete_${entry.id}")
            }
        },
        content = {
            Row(Modifier.background(AppTheme.colors.surfaceRaised)) { content() }
        },
    )
}

/** The swipe-to-delete background (error colour, icon and the word "Eliminar"), tagged [testTag]. */
@Composable
internal fun DeleteBackground(testTag: String) {
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
