package io.github.lopezjavier.appfitness.feature.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.routines_folder_empty
import io.github.lopezjavier.appfitness.composeapp.resources.routines_group_none
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ReorderableColumn
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.appAnimateItem
import io.github.lopezjavier.appfitness.domain.model.RoutineGroup
import io.github.lopezjavier.appfitness.domain.usecase.moveWithinGroup
import org.jetbrains.compose.resources.stringResource

private const val NO_FOLDER_KEY = "group_none"

/**
 * One list item per group: folders first, then the no-folder group. The "Sin carpeta" header only shows when
 * there is at least one folder. Groups are separated by 24 dp.
 */
internal fun LazyListScope.routineGroups(state: RoutinesUiState.Content, onEvent: (RoutinesEvent) -> Unit) {
    val hasFolders = state.folders.isNotEmpty()
    val canStart = state.isOnline && !state.starting
    state.groups.forEach { group ->
        val folder = group.folder
        if (folder == null && group.routines.isEmpty()) return@forEach
        item(key = folder?.id?.let { "group_$it" } ?: NO_FOLDER_KEY) {
            Column(
                appAnimateItem().padding(bottom = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
            ) {
                val expanded = folder == null || folder.id !in state.collapsedFolderIds
                when {
                    folder != null -> FolderHeader(
                        folder = folder,
                        routineCount = group.routines.size,
                        expanded = expanded,
                        canWrite = state.isOnline,
                        onEvent = onEvent,
                    )

                    hasFolders -> Text(
                        text = stringResource(Res.string.routines_group_none),
                        style = AppTheme.typography.titleMedium,
                        color = AppTheme.colors.textPrimary,
                        modifier = Modifier.semantics { heading() }.testTag("routines_group_none"),
                    )
                }
                if (expanded) GroupBody(group, canStart, state.isOnline, onEvent)
            }
        }
    }
}

@Composable
private fun GroupBody(group: RoutineGroup, canStart: Boolean, canDelete: Boolean, onEvent: (RoutinesEvent) -> Unit) {
    val folderId = group.folder?.id
    if (group.routines.isEmpty()) {
        Text(
            text = stringResource(Res.string.routines_folder_empty),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.testTag("routines_folder_empty_$folderId"),
        )
        return
    }
    ReorderableColumn(
        items = group.routines,
        key = { it.id },
        onMove = { from, to ->
            val ids = group.routines.map { it.id }
            onEvent(RoutinesEvent.Reorder(folderId, moveWithinGroup(ids, ids[from], to - from)))
        },
        modifier = Modifier.fillMaxWidth(),
    ) { routine, _, _ ->
        RoutineCard(routine, canStart, canDelete, onEvent)
    }
}
