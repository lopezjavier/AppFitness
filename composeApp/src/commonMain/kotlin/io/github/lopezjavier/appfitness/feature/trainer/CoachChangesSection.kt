package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.coach_change_add_range
import io.github.lopezjavier.appfitness.composeapp.resources.coach_change_decide_offline
import io.github.lopezjavier.appfitness.composeapp.resources.coach_change_decided_keep
import io.github.lopezjavier.appfitness.composeapp.resources.coach_change_decided_raise
import io.github.lopezjavier.appfitness.composeapp.resources.coach_change_decided_range
import io.github.lopezjavier.appfitness.composeapp.resources.coach_change_flag_keep
import io.github.lopezjavier.appfitness.composeapp.resources.coach_change_flag_raise
import io.github.lopezjavier.appfitness.composeapp.resources.coach_change_marker_cd
import io.github.lopezjavier.appfitness.composeapp.resources.coach_change_marker_title
import io.github.lopezjavier.appfitness.composeapp.resources.coach_change_status_undone
import io.github.lopezjavier.appfitness.composeapp.resources.coach_change_undo
import io.github.lopezjavier.appfitness.composeapp.resources.coach_change_undo_cd
import io.github.lopezjavier.appfitness.composeapp.resources.coach_change_undo_offline
import io.github.lopezjavier.appfitness.composeapp.resources.coach_change_undone_line
import io.github.lopezjavier.appfitness.composeapp.resources.coach_changes_header
import io.github.lopezjavier.appfitness.designsystem.CoachChangeRow
import io.github.lopezjavier.appfitness.designsystem.CoachChangeRowAction
import io.github.lopezjavier.appfitness.designsystem.CoachChangeRowState
import io.github.lopezjavier.appfitness.designsystem.HelpButton
import io.github.lopezjavier.appfitness.designsystem.ListSectionHeader
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.CoachChangeDecision
import io.github.lopezjavier.appfitness.domain.model.CoachChangeKind
import io.github.lopezjavier.appfitness.domain.model.CoachChangeStatus
import io.github.lopezjavier.appfitness.domain.model.ProgressionFlagDecision
import org.jetbrains.compose.resources.stringResource

/** What decides whether the actions of a row are usable: the change in flight and the connection. */
private data class CoachChangeContext(val busyId: String?, val online: Boolean)

/**
 * "Cambios del entrenador" (US-97, US-104): the load changes the coach applied or flagged, one [CoachChangeRow] each.
 * [names] resolves the exercise names by id (R-54; a row waits for its name); `busyChangeId` is the change whose undo
 * or decision is in flight (its action shows progress, every other row's are disabled).
 */
@Composable
internal fun CoachChangesSection(
    state: TrainerUiState.Content,
    names: Map<String, String>,
    online: Boolean,
    onEvent: (TrainerEvent) -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    val named = state.coachChanges.filter { it.exerciseId in names }
    if (named.isEmpty()) return
    val context = CoachChangeContext(state.busyChangeId, online)
    Column(Modifier.testTag("trainer_coach_changes_section"), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ListSectionHeader(stringResource(Res.string.coach_changes_header))
        named.forEach { row ->
            names[row.exerciseId]?.let { CoachChangeItem(row, it, context, onEvent, onOpenExercise) }
        }
    }
}

@Composable
private fun CoachChangeItem(
    row: CoachChangeRowUi,
    name: String,
    context: CoachChangeContext,
    onEvent: (TrainerEvent) -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    val needsConnection = row.undoable || (row.pending && row.kind == CoachChangeKind.LoadFlag)
    val offlineAction = if (row.undoable) {
        Res.string.coach_change_undo_offline
    } else {
        Res.string.coach_change_decide_offline
    }
    CoachChangeRow(
        headline = name,
        reason = coachChangeReasonText(row.kind, row.figures),
        state = rowState(row),
        testTagPrefix = "trainer_coach_change_row_${row.id}",
        detail = undoneLine(row),
        statusText = statusText(row),
        actions = coachChangeActions(row, name, context.busyId == row.id, onEvent),
        enabled = context.busyId == null && (context.online || !needsConnection),
        onHeadlineClick = { onOpenExercise(row.exerciseId) },
        footer = if (!context.online && needsConnection) {
            { OfflineHint(stringResource(offlineAction)) }
        } else {
            null
        },
    )
}

@Composable
private fun undoneLine(row: CoachChangeRowUi): String? =
    row.figures.weightFromKg?.takeIf { row.status == CoachChangeStatus.Undone }
        ?.let { stringResource(Res.string.coach_change_undone_line, coachWeightText(it)) }

@Composable
private fun statusText(row: CoachChangeRowUi): String? = when (row.status) {
    CoachChangeStatus.Undone -> stringResource(Res.string.coach_change_status_undone)
    CoachChangeStatus.Decided -> decidedText(row.decision)
    else -> null
}

/** The text actions of a row: "Deshacer", the two flag options or "Añadir un rango" (only one set applies). */
@Composable
private fun coachChangeActions(
    row: CoachChangeRowUi,
    name: String,
    busy: Boolean,
    onEvent: (TrainerEvent) -> Unit,
): List<CoachChangeRowAction> = when {
    row.undoable -> listOf(
        CoachChangeRowAction(
            text = stringResource(Res.string.coach_change_undo),
            testTag = "trainer_coach_change_undo_${row.id}",
            onClick = { onEvent(TrainerEvent.UndoCoachChange(row.id)) },
            loading = busy,
            contentDescription = stringResource(Res.string.coach_change_undo_cd, name),
        ),
    )

    row.pending && row.kind == CoachChangeKind.LoadFlag -> listOf(
        CoachChangeRowAction(
            text = stringResource(Res.string.coach_change_flag_raise, coachWeightText(row.figures.nextKg ?: 0.0)),
            testTag = "trainer_coach_change_raise_${row.id}",
            onClick = { onEvent(TrainerEvent.DecideFlag(row.id, ProgressionFlagDecision.Raise)) },
            primary = true,
        ),
        CoachChangeRowAction(
            text = stringResource(Res.string.coach_change_flag_keep),
            testTag = "trainer_coach_change_keep_${row.id}",
            onClick = { onEvent(TrainerEvent.DecideFlag(row.id, ProgressionFlagDecision.Keep)) },
        ),
    )

    row.pending && row.kind == CoachChangeKind.NoRangeFlag -> listOf(
        CoachChangeRowAction(
            text = stringResource(Res.string.coach_change_add_range),
            testTag = "trainer_coach_change_add_range_${row.id}",
            onClick = { onEvent(TrainerEvent.OpenRange(row.id)) },
        ),
    )

    else -> emptyList()
}

private fun rowState(row: CoachChangeRowUi): CoachChangeRowState = when {
    row.status == CoachChangeStatus.Undone -> CoachChangeRowState.Undone
    row.status == CoachChangeStatus.Decided -> CoachChangeRowState.Decided
    row.kind == CoachChangeKind.NoRangeFlag -> CoachChangeRowState.NoRange
    row.status == CoachChangeStatus.Pending -> CoachChangeRowState.Pending
    row.kind == CoachChangeKind.LoadKept -> CoachChangeRowState.Kept
    else -> CoachChangeRowState.Applied
}

@Composable
private fun decidedText(decision: CoachChangeDecision?): String? = when (decision) {
    CoachChangeDecision.Raise -> stringResource(Res.string.coach_change_decided_raise)
    CoachChangeDecision.Keep -> stringResource(Res.string.coach_change_decided_keep)
    CoachChangeDecision.RangeAdded -> stringResource(Res.string.coach_change_decided_range)
    else -> null
}

/**
 * The `?` of an exercise the coach changed (R-57, R-32): opens the reason in an info sheet. Tagged [testTag]; the
 * sheet reuses the [HelpButton] tags.
 */
@Composable
internal fun CoachChangeMarkerButton(
    exerciseName: String,
    marker: CoachChangeMarker,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    HelpButton(
        contentDescription = stringResource(Res.string.coach_change_marker_cd, exerciseName),
        title = stringResource(Res.string.coach_change_marker_title),
        body = coachChangeReasonText(marker.kind, marker.figures),
        testTag = testTag,
        modifier = modifier,
    )
}
