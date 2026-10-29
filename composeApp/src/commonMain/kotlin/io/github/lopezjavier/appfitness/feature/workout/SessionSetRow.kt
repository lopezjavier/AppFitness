package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.builder_set_type_cd
import io.github.lopezjavier.appfitness.composeapp.resources.ic_cloud_off
import io.github.lopezjavier.appfitness.composeapp.resources.live_invalid_set
import io.github.lopezjavier.appfitness.composeapp.resources.live_invalid_set_metric
import io.github.lopezjavier.appfitness.composeapp.resources.live_pending_save
import io.github.lopezjavier.appfitness.composeapp.resources.live_rpe_cell_cd
import io.github.lopezjavier.appfitness.composeapp.resources.live_rpe_empty_state
import io.github.lopezjavier.appfitness.composeapp.resources.live_set_delete_cd
import io.github.lopezjavier.appfitness.composeapp.resources.note_edit
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.InlineNote
import io.github.lopezjavier.appfitness.designsystem.SetFieldKind
import io.github.lopezjavier.appfitness.designsystem.SetNoteButton
import io.github.lopezjavier.appfitness.designsystem.SetRow
import io.github.lopezjavier.appfitness.designsystem.SetRowFieldActions
import io.github.lopezjavier.appfitness.designsystem.SetRowValues
import io.github.lopezjavier.appfitness.designsystem.SetTypeBadgeDefaults
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.SwipeToDeleteRow
import io.github.lopezjavier.appfitness.designsystem.setNoteSummary
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.feature.exercises.setFieldSpecs
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Maps a live domain row to the values of a [SetRow]. The single place to extend for other set entry types. */
internal fun LiveSetRow.toSetRowValues() = SetRowValues(
    index = setIndex,
    previous = previous,
    weight = weight,
    reps = reps,
    completed = completed,
    weightPlaceholder = weightPlaceholder,
    repsPlaceholder = repsPlaceholder,
    type = type,
    rpe = rpe,
    distance = distance,
    steps = steps,
    floors = floors,
    durationSeconds = durationSeconds,
    stopwatchRunning = stopwatchRunning,
)

/**
 * One set of the live table: the design-system [SetRow] plus, outside it, a marker when the write is queued
 * (icon and text) and a hint when checking was refused.
 */
@Composable
@Suppress("LongMethod") // The design-system row plus its markers and the note line.
internal fun SessionSetRow(
    position: Int,
    row: LiveSetRow,
    onEvent: (LiveSessionEvent) -> Unit,
    modifier: Modifier = Modifier,
    metricType: ExerciseMetricType = ExerciseMetricType.WeightReps,
    equipment: Equipment? = null,
    deleteEnabled: Boolean = true,
) {
    val tag = "live_set_${position}_${row.setIndex}"
    val noteText = setNoteSummary(row.noteKind, row.notes)
    val openNote = { onEvent(LiveSessionEvent.OpenSetNote(position, row.setIndex)) }
    SwipeToDeleteRow(
        deleteLabel = stringResource(Res.string.live_set_delete_cd, row.setIndex),
        enabled = deleteEnabled,
        onDelete = { onEvent(LiveSessionEvent.DeleteSet(position, row.setIndex)) },
        backgroundTag = "live_set_delete_background_${position}_${row.setIndex}",
        modifier = modifier.testTag("${tag}_swipe"),
    ) {
        Column {
            SetRow(
                values = row.toSetRowValues(),
                onWeightChange = { onEvent(LiveSessionEvent.WeightChanged(position, row.setIndex, it)) },
                onRepsChange = { onEvent(LiveSessionEvent.RepsChanged(position, row.setIndex, it)) },
                onCompletedChange = { onEvent(LiveSessionEvent.SetChecked(position, row.setIndex, it)) },
                modifier = Modifier.testTag(tag),
                showPrevious = false,
                testTagPrefix = tag,
                onTypeClick = { onEvent(LiveSessionEvent.OpenTypePicker(position, row.setIndex)) },
                typeClickLabel = stringResource(
                    Res.string.builder_set_type_cd,
                    row.setIndex,
                    stringResource(SetTypeBadgeDefaults.labelResource(row.type)),
                ),
                fields = setFieldSpecs(metricType, equipment),
                actions = SetRowFieldActions(
                    onFieldChange = { kind, text -> onEvent(fieldChanged(kind, position, row.setIndex, text)) },
                    onStopwatchToggle = { onEvent(LiveSessionEvent.StopwatchToggled(position, row.setIndex)) },
                    onDurationEdit = { onEvent(LiveSessionEvent.DurationEdited(position, row.setIndex, it)) },
                ),
                onRpeClick = { onEvent(LiveSessionEvent.OpenRpePicker(position, row.setIndex)) },
                rpeClickLabel = stringResource(
                    Res.string.live_rpe_cell_cd,
                    AppNumberFormat.integer(row.setIndex),
                    row.rpe ?: stringResource(Res.string.live_rpe_empty_state),
                ),
                trailing = {
                    SetNoteButton(
                        setNumber = row.setIndex,
                        hasNote = noteText != null,
                        testTag = "live_set_note_action_${position}_${row.setIndex}",
                        onClick = openNote,
                    )
                },
            )
            if (row.pendingSave) PendingSaveLabel(Modifier.testTag("${tag}_pending"))
            noteText?.let {
                InlineNote(
                    text = it,
                    testTag = "live_set_note_${position}_${row.setIndex}",
                    onClick = openNote,
                    onClickLabel = stringResource(Res.string.note_edit),
                )
            }
            if (row.invalid) {
                Text(
                    text = stringResource(
                        if (metricType == ExerciseMetricType.WeightReps) {
                            Res.string.live_invalid_set
                        } else {
                            Res.string.live_invalid_set_metric
                        },
                    ),
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colors.error,
                    modifier = Modifier.padding(start = Spacing.xl).semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag("${tag}_invalid"),
                )
            }
        }
    }
}

/** The event of an edit of the distance, steps or floors field. */
private fun fieldChanged(kind: SetFieldKind, position: Int, setIndex: Int, text: String): LiveSessionEvent =
    when (kind) {
        SetFieldKind.Distance -> LiveSessionEvent.DistanceChanged(position, setIndex, text)
        SetFieldKind.Steps -> LiveSessionEvent.StepsChanged(position, setIndex, text)
        else -> LiveSessionEvent.FloorsChanged(position, setIndex, text)
    }

/** "Pendiente de guardar": amber icon and text under a row whose write has not reached the server. */
@Composable
private fun PendingSaveLabel(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(start = Spacing.xl).semantics(mergeDescendants = true) {
            liveRegion = LiveRegionMode.Polite
        },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_cloud_off),
            contentDescription = null,
            tint = AppTheme.colors.energyAmber,
            modifier = Modifier.size(Spacing.lg),
        )
        Text(
            text = stringResource(Res.string.live_pending_save),
            style = AppTheme.typography.bodySmall,
            color = AppTheme.colors.energyAmber,
        )
    }
}
