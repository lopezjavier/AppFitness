package io.github.lopezjavier.appfitness.feature.routines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_more_vert
import io.github.lopezjavier.appfitness.composeapp.resources.ic_play_arrow
import io.github.lopezjavier.appfitness.composeapp.resources.routines_count
import io.github.lopezjavier.appfitness.composeapp.resources.routines_delete_cd
import io.github.lopezjavier.appfitness.composeapp.resources.routines_drag_cd
import io.github.lopezjavier.appfitness.composeapp.resources.routines_folder_summary
import io.github.lopezjavier.appfitness.composeapp.resources.routines_next_badge
import io.github.lopezjavier.appfitness.composeapp.resources.routines_next_state
import io.github.lopezjavier.appfitness.composeapp.resources.routines_row_menu_cd
import io.github.lopezjavier.appfitness.composeapp.resources.routines_start_cd
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.CircleIconButton
import io.github.lopezjavier.appfitness.designsystem.DragHandle
import io.github.lopezjavier.appfitness.designsystem.ReorderableItemScope
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.SwipeToDeleteRow
import io.github.lopezjavier.appfitness.domain.model.RoutineSummary
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One routine card (R-17, R-19, R-20, R-22, C-94): a single 72 dp row; the whole card swipes left to delete at once;
 * the handle only reorders (it takes horizontal movement too, so it never arms the swipe); the name block opens the
 * details, the round play button starts the session and ⋮ opens the actions sheet ("Editar" lives there); "Siguiente"
 * marks the next workout (cyan border plus text, lime play button). TalkBack gets "Subir" / "Bajar" and "Eliminar <nombre>" as custom actions.
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
        contentPadding = PaddingValues(start = Spacing.xs, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
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
            Spacer(Modifier.width(Spacing.md))
            NameBlock(routine, onEvent, Modifier.weight(1f))
            CircleIconButton(
                contentDescription = startLabel,
                icon = Res.drawable.ic_play_arrow,
                testTag = "routines_start_${routine.id}",
                onClick = { onEvent(RoutinesEvent.StartRoutine(routine.id)) },
                enabled = canStart,
                accent = routine.isNext,
            )
            AppIconButton(
                contentDescription = stringResource(Res.string.routines_row_menu_cd, routine.name),
                icon = Res.drawable.ic_more_vert,
                testTag = "routines_row_menu_${routine.id}",
                onClick = { onEvent(RoutinesEvent.OpenMenu(routine.id)) },
                tint = AppTheme.colors.textSecondary,
            )
        }
    }
}

/** Name and "6 ejercicios" (or "Siguiente · 6 ejercicios", C-94): one 56 dp tap target that opens the details. */
@Composable
private fun NameBlock(routine: RoutineSummary, onEvent: (RoutinesEvent) -> Unit, modifier: Modifier = Modifier) {
    val count = pluralStringResource(Res.plurals.routines_count, routine.exerciseCount, routine.exerciseCount)
    val next = stringResource(Res.string.routines_next_badge)
    val summary = if (routine.isNext) stringResource(Res.string.routines_folder_summary, next, count) else count
    val nextStyle = SpanStyle(
        color = AppTheme.colors.athleticCyan,
        fontWeight = AppTheme.typography.titleMedium.fontWeight,
    )
    Column(
        modifier
            .heightIn(min = Spacing.minTouchTarget + Spacing.sm)
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
            text = buildAnnotatedString {
                append(summary)
                if (routine.isNext) addStyle(nextStyle, 0, next.length)
            },
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
            modifier = if (routine.isNext) Modifier.testTag("routines_next_badge_${routine.id}") else Modifier,
        )
    }
}
