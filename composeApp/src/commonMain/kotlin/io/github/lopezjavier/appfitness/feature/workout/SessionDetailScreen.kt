@file:Suppress("TooManyFunctions") // One small composable per part of the screen.

package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_history_rpe
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_link
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_link_cd
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_unnamed
import io.github.lopezjavier.appfitness.composeapp.resources.ic_bookmark_add
import io.github.lopezjavier.appfitness.composeapp.resources.ic_delete
import io.github.lopezjavier.appfitness.composeapp.resources.ic_edit
import io.github.lopezjavier.appfitness.composeapp.resources.session_action_failed
import io.github.lopezjavier.appfitness.composeapp.resources.session_detail_delete_cd
import io.github.lopezjavier.appfitness.composeapp.resources.session_detail_edit_cd
import io.github.lopezjavier.appfitness.composeapp.resources.session_detail_error
import io.github.lopezjavier.appfitness.composeapp.resources.session_detail_exercises
import io.github.lopezjavier.appfitness.composeapp.resources.session_detail_no_sets
import io.github.lopezjavier.appfitness.composeapp.resources.session_detail_routine_saved
import io.github.lopezjavier.appfitness.composeapp.resources.session_detail_save_as_routine_cd
import io.github.lopezjavier.appfitness.composeapp.resources.session_detail_volume_label
import io.github.lopezjavier.appfitness.composeapp.resources.session_offline_reason
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.composeapp.resources.summary_duration
import io.github.lopezjavier.appfitness.composeapp.resources.summary_sets
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.SetOrdinal
import io.github.lopezjavier.appfitness.designsystem.SetTypeBadge
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.domain.usecase.RpeScale
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private val SkeletonHeaderHeight = Spacing.xl * 4
private val SkeletonBlockHeight = Spacing.xl * 5

/**
 * Stateful session detail: connects [SessionDetailViewModel], leaves through [onClose] once the session was
 * deleted and reports a failed save or deletion in a snackbar.
 */
@Composable
fun SessionDetailRoot(
    sessionId: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenProgress: (String) -> Unit = {},
    onEdit: () -> Unit = {},
    viewModel: SessionDetailViewModel = koinViewModel(key = sessionId, parameters = { parametersOf(sessionId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val currentOnClose by rememberUpdatedState(onClose)
    val routineSaved = stringResource(Res.string.session_detail_routine_saved)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SessionDetailEffect.Deleted -> currentOnClose()

                is SessionDetailEffect.SavedAsRoutine -> snackbarHost.showSnackbar(routineSaved)

                SessionDetailEffect.ActionFailed -> snackbarHost.showSnackbar(
                    getString(Res.string.session_action_failed),
                )
            }
        }
    }
    // The workout may have been edited on the screen above this one.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(SessionDetailEvent.Refresh) }
    SessionDetailScreen(state, viewModel::onEvent, modifier, snackbarHost, onOpenProgress, onEdit)
}

/**
 * Detail of a completed session (US-14): header with duration, sets and volume, one card per exercise with its sets
 * (tap to correct) and a "PR" mark, and the destructive "Eliminar sesión" at the end of the scroll.
 */
@Composable
fun SessionDetailScreen(
    state: SessionDetailUiState,
    onEvent: (SessionDetailEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHost: SnackbarHostState = remember { SnackbarHostState() },
    onOpenProgress: (String) -> Unit = {},
    onEdit: () -> Unit = {},
) {
    Box(modifier.fillMaxSize().testTag("session_detail_screen"), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = WindowWidthClassDefaults.FormContentMaxWidth).fillMaxSize()) {
            if (state is SessionDetailUiState.Content) DetailActions(state, onEvent, onEdit)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state) {
                    SessionDetailUiState.Loading -> DetailLoading()
                    SessionDetailUiState.Error -> DetailError { onEvent(SessionDetailEvent.Retry) }
                    is SessionDetailUiState.Content -> DetailContent(state, onOpenProgress)
                }
            }
            SnackbarHost(snackbarHost) { data -> Snackbar { Text(data.visuals.message) } }
        }
    }
    if (state is SessionDetailUiState.Content) state.saveAsRoutine?.let { SaveAsRoutineSheet(it, onEvent) }
}

/** Edit, "Guardar como rutina" and delete as icon buttons above the details; the details stay read-only. */
@Composable
private fun DetailActions(
    state: SessionDetailUiState.Content,
    onEvent: (SessionDetailEvent) -> Unit,
    onEdit: () -> Unit,
) {
    val canChange = state.isOnline && !state.busy
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            AppIconButton(
                contentDescription = stringResource(Res.string.session_detail_edit_cd),
                icon = Res.drawable.ic_edit,
                testTag = "session_detail_edit",
                onClick = onEdit,
                enabled = canChange,
            )
            AppIconButton(
                contentDescription = stringResource(Res.string.session_detail_save_as_routine_cd),
                icon = Res.drawable.ic_bookmark_add,
                testTag = "session_detail_save_as_routine",
                onClick = { onEvent(SessionDetailEvent.OpenSaveAsRoutine) },
                enabled = canChange,
            )
            AppIconButton(
                contentDescription = stringResource(Res.string.session_detail_delete_cd),
                icon = Res.drawable.ic_delete,
                testTag = "session_detail_delete",
                onClick = { onEvent(SessionDetailEvent.Delete) },
                enabled = canChange,
            )
        }
        if (!state.isOnline) {
            Text(
                text = stringResource(Res.string.session_offline_reason),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = Spacing.sm).testTag("session_detail_offline_reason"),
            )
        }
    }
}

@Composable
private fun DetailContent(state: SessionDetailUiState.Content, onOpenProgress: (String) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().testTag("session_detail_list"),
        contentPadding = PaddingValues(horizontal = Spacing.screenMargin, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        item(key = "header") { DetailHeader(state) }
        if (state.split.isNotEmpty()) item(key = "split") { MuscleSplitSection(state.split) }
        item(key = "exercises_title") {
            Text(
                text = stringResource(Res.string.session_detail_exercises),
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.padding(top = Spacing.blockGap - Spacing.cardGap).semantics { heading() },
            )
        }
        items(state.exercises, key = { it.position }) { exercise ->
            ExerciseBlock(exercise) { onOpenProgress(exercise.exerciseId) }
        }
    }
}

@Composable
private fun DetailHeader(state: SessionDetailUiState.Content) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(state.label.text(), style = AppTheme.typography.labelTechnical, color = AppTheme.colors.textSecondary)
            Text(
                text = state.name,
                style = AppTheme.typography.titleLarge,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
        }
        AppCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatRow(
                    stringResource(Res.string.summary_duration),
                    formatElapsed(state.stats.durationSeconds),
                    "session_detail_duration",
                )
                StatRow(
                    stringResource(Res.string.summary_sets),
                    AppNumberFormat.integer(state.stats.completedSets),
                    "session_detail_sets",
                )
                StatRow(
                    stringResource(Res.string.session_detail_volume_label),
                    volumeText(state.stats.volumeKg),
                    "session_detail_volume",
                )
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, tag: String) {
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag(tag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
        Text(value, style = AppTheme.typography.labelNumeric, color = AppTheme.colors.textPrimary)
    }
}

@Composable
private fun ExerciseBlock(exercise: DetailExercise, onOpenProgress: () -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = exercise.name ?: stringResource(Res.string.exercise_unnamed),
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            PersonalRecordPills(exercise.records, exercise.position, "session_detail")
            if (exercise.sets.isEmpty()) {
                Text(
                    text = stringResource(Res.string.session_detail_no_sets),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                )
            }
            exercise.sets.forEach { set -> SetLine(exercise.position, set) }
            val link = stringResource(
                Res.string.exercise_progress_link_cd,
                exercise.name ?: stringResource(Res.string.exercise_unnamed),
            )
            GhostButton(
                text = stringResource(Res.string.exercise_progress_link),
                onClick = onOpenProgress,
                modifier = Modifier
                    .semantics { contentDescription = link }
                    .testTag("session_detail_progress_${exercise.exerciseId}"),
            )
        }
    }
}

@Composable
private fun SetLine(position: Int, set: DetailSet) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget)
            .semantics(mergeDescendants = true) {}
            .testTag("session_detail_set_${position}_${set.setIndex}"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SetOrdinal(AppNumberFormat.integer(set.setIndex))
        SetTypeBadge(type = set.setType)
        Text(
            text = set.text,
            style = AppTheme.typography.labelNumeric,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        set.rpe?.let { rpe ->
            Text(
                text = stringResource(Res.string.exercise_history_rpe, RpeScale.text(rpe)),
                style = AppTheme.typography.labelNumeric,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.testTag("session_detail_set_rpe_${position}_${set.setIndex}"),
            )
        }
    }
}

@Composable
private fun DetailLoading() {
    Column(
        Modifier.fillMaxSize().padding(horizontal = Spacing.screenMargin, vertical = Spacing.lg)
            .testTag("session_detail_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        SkeletonBox(Modifier.fillMaxWidth().height(SkeletonHeaderHeight), AppShapes.lg)
        repeat(2) { SkeletonBox(Modifier.fillMaxWidth().height(SkeletonBlockHeight), AppShapes.lg) }
    }
}

@Composable
private fun DetailError(onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ErrorState(
            message = stringResource(Res.string.session_detail_error),
            action = AppStateAction(stringResource(Res.string.state_retry), onRetry),
            testTagPrefix = "session_detail_error",
        )
    }
}
