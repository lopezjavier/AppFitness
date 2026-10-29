package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_cancel
import io.github.lopezjavier.appfitness.composeapp.resources.action_discard
import io.github.lopezjavier.appfitness.composeapp.resources.ic_check
import io.github.lopezjavier.appfitness.composeapp.resources.ic_delete
import io.github.lopezjavier.appfitness.composeapp.resources.live_discard_message
import io.github.lopezjavier.appfitness.composeapp.resources.live_discard_title
import io.github.lopezjavier.appfitness.composeapp.resources.live_finish
import io.github.lopezjavier.appfitness.composeapp.resources.live_finish_empty_reason
import io.github.lopezjavier.appfitness.composeapp.resources.live_finish_offline_reason
import io.github.lopezjavier.appfitness.composeapp.resources.live_menu_discard
import io.github.lopezjavier.appfitness.composeapp.resources.live_title
import io.github.lopezjavier.appfitness.composeapp.resources.note_sheet_exercise_title
import io.github.lopezjavier.appfitness.composeapp.resources.note_sheet_set_title
import io.github.lopezjavier.appfitness.designsystem.AppActionSheet
import io.github.lopezjavier.appfitness.designsystem.AppConfirmDialog
import io.github.lopezjavier.appfitness.designsystem.AppSheetAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.NoteSheet
import io.github.lopezjavier.appfitness.designsystem.RpePickerSheet
import io.github.lopezjavier.appfitness.designsystem.SetTypePickerSheet
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.exerciseNoteHelp
import io.github.lopezjavier.appfitness.designsystem.exerciseNoteOptions
import io.github.lopezjavier.appfitness.designsystem.setNoteHelp
import io.github.lopezjavier.appfitness.designsystem.setNoteOptions
import io.github.lopezjavier.appfitness.domain.usecase.RpeScale
import org.jetbrains.compose.resources.stringResource

/** The set type picker of the targeted set; a tap applies the choice, dismissing changes nothing. */
@Composable
internal fun LiveTypePicker(state: LiveSessionUiState.Active, onEvent: (LiveSessionEvent) -> Unit) {
    val target = state.typePicker ?: return
    val current = state.exercises.firstOrNull { it.position == target.position }
        ?.sets?.firstOrNull { it.setIndex == target.setIndex }?.type ?: return
    SetTypePickerSheet(
        current = current,
        onSelect = { onEvent(LiveSessionEvent.SetTypeChosen(target.position, target.setIndex, it)) },
        onDismiss = { onEvent(LiveSessionEvent.DismissTypePicker) },
    )
}

/** The RPE picker of the targeted set; a tap applies the choice (and completes the set when prompted). */
@Composable
internal fun LiveRpePicker(state: LiveSessionUiState.Active, onEvent: (LiveSessionEvent) -> Unit) {
    val target = state.rpePicker ?: return
    val row = state.exercises.firstOrNull { it.position == target.position }
        ?.sets?.firstOrNull { it.setIndex == target.setIndex } ?: return
    RpePickerSheet(
        current = row.rpe?.let(RpeScale::parse),
        setNumber = row.setIndex,
        onSelect = { onEvent(LiveSessionEvent.SetRpeChosen(target.position, target.setIndex, it)) },
        onDismiss = { onEvent(LiveSessionEvent.DismissRpePicker) },
        completesSet = target.completesSet,
    )
}

/** The note sheet of the targeted set; "Guardar" stores the note with the set and confirms, closing discards. */
@Composable
internal fun LiveNoteSheetHost(state: LiveSessionUiState.Active, onEvent: (LiveSessionEvent) -> Unit) {
    val sheet = state.noteSheet ?: return
    val haptics = LocalAppHaptics.current
    NoteSheet(
        title = stringResource(Res.string.note_sheet_set_title, sheet.setIndex),
        options = setNoteOptions(),
        kind = sheet.kind,
        draft = sheet.draft,
        originalKind = sheet.originalKind,
        original = sheet.original,
        help = setNoteHelp(),
        onKindChange = { onEvent(LiveSessionEvent.SetNoteKindChanged(it)) },
        onDraftChange = { onEvent(LiveSessionEvent.SetNoteDraftChanged(it)) },
        onSave = {
            haptics.confirm()
            onEvent(LiveSessionEvent.SaveSetNote)
        },
        onDismiss = { onEvent(LiveSessionEvent.DismissSetNote) },
        testTagPrefix = "note_sheet",
    )
}

/**
 * The routine note sheet of the targeted exercise (ADR-82); "Guardar" stores the note in the session's copy of the
 * routine, which the "Actualizar rutina" prompt may then persist.
 */
@Composable
internal fun LiveRoutineNoteSheetHost(state: LiveSessionUiState.Active, onEvent: (LiveSessionEvent) -> Unit) {
    val sheet = state.routineNoteSheet ?: return
    val haptics = LocalAppHaptics.current
    NoteSheet(
        title = stringResource(Res.string.note_sheet_exercise_title),
        options = exerciseNoteOptions(),
        kind = sheet.kind,
        draft = sheet.draft,
        originalKind = sheet.originalKind,
        original = sheet.original,
        help = exerciseNoteHelp(),
        onKindChange = { onEvent(LiveSessionEvent.RoutineNoteKindChanged(it)) },
        onDraftChange = { onEvent(LiveSessionEvent.RoutineNoteDraftChanged(it)) },
        onSave = {
            haptics.confirm()
            onEvent(LiveSessionEvent.SaveRoutineNote)
        },
        onDismiss = { onEvent(LiveSessionEvent.DismissRoutineNote) },
        testTagPrefix = "routine_note_sheet",
    )
}

@Composable
internal fun LiveDialogs(
    state: LiveSessionUiState.Active,
    menuOpen: Boolean,
    onCloseMenu: () -> Unit,
    onEvent: (LiveSessionEvent) -> Unit,
) {
    if (menuOpen) {
        AppActionSheet(
            actions = listOf(
                AppSheetAction(
                    icon = Res.drawable.ic_check,
                    title = stringResource(Res.string.live_finish),
                    onClick = { onEvent(LiveSessionEvent.RequestFinish) },
                    testTag = "live_menu_finish",
                    enabled = !state.actionsBlocked && !state.busy && state.canFinish,
                ),
                AppSheetAction(
                    icon = Res.drawable.ic_delete,
                    title = stringResource(Res.string.live_menu_discard),
                    onClick = { onEvent(LiveSessionEvent.RequestDiscard) },
                    testTag = "live_menu_discard",
                    enabled = !state.actionsBlocked,
                ),
            ),
            onDismiss = onCloseMenu,
            title = stringResource(Res.string.live_title),
            testTagPrefix = "live_menu",
            notice = {
                if (state.actionsBlocked) OfflineReason("live_menu_offline_reason")
                if (!state.canFinish) FinishEmptyReason()
            },
        )
    }
    state.finishSheet?.let {
        FinishSessionSheet(
            sheet = it,
            busy = state.busy,
            onConfirm = { onEvent(LiveSessionEvent.ConfirmFinish) },
            onDismiss = { onEvent(LiveSessionEvent.DismissFinish) },
        )
    }
    if (state.discardConfirm) {
        AppConfirmDialog(
            title = stringResource(Res.string.live_discard_title),
            message = stringResource(Res.string.live_discard_message),
            confirmLabel = stringResource(Res.string.action_discard),
            cancelLabel = stringResource(Res.string.action_cancel),
            onConfirm = { onEvent(LiveSessionEvent.ConfirmDiscard) },
            onDismiss = { onEvent(LiveSessionEvent.DismissDiscard) },
            testTagPrefix = "live_discard",
        )
    }
}

@Composable
private fun FinishEmptyReason() {
    Text(
        text = stringResource(Res.string.live_finish_empty_reason),
        style = AppTheme.typography.bodySmall,
        color = AppTheme.colors.textSecondary,
        modifier = Modifier.padding(horizontal = Spacing.screenMargin).testTag("live_menu_finish_reason"),
    )
}

@Composable
private fun OfflineReason(tag: String) {
    Text(
        text = stringResource(Res.string.live_finish_offline_reason),
        style = AppTheme.typography.bodySmall,
        color = AppTheme.colors.textSecondary,
        modifier = Modifier.padding(horizontal = Spacing.screenMargin).testTag(tag),
    )
}
