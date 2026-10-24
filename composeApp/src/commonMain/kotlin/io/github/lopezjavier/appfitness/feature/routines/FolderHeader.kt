package io.github.lopezjavier.appfitness.feature.routines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_delete
import io.github.lopezjavier.appfitness.composeapp.resources.ic_edit
import io.github.lopezjavier.appfitness.composeapp.resources.ic_expand_more
import io.github.lopezjavier.appfitness.composeapp.resources.routines_folder_collapse_cd
import io.github.lopezjavier.appfitness.composeapp.resources.routines_folder_collapsed
import io.github.lopezjavier.appfitness.composeapp.resources.routines_folder_count
import io.github.lopezjavier.appfitness.composeapp.resources.routines_folder_delete_cd
import io.github.lopezjavier.appfitness.composeapp.resources.routines_folder_expand_cd
import io.github.lopezjavier.appfitness.composeapp.resources.routines_folder_expanded
import io.github.lopezjavier.appfitness.composeapp.resources.routines_folder_rename_cd
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.RoutineFolder
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val CHEVRON_EXPANDED_DEGREES = 180f

/**
 * Header of a folder section: chevron toggle, name, routine count and the rename / delete icon buttons. Rename and
 * delete are writes, so they need [canWrite].
 */
@Composable
internal fun FolderHeader(
    folder: RoutineFolder,
    routineCount: Int,
    expanded: Boolean,
    canWrite: Boolean,
    onEvent: (RoutinesEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FolderToggle(folder, expanded, onEvent)
        Column(
            Modifier.weight(1f).semantics(mergeDescendants = true) {
                heading()
            }.testTag("routines_folder_${folder.id}"),
        ) {
            Text(
                text = folder.name,
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(Res.plurals.routines_folder_count, routineCount, routineCount),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
            )
        }
        AppIconButton(
            contentDescription = stringResource(Res.string.routines_folder_rename_cd, folder.name),
            icon = Res.drawable.ic_edit,
            testTag = "routines_folder_rename_${folder.id}",
            onClick = { onEvent(RoutinesEvent.RenameFolder(folder.id)) },
            enabled = canWrite,
        )
        AppIconButton(
            contentDescription = stringResource(Res.string.routines_folder_delete_cd, folder.name),
            icon = Res.drawable.ic_delete,
            testTag = "routines_folder_delete_${folder.id}",
            onClick = { onEvent(RoutinesEvent.RequestDeleteFolder(folder.id)) },
            enabled = canWrite,
        )
    }
}

@Composable
private fun FolderToggle(folder: RoutineFolder, expanded: Boolean, onEvent: (RoutinesEvent) -> Unit) {
    val toggleLabel = stringResource(
        if (expanded) Res.string.routines_folder_collapse_cd else Res.string.routines_folder_expand_cd,
        folder.name,
    )
    val stateLabel = stringResource(
        if (expanded) Res.string.routines_folder_expanded else Res.string.routines_folder_collapsed,
    )
    Box(
        Modifier
            .size(Spacing.minTouchTarget)
            .clickable(role = Role.Button) { onEvent(RoutinesEvent.ToggleFolder(folder.id)) }
            .semantics {
                contentDescription = toggleLabel
                stateDescription = stateLabel
            }
            .testTag("routines_folder_toggle_${folder.id}"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_expand_more),
            contentDescription = null,
            tint = AppTheme.colors.textSecondary,
            modifier = Modifier.size(Spacing.xl).rotate(if (expanded) CHEVRON_EXPANDED_DEGREES else 0f),
        )
    }
}
