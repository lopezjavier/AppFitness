package io.github.lopezjavier.appfitness.feature.routines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_edit
import io.github.lopezjavier.appfitness.composeapp.resources.ic_more_vert
import io.github.lopezjavier.appfitness.composeapp.resources.routine_edit_cd
import io.github.lopezjavier.appfitness.composeapp.resources.routines_count
import io.github.lopezjavier.appfitness.composeapp.resources.routines_delete_cd
import io.github.lopezjavier.appfitness.composeapp.resources.routines_drag_cd
import io.github.lopezjavier.appfitness.composeapp.resources.routines_next_badge
import io.github.lopezjavier.appfitness.composeapp.resources.routines_next_state
import io.github.lopezjavier.appfitness.composeapp.resources.routines_row_menu_cd
import io.github.lopezjavier.appfitness.composeapp.resources.routines_start
import io.github.lopezjavier.appfitness.composeapp.resources.routines_start_cd
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.DragHandle
import io.github.lopezjavier.appfitness.designsystem.ReorderableItemScope
import io.github.lopezjavier.appfitness.designsystem.SecondaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.SwipeToDeleteRow
import io.github.lopezjavier.appfitness.domain.model.RoutineSummary
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One routine card (R-17, R-19, R-20, R-22): the whole card swipes left to delete at once; the handle only reorders
 * (it takes horizontal movement too, so it never arms the swipe); the name block opens the details, ✎ the builder
 * and ⋮ the actions sheet, each a 48 dp target; "Siguiente" marks the next workout (cyan border plus text) and
 * "Empezar" starts it. TalkBack gets "Subir" / "Bajar" and "Eliminar <nombre>" as custom actions.
 */
@Composable
internal fun ReorderableItemScope.RoutineCard(
    routine: RoutineSummary,
    canStart: Boolean,
    canDelete: Boolean,
    onEvent: (RoutinesEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    SwipeToDeleteRow(
        deleteLabel = stringResource(Res.string.routines_delete_cd, routine.name),
        enabled = canDelete,
        onDelete = { onEvent(RoutinesEvent.Delete(routine.id)) },
        backgroundTag = "routines_swipe_bg_${routine.id}",
        containerColor = AppTheme.colors.surfaceBase,
        modifier = modifier.fillMaxWidth(),
    ) {
        CardBody(routine, canStart, onEvent)
    }
}

@Composable
private fun ReorderableItemScope.CardBody(
    routine: RoutineSummary,
    canStart: Boolean,
    onEvent: (RoutinesEvent) -> Unit,
) {
    val startLabel = stringResource(Res.string.routines_start_cd, routine.name)
    val nextState = stringResource(Res.string.routines_next_state)
    AppCard(
        Modifier.fillMaxWidth().reorderActions().semantics { if (routine.isNext) stateDescription = nextState },
        highlighted = routine.isNext,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DragHandle(
                contentDescription = stringResource(Res.string.routines_drag_cd, routine.name),
                modifier = Modifier
                    .dragHandle()
                    // Takes the horizontal movement too, so a sideways drag on the grip never reaches the swipe.
                    .draggable(rememberDraggableState { }, Orientation.Horizontal)
                    .testTag("routines_drag_${routine.id}"),
            )
            NameBlock(routine, onEvent, Modifier.weight(1f))
            AppIconButton(
                contentDescription = stringResource(Res.string.routine_edit_cd, routine.name),
                icon = Res.drawable.ic_edit,
                testTag = "routines_edit_${routine.id}",
                onClick = { onEvent(RoutinesEvent.Edit(routine.id)) },
                tint = AppTheme.colors.textSecondary,
            )
            AppIconButton(
                contentDescription = stringResource(Res.string.routines_row_menu_cd, routine.name),
                icon = Res.drawable.ic_more_vert,
                testTag = "routines_row_menu_${routine.id}",
                onClick = { onEvent(RoutinesEvent.OpenMenu(routine.id)) },
                tint = AppTheme.colors.textSecondary,
            )
        }
        SecondaryButton(
            text = stringResource(Res.string.routines_start),
            onClick = { onEvent(RoutinesEvent.StartRoutine(routine.id)) },
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm).semantics { contentDescription = startLabel }
                .testTag("routines_start_${routine.id}"),
            enabled = canStart,
        )
    }
}

@Composable
private fun NameBlock(routine: RoutineSummary, onEvent: (RoutinesEvent) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.minTouchTarget)
                .clickable(role = Role.Button) { onEvent(RoutinesEvent.OpenDetails(routine.id)) }
                .testTag("routines_row_${routine.id}"),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterVertically),
        ) {
            Text(
                text = routine.name,
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(Res.plurals.routines_count, routine.exerciseCount, routine.exerciseCount),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
            )
        }
        if (routine.isNext) {
            Text(
                text = stringResource(Res.string.routines_next_badge),
                style = AppTheme.typography.labelTechnical,
                color = AppTheme.colors.athleticCyan,
                modifier = Modifier.testTag("routines_next_badge_${routine.id}"),
            )
        }
    }
}
