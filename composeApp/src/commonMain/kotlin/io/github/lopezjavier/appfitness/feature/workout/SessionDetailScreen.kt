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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_delete
import io.github.lopezjavier.appfitness.composeapp.resources.action_edit
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_history_rpe
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_unnamed
import io.github.lopezjavier.appfitness.composeapp.resources.ic_bookmark_add
import io.github.lopezjavier.appfitness.composeapp.resources.ic_delete
import io.github.lopezjavier.appfitness.composeapp.resources.ic_edit
import io.github.lopezjavier.appfitness.composeapp.resources.save_as_routine_failed
import io.github.lopezjavier.appfitness.composeapp.resources.session_action_failed
import io.github.lopezjavier.appfitness.composeapp.resources.session_detail_error
import io.github.lopezjavier.appfitness.composeapp.resources.session_detail_exercises
import io.github.lopezjavier.appfitness.composeapp.resources.session_detail_fallback_title
import io.github.lopezjavier.appfitness.composeapp.resources.session_detail_no_sets
import io.github.lopezjavier.appfitness.composeapp.resources.session_detail_routine_saved
import io.github.lopezjavier.appfitness.composeapp.resources.session_detail_save_as_routine_cd
import io.github.lopezjavier.appfitness.composeapp.resources.session_detail_volume_label
import io.github.lopezjavier.appfitness.composeapp.resources.summary_duration
import io.github.lopezjavier.appfitness.composeapp.resources.summary_sets
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppDateFormat
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.AppTopBarAction
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.ExerciseRow
import io.github.lopezjavier.appfitness.designsystem.InlineNote
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.SetOrdinal
import io.github.lopezjavier.appfitness.designsystem.SetTypeBadge
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.appAnimateItem
import io.github.lopezjavier.appfitness.designsystem.setNoteSummary
import io.github.lopezjavier.appfitness.domain.usecase.RpeScale
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private val SkeletonHeaderHeight = Spacing.xl * 4
private val SkeletonBlockHeight = Spacing.xl * 5

private const val TAG = "session_detail"

/**
 * Stateful session detail: connects [SessionDetailViewModel], leaves through [onClose] once the session was
 * deleted, opens an exercise's details through [onOpenExercise] and reports results through the app messages
 * (R-28) with one Confirm / Reject haptic (R-29).
 */
@Composable
fun SessionDetailRoot(
    sessionId: String,
    onClose: () -> Unit,
    onOpenExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit = {},
    viewModel: SessionDetailViewModel = koinViewModel(key = sessionId, parameters = { parametersOf(sessionId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val messages = LocalAppMessages.current
    val haptics = LocalAppHaptics.current
    val currentOnClose by rememberUpdatedState(onClose)
    val routineSaved = stringResource(Res.string.session_detail_routine_saved)
    val actionFailed = stringResource(Res.string.session_action_failed)
    val routineFailed = stringResource(Res.string.save_as_routine_failed)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SessionDetailEffect.Deleted -> currentOnClose()

                is SessionDetailEffect.SavedAsRoutine -> {
                    haptics.confirm()
                    messages.show(routineSaved)
                }

                SessionDetailEffect.ActionFailed -> {
                    haptics.reject()
                    messages.show(actionFailed)
                }
            }
        }
    }
    val saveFailed = (state as? SessionDetailUiState.Content)?.saveAsRoutine?.failed == true
    LaunchedEffect(saveFailed) {
        if (saveFailed) {
            haptics.reject()
            messages.show(routineFailed)
        }
    }
    // The workout may have been edited on the screen above this one.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(SessionDetailEvent.Refresh) }
    SessionDetailScreen(state, viewModel::onEvent, modifier, onClose, onOpenExercise, onEdit)
}

/**
 * Detail of a completed session (US-14) on [AppScreen]: the title is "<rutina> · <fecha>", Editar and Eliminar sit in
 * the top bar and "Guardar como rutina" in its overflow. Below: duration, sets and volume, and one card per exercise
 * whose name opens the exercise details, with its sets and "PR" marks.
 */
@Composable
fun SessionDetailScreen(
    state: SessionDetailUiState,
    onEvent: (SessionDetailEvent) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOpenExercise: (String) -> Unit = {},
    onEdit: () -> Unit = {},
) {
    val content = state as? SessionDetailUiState.Content
    val title = content?.let { "${it.name} · ${AppDateFormat.list(it.date, it.today)}" }
        ?: stringResource(Res.string.session_detail_fallback_title)
    AppScreen(
        title = title,
        modifier = modifier,
        onBack = onBack,
        actions = content?.let { detailActions(it, onEvent, onEdit) }.orEmpty(),
        testTagPrefix = TAG,
    ) {
        Box(Modifier.fillMaxSize().testTag("session_detail_screen")) {
            when (state) {
                SessionDetailUiState.Loading -> DetailLoading()
                SessionDetailUiState.Error -> DetailError { onEvent(SessionDetailEvent.Retry) }
                is SessionDetailUiState.Content -> DetailContent(state, onOpenExercise)
            }
        }
    }
    content?.saveAsRoutine?.let { sheet -> SaveAsRoutineSheet(content.name, sheet, onEvent) }
}

/** Editar and Eliminar inline, "Guardar como rutina" in the overflow (R-02); all disabled offline or while busy. */
@Composable
private fun detailActions(
    state: SessionDetailUiState.Content,
    onEvent: (SessionDetailEvent) -> Unit,
    onEdit: () -> Unit,
): List<AppTopBarAction> {
    val enabled = state.isOnline && !state.busy
    return listOf(
        AppTopBarAction(
            Res.drawable.ic_edit,
            stringResource(Res.string.action_edit),
            onEdit,
            testTag = "${TAG}_edit",
            enabled = enabled,
        ),
        AppTopBarAction(
            Res.drawable.ic_delete,
            stringResource(Res.string.action_delete),
            { onEvent(SessionDetailEvent.Delete) },
            testTag = "${TAG}_delete",
            enabled = enabled,
        ),
        AppTopBarAction(
            Res.drawable.ic_bookmark_add,
            stringResource(Res.string.session_detail_save_as_routine_cd),
            { onEvent(SessionDetailEvent.OpenSaveAsRoutine) },
            testTag = "${TAG}_save_as_routine",
            enabled = enabled,
        ),
    )
}

@Composable
private fun DetailContent(state: SessionDetailUiState.Content, onOpenExercise: (String) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().testTag("session_detail_list"),
        contentPadding = PaddingValues(horizontal = Spacing.screenMargin, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        if (!state.isOnline) item(key = "offline_hint") { OfflineHint(action = "editar") }
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
            ExerciseBlock(exercise, { onOpenExercise(exercise.exerciseId) }, appAnimateItem())
        }
    }
}

@Composable
private fun DetailHeader(state: SessionDetailUiState.Content) {
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
private fun ExerciseBlock(exercise: DetailExercise, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    AppCard(modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            ExerciseRow(
                name = exercise.name ?: stringResource(Res.string.exercise_unnamed),
                supporting = null,
                onOpen = onOpen,
                tagPrefix = "session_detail_exercise_${exercise.position}",
            )
            PersonalRecordPills(exercise.records, exercise.position, "session_detail")
            if (exercise.sets.isEmpty()) {
                Text(
                    text = stringResource(Res.string.session_detail_no_sets),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                )
            }
            exercise.sets.forEach { set ->
                SetLine(exercise.position, set)
                setNoteSummary(set.noteKind, set.notes)?.let {
                    InlineNote(text = it, testTag = "session_detail_set_note_${exercise.position}_${set.setIndex}")
                }
            }
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
            action = AppStateAction(stringResource(Res.string.action_retry), onRetry),
            testTagPrefix = "session_detail_error",
        )
    }
}
