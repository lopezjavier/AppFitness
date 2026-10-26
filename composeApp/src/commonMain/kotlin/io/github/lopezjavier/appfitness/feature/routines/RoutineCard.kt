package io.github.lopezjavier.appfitness.feature.routines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
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
import io.github.lopezjavier.appfitness.composeapp.resources.ic_more_vert
import io.github.lopezjavier.appfitness.composeapp.resources.routines_count
import io.github.lopezjavier.appfitness.composeapp.resources.routines_drag_cd
import io.github.lopezjavier.appfitness.composeapp.resources.routines_next_badge
import io.github.lopezjavier.appfitness.composeapp.resources.routines_next_state
import io.github.lopezjavier.appfitness.composeapp.resources.routines_row_menu_cd
import io.github.lopezjavier.appfitness.composeapp.resources.routines_start
import io.github.lopezjavier.appfitness.composeapp.resources.routines_start_cd
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.DragHandle
import io.github.lopezjavier.appfitness.designsystem.ReorderableItemScope
import io.github.lopezjavier.appfitness.designsystem.SecondaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.RoutineSummary
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One routine card: drag handle, name, exercise count, the "Siguiente" badge when it is the next workout (cyan
 * border plus text, so it is not colour-only), the sheet menu and "Empezar".
 */
@Composable
internal fun ReorderableItemScope.RoutineCard(
    routine: RoutineSummary,
    canStart: Boolean,
    onEvent: (RoutinesEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val startLabel = stringResource(Res.string.routines_start_cd, routine.name)
    val nextState = stringResource(Res.string.routines_next_state)
    AppCard(
        modifier.fillMaxWidth().reorderActions().semantics { if (routine.isNext) stateDescription = nextState },
        highlighted = routine.isNext,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DragHandle(
                contentDescription = stringResource(Res.string.routines_drag_cd, routine.name),
                modifier = Modifier.dragHandle().testTag("routines_drag_${routine.id}"),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Column(
                    Modifier
                        .clickable(role = Role.Button) { onEvent(RoutinesEvent.Edit(routine.id)) }
                        .testTag("routines_row_${routine.id}"),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(
                        text = routine.name,
                        style = AppTheme.typography.titleMedium,
                        color = AppTheme.colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = pluralStringResource(
                            Res.plurals.routines_count,
                            routine.exerciseCount,
                            routine.exerciseCount,
                        ),
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
            RoutineMenuButton(routine, onEvent)
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
private fun RoutineMenuButton(routine: RoutineSummary, onEvent: (RoutinesEvent) -> Unit) {
    val menuLabel = stringResource(Res.string.routines_row_menu_cd, routine.name)
    Box(
        Modifier
            .size(Spacing.minTouchTarget)
            .clickable(role = Role.Button, onClickLabel = menuLabel) {
                onEvent(RoutinesEvent.OpenMenu(routine.id))
            }
            .testTag("routines_row_menu_${routine.id}"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_more_vert),
            contentDescription = menuLabel,
            tint = AppTheme.colors.textSecondary,
            modifier = Modifier.size(Spacing.xl),
        )
    }
}
