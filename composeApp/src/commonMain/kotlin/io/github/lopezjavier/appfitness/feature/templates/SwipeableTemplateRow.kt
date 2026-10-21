@file:OptIn(ExperimentalMaterial3Api::class) // SwipeToDismissBox is experimental in this Material3 version.

package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.template_swipe_delete_cd
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.feature.diary.DeleteBackground
import org.jetbrains.compose.resources.stringResource

/**
 * Wraps [content] so that swiping it from the end calls [onDelete] when [enabled] (the row is hidden by the list and
 * the snackbar offers the undo; a row that stays snaps back). The same action is exposed as a custom accessibility
 * action "Eliminar <name>", because a swipe is not accessible by itself.
 */
@Composable
internal fun SwipeableTemplateRow(
    templateId: String,
    name: String,
    enabled: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val currentOnDelete by rememberUpdatedState(onDelete)
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { target ->
            if (target == SwipeToDismissBoxValue.EndToStart) currentOnDelete()
            false
        },
    )
    val deleteLabel = stringResource(Res.string.template_swipe_delete_cd, name)
    SwipeToDismissBox(
        state = state,
        modifier = modifier
            .semantics {
                if (enabled) customActions = listOf(CustomAccessibilityAction(deleteLabel) { onDelete().let { true } })
            }
            .testTag("food_search_template_${templateId}_swipe"),
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = enabled,
        backgroundContent = {
            if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                DeleteBackground("food_search_template_${templateId}_delete_background")
            }
        },
        content = { Box(Modifier.background(AppTheme.colors.surfaceBase)) { content() } },
    )
}
