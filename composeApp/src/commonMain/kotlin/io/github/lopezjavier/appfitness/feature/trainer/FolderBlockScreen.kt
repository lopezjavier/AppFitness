package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_error
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_extra_object
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_plan_closed
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_reps_range
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_reps_single
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_save
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_save_action
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_save_error
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_slot_clear_cd
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_slot_swap_cd
import io.github.lopezjavier.appfitness.composeapp.resources.folder_block_empty_body
import io.github.lopezjavier.appfitness.composeapp.resources.folder_block_intro
import io.github.lopezjavier.appfitness.composeapp.resources.folder_block_left_out
import io.github.lopezjavier.appfitness.composeapp.resources.folder_block_missing_title
import io.github.lopezjavier.appfitness.composeapp.resources.folder_block_refused
import io.github.lopezjavier.appfitness.composeapp.resources.folder_block_refused_one
import io.github.lopezjavier.appfitness.composeapp.resources.folder_block_rep_fix
import io.github.lopezjavier.appfitness.composeapp.resources.folder_block_row_supporting
import io.github.lopezjavier.appfitness.composeapp.resources.folder_block_save_helper
import io.github.lopezjavier.appfitness.composeapp.resources.folder_block_sets_over
import io.github.lopezjavier.appfitness.composeapp.resources.folder_block_title
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_empty_title
import io.github.lopezjavier.appfitness.composeapp.resources.ic_close
import io.github.lopezjavier.appfitness.composeapp.resources.ic_swap
import io.github.lopezjavier.appfitness.designsystem.AddItemButton
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.DiscardChangesDialog
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.ExerciseRow
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.InlineNote
import io.github.lopezjavier.appfitness.designsystem.ListSectionHeader
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.OfflineState
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.coach.builder.DaysCheck
import io.github.lopezjavier.appfitness.domain.coach.builder.FolderBlockExercise
import io.github.lopezjavier.appfitness.domain.coach.builder.GateIssue
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource

private const val LOADING_ROWS = 4

/**
 * "Desde una carpeta" (US-93): the copy of a folder with its live score, what is missing and the fixes (add, swap,
 * remove an added row, rep fix). It only renders [state] and sends [FolderBlockEvent]s; every rule lives in the core.
 */
@Composable
internal fun FolderBlockScreen(
    state: FolderBlockUiState,
    isOnline: Flow<Boolean>,
    onEvent: (FolderBlockEvent) -> Unit,
    onOpenExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val online by isOnline.collectAsStateWithLifecycle(initialValue = true)
    val content = state as? FolderBlockUiState.Content
    AppScreen(
        title = stringResource(Res.string.folder_block_title),
        modifier = modifier,
        subtitle = when (state) {
            is FolderBlockUiState.Content -> state.folderName
            is FolderBlockUiState.Empty -> state.folderName
            is FolderBlockUiState.Refused -> state.folderName
            else -> null
        },
        onBack = { onEvent(FolderBlockEvent.Back) },
        footer = when {
            content != null -> ({ ContentFooter(content, online, onEvent) })
            state is FolderBlockUiState.Refused || state is FolderBlockUiState.Empty -> ({ BlockedFooter() })
            else -> null
        },
        testTagPrefix = "folder_block",
    ) {
        Box(Modifier.fillMaxSize().testTag("folder_block_screen")) {
            StateBody(state, isOnline, onEvent, onOpenExercise)
        }
    }
    if (content != null) {
        FolderBlockPickers(content, onEvent, onOpenExercise)
        if (content.discardDialog) {
            DiscardChangesDialog(
                onConfirm = { onEvent(FolderBlockEvent.ConfirmDiscard) },
                onDismiss = { onEvent(FolderBlockEvent.CancelDiscard) },
                testTagPrefix = "folder_block_discard_dialog",
                confirmTestTag = "folder_block_discard_confirm",
            )
        }
    }
}

@Composable
private fun StateBody(
    state: FolderBlockUiState,
    isOnline: Flow<Boolean>,
    onEvent: (FolderBlockEvent) -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    when (state) {
        FolderBlockUiState.Loading -> Column(
            Modifier.fillMaxSize().padding(Spacing.screenMargin).testTag("folder_block_loading"),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            repeat(LOADING_ROWS) { SkeletonBox(Modifier.fillMaxWidth().height(Spacing.minTouchTarget)) }
        }

        is FolderBlockUiState.Failed -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            if (state.offline) {
                OfflineState(isOnline = isOnline, onReload = { onEvent(FolderBlockEvent.Retry) })
            } else {
                ErrorState(
                    message = stringResource(Res.string.block_builder_error),
                    action = AppStateAction(stringResource(Res.string.action_retry)) {
                        onEvent(FolderBlockEvent.Retry)
                    },
                    testTagPrefix = "folder_block_error",
                    actionTestTag = "folder_block_retry",
                )
            }
        }

        is FolderBlockUiState.Empty -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            EmptyState(
                message = stringResource(Res.string.folder_block_empty_body),
                title = stringResource(Res.string.folder_review_result_empty_title),
                testTagPrefix = "folder_block_empty",
            )
        }

        is FolderBlockUiState.Refused -> RefusedNote(state)

        is FolderBlockUiState.Content -> ContentBody(state, onEvent, onOpenExercise)
    }
}

@Composable
private fun RefusedNote(state: FolderBlockUiState.Refused) {
    val text = when (val days = state.days) {
        is DaysCheck.Refused -> stringResource(Res.string.folder_block_refused, days.routines, days.days)
        else -> stringResource(Res.string.folder_block_refused_one, state.daysPerWeek)
    }
    InlineNote(
        text = text,
        modifier = Modifier
            .padding(Spacing.screenMargin)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("folder_block_refused_note"),
    )
}

/** Refused and empty folders: nothing can be saved, the footer says so. */
@Composable
private fun BlockedFooter() {
    Column(
        Modifier.fillMaxWidth().padding(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        PrimaryButton(
            text = stringResource(Res.string.block_builder_save),
            onClick = {},
            modifier = Modifier.fillMaxWidth().testTag("folder_block_save"),
            enabled = false,
        )
    }
}

@Composable
private fun ContentBody(
    state: FolderBlockUiState.Content,
    onEvent: (FolderBlockEvent) -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (state.status.percent < FULL_SCORE) {
            InlineNote(
                stringResource(Res.string.folder_block_intro, state.status.percent),
                Modifier.testTag("folder_block_intro"),
            )
        }
        state.draft.routines.forEachIndexed { ri, routine ->
            if (!routine.lifting) return@forEachIndexed
            ListSectionHeader(routine.name, Modifier.testTag("folder_block_routine_$ri"))
            routine.exercises.forEachIndexed { ei, exercise ->
                ExerciseBlockRow(state, ri, ei, exercise, onEvent, onOpenExercise)
            }
        }
        if (state.status.leftOutRoutines.isNotEmpty()) {
            InlineNote(
                stringResource(Res.string.folder_block_left_out, state.status.leftOutRoutines.joinToString(", ")),
                Modifier.testTag("folder_block_left_out_note"),
            )
        }
        MissingSection(state, onEvent)
    }
}

private const val FULL_SCORE = 100

@Suppress("LongParameterList") // The row of one exercise needs its place, its data and the two callbacks.
@Composable
private fun ExerciseBlockRow(
    state: FolderBlockUiState.Content,
    ri: Int,
    ei: Int,
    exercise: FolderBlockExercise,
    onEvent: (FolderBlockEvent) -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    val catalog = state.exercises[exercise.id] ?: return
    val name = catalog.name
    val min = exercise.repMin
    val max = exercise.repMax
    val sets = setsText(exercise.sets)
    val reps = when {
        min == null || max == null -> null
        min == max -> stringResource(Res.string.block_builder_reps_single, min)
        else -> stringResource(Res.string.block_builder_reps_range, min, max)
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        ExerciseRow(
            name = name,
            supporting = reps?.let { stringResource(Res.string.folder_block_row_supporting, sets, it) } ?: sets,
            onOpen = { onOpenExercise(exercise.id) },
            tagPrefix = "folder_block_row_${ri}_$ei",
            imageUri = catalog.rowImageUri(),
            trailing = {
                Row {
                    AppIconButton(
                        contentDescription = stringResource(Res.string.block_builder_slot_swap_cd, name),
                        icon = Res.drawable.ic_swap,
                        testTag = "folder_block_swap_${ri}_$ei",
                        onClick = { onEvent(FolderBlockEvent.OpenSwap(ri, ei)) },
                        tint = AppTheme.colors.textSecondary,
                    )
                    if (exercise.added) {
                        AppIconButton(
                            contentDescription = stringResource(Res.string.block_builder_slot_clear_cd, name),
                            icon = Res.drawable.ic_close,
                            testTag = "folder_block_remove_${ri}_$ei",
                            onClick = { onEvent(FolderBlockEvent.RemoveAdded(ri, ei)) },
                            tint = AppTheme.colors.textSecondary,
                        )
                    }
                }
            },
        )
        state.status.repFixes.firstOrNull { it.routineIndex == ri && it.exerciseIndex == ei }?.let { fix ->
            GhostButton(
                text = stringResource(Res.string.folder_block_rep_fix, fix.newMin, fix.newMax),
                onClick = { onEvent(FolderBlockEvent.ApplyRepFix(ri, ei)) },
                modifier = Modifier.testTag("folder_block_rep_fix_${ri}_$ei"),
            )
        }
        if (state.status.gate.any {
                it is GateIssue.SetsOverLimit && it.routineIndex == ri && it.exerciseId == exercise.id
            }
        ) {
            InlineNote(
                stringResource(Res.string.folder_block_sets_over),
                Modifier.testTag("folder_block_sets_over_note"),
            )
        }
    }
}

@Composable
private fun MissingSection(state: FolderBlockUiState.Content, onEvent: (FolderBlockEvent) -> Unit) {
    val groups = state.addableGroups
    if (groups.isEmpty()) return
    ListSectionHeader(stringResource(Res.string.folder_block_missing_title))
    groups.forEach { ReviewListRow(it.toReviewItem(), "folder_block_missing_row_") }
    AddItemButton(
        objectLabel = stringResource(Res.string.block_builder_extra_object),
        onClick = { onEvent(FolderBlockEvent.OpenAdd) },
        modifier = Modifier.testTag("folder_block_add"),
        enabled = !state.saving,
    )
}

/** The score, the notes and "Guardar bloque" (R-09: one pinned footer). */
@Composable
private fun ContentFooter(state: FolderBlockUiState.Content, online: Boolean, onEvent: (FolderBlockEvent) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        BuilderScoreBar(state.status.percent, "folder_block_score")
        if (!state.status.canSave) {
            Text(
                text = stringResource(Res.string.folder_block_save_helper),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.testTag("folder_block_save_helper"),
            )
        }
        when (state.saveError) {
            BuilderSaveError.Generic, BuilderSaveError.Offline -> InlineNote(
                stringResource(Res.string.block_builder_save_error),
                Modifier.testTag("folder_block_save_error"),
            )

            BuilderSaveError.PlanClosed -> InlineNote(
                stringResource(Res.string.block_builder_plan_closed),
                Modifier.testTag("folder_block_save_error"),
            )

            null -> Unit
        }
        PrimaryButton(
            text = stringResource(Res.string.block_builder_save),
            onClick = { onEvent(FolderBlockEvent.Save) },
            modifier = Modifier.fillMaxWidth().testTag("folder_block_save"),
            enabled = state.canSave && online,
            loading = state.saving,
        )
        if (!online) OfflineHint(stringResource(Res.string.block_builder_save_action))
    }
}
