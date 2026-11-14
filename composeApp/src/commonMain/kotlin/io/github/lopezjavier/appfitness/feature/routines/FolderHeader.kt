package io.github.lopezjavier.appfitness.feature.routines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_expand_more
import io.github.lopezjavier.appfitness.composeapp.resources.ic_folder
import io.github.lopezjavier.appfitness.composeapp.resources.ic_folder_open
import io.github.lopezjavier.appfitness.composeapp.resources.ic_more_vert
import io.github.lopezjavier.appfitness.composeapp.resources.routines_folder_collapse_cd
import io.github.lopezjavier.appfitness.composeapp.resources.routines_folder_collapsed
import io.github.lopezjavier.appfitness.composeapp.resources.routines_folder_count
import io.github.lopezjavier.appfitness.composeapp.resources.routines_folder_empty
import io.github.lopezjavier.appfitness.composeapp.resources.routines_folder_expand_cd
import io.github.lopezjavier.appfitness.composeapp.resources.routines_folder_expanded
import io.github.lopezjavier.appfitness.composeapp.resources.routines_folder_menu_cd
import io.github.lopezjavier.appfitness.composeapp.resources.routines_folder_next
import io.github.lopezjavier.appfitness.composeapp.resources.routines_folder_summary
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.IconTile
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.RoutineFolder
import io.github.lopezjavier.appfitness.domain.model.RoutineSummary
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val CHEVRON_EXPANDED_DEGREES = 180f

/**
 * A folder as a card (C-95): icon tile, name with a chevron, and a summary line ("2 rutinas · Torso A, Pierna A";
 * collapsed with the next routine inside: "4 rutinas · Siguiente: Torso A"; empty: "Carpeta vacía", no chevron).
 * The tile and the text are one toggle; the actions menu (review with the coach, rename, delete; its entries check
 * the network and the folder's routines) is a separate 48 dp target.
 */
@Composable
internal fun FolderHeader(
    folder: RoutineFolder,
    routines: List<RoutineSummary>,
    expanded: Boolean,
    onEvent: (RoutinesEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = Spacing.md, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = CardMinHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FolderToggle(folder, routines, expanded, onEvent, Modifier.weight(1f))
            AppIconButton(
                contentDescription = stringResource(Res.string.routines_folder_menu_cd, folder.name),
                icon = Res.drawable.ic_more_vert,
                testTag = "routines_folder_menu_${folder.id}",
                onClick = { onEvent(RoutinesEvent.OpenFolderMenu(folder.id)) },
                tint = AppTheme.colors.textSecondary,
            )
        }
    }
}

private val CardMinHeight = Spacing.minTouchTarget + Spacing.sm

@Composable
private fun FolderToggle(
    folder: RoutineFolder,
    routines: List<RoutineSummary>,
    expanded: Boolean,
    onEvent: (RoutinesEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val toggleLabel = stringResource(
        if (expanded) Res.string.routines_folder_collapse_cd else Res.string.routines_folder_expand_cd,
        folder.name,
    )
    val stateLabel = stringResource(
        if (expanded) Res.string.routines_folder_expanded else Res.string.routines_folder_collapsed,
    )
    val empty = routines.isEmpty()
    // An empty folder has nothing to expand: no toggle semantics, no chevron (V-49).
    val toggle = if (empty) {
        Modifier
    } else {
        Modifier.clickable(role = Role.Button) { onEvent(RoutinesEvent.ToggleFolder(folder.id)) }
            .semantics {
                contentDescription = toggleLabel
                stateDescription = stateLabel
            }
    }
    Row(
        modifier.heightIn(min = CardMinHeight).then(toggle).testTag("routines_folder_toggle_${folder.id}"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(if (expanded && !empty) Res.drawable.ic_folder_open else Res.drawable.ic_folder)
        Column(
            Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }
                .testTag("routines_folder_${folder.id}"),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = folder.name,
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!empty) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_expand_more),
                        contentDescription = null,
                        tint = AppTheme.colors.textSecondary,
                        modifier = Modifier.size(Spacing.xl).rotate(if (expanded) CHEVRON_EXPANDED_DEGREES else 0f),
                    )
                }
            }
            FolderSummary(folder, routines, expanded)
        }
    }
}

/** Line 2 of a folder card: one line, ellipsis; the next routine's name is cyan while the folder is collapsed. */
@Composable
private fun FolderSummary(folder: RoutineFolder, routines: List<RoutineSummary>, expanded: Boolean) {
    if (routines.isEmpty()) {
        Text(
            text = stringResource(Res.string.routines_folder_empty),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textTertiary,
            maxLines = 1,
            modifier = Modifier.testTag("routines_folder_empty_${folder.id}"),
        )
        return
    }
    val count = pluralStringResource(Res.plurals.routines_folder_count, routines.size, routines.size)
    val nextName = if (expanded) null else routines.firstOrNull { it.isNext }?.name
    val nextText = nextName?.let { stringResource(Res.string.routines_folder_next, it) }
    val tail = nextText ?: routines.map { it.name }.joinToString(", ")
    val summary = stringResource(Res.string.routines_folder_summary, count, tail)
    val nextStyle = SpanStyle(color = AppTheme.colors.athleticCyan)
    Text(
        text = buildAnnotatedString {
            append(summary)
            if (nextText != null) {
                val start = summary.lastIndexOf(nextText)
                if (start >= 0) addStyle(nextStyle, start, start + nextText.length)
            }
        },
        style = AppTheme.typography.bodyMedium,
        color = AppTheme.colors.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
