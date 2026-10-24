package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.history_add_past_workout_cd
import io.github.lopezjavier.appfitness.composeapp.resources.history_calendar_cd
import io.github.lopezjavier.appfitness.composeapp.resources.history_empty_message
import io.github.lopezjavier.appfitness.composeapp.resources.history_empty_title
import io.github.lopezjavier.appfitness.composeapp.resources.history_error
import io.github.lopezjavier.appfitness.composeapp.resources.history_load_more_error
import io.github.lopezjavier.appfitness.composeapp.resources.history_loading_more
import io.github.lopezjavier.appfitness.composeapp.resources.history_row_cd
import io.github.lopezjavier.appfitness.composeapp.resources.history_row_sets
import io.github.lopezjavier.appfitness.composeapp.resources.ic_add
import io.github.lopezjavier.appfitness.composeapp.resources.ic_calendar
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private const val SKELETON_ROWS = 5
private val SkeletonRowHeight = Spacing.xl * 3

/**
 * Stateful history: connects [HistoryViewModel], reloads when the screen comes back (after a deletion or a
 * correction) and opens a session's detail through [onOpenSession].
 */
@Composable
fun HistoryRoot(
    onOpenSession: (sessionId: String) -> Unit,
    onOpenCalendar: () -> Unit,
    onStartPastWorkout: (PastWorkoutSource) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = koinViewModel(),
    chooser: PastWorkoutChooserViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOpen by rememberUpdatedState(onOpenSession)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is HistoryEffect.OpenDetail -> currentOpen(effect.sessionId)
            }
        }
    }
    val currentStart by rememberUpdatedState(onStartPastWorkout)
    val chooserState by chooser.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(chooser) {
        chooser.effects.collect { effect ->
            when (effect) {
                is PastWorkoutChooserEffect.Start -> currentStart(effect.source)
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(HistoryEvent.Refresh) }
    HistoryScreen(
        state,
        viewModel::onEvent,
        modifier,
        onOpenCalendar,
        onAddPastWorkout = { chooser.onEvent(PastWorkoutChooserEvent.Open) },
    )
    PastWorkoutChooserSheet(chooserState, state, chooser::onEvent)
}

/**
 * The history list (US-14): one card per completed session, newest first, loading further pages at the end. The
 * calendar icon above it opens the training calendar through [onOpenCalendar].
 */
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onEvent: (HistoryEvent) -> Unit,
    modifier: Modifier = Modifier,
    onOpenCalendar: () -> Unit = {},
    onAddPastWorkout: () -> Unit = {},
) {
    Box(modifier.fillMaxSize().testTag("history_screen"), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = WindowWidthClassDefaults.FormContentMaxWidth).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm), horizontalArrangement = Arrangement.End) {
                AppIconButton(
                    contentDescription = stringResource(Res.string.history_add_past_workout_cd),
                    icon = Res.drawable.ic_add,
                    testTag = "history_add_past_workout",
                    onClick = onAddPastWorkout,
                )
                AppIconButton(
                    contentDescription = stringResource(Res.string.history_calendar_cd),
                    icon = Res.drawable.ic_calendar,
                    testTag = "history_calendar",
                    onClick = onOpenCalendar,
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state) {
                    HistoryUiState.Loading -> HistoryLoading()
                    HistoryUiState.Error -> HistoryMessage { onEvent(HistoryEvent.Retry) }
                    HistoryUiState.Empty -> HistoryEmpty()
                    is HistoryUiState.Content -> HistoryList(state, onEvent)
                }
            }
        }
    }
}

@Composable
private fun HistoryList(state: HistoryUiState.Content, onEvent: (HistoryEvent) -> Unit) {
    val listState = rememberLazyListState()
    val lastRow = state.rows.size - 1
    val currentOnEvent by rememberUpdatedState(onEvent)
    // Asks for the next page once the last row is on screen; the ViewModel ignores it while loading or at the end.
    LaunchedEffect(listState, lastRow, state.endReached, state.pageError) {
        if (state.endReached || state.pageError) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { if (it >= lastRow) currentOnEvent(HistoryEvent.LoadMore) }
    }
    LazyColumn(
        Modifier.fillMaxSize().testTag("history_list"),
        state = listState,
        contentPadding = PaddingValues(horizontal = Spacing.screenMargin, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        items(state.rows, key = {
            it.id
        }) { row -> HistoryRowCard(row, onClick = { onEvent(HistoryEvent.OpenSession(row.id)) }) }
        if (state.loadingMore) item(key = "loading_more") { LoadingMore() }
        if (state.pageError) item(key = "page_error") { PageError { onEvent(HistoryEvent.RetryLoadMore) } }
    }
}

@Composable
internal fun HistoryRowCard(
    row: HistoryRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String = "history_row_${row.id}",
) {
    val date = row.label.text()
    val duration = formatElapsed(row.durationSeconds)
    val volume = volumeText(row.volumeKg)
    val sets = pluralStringResource(Res.plurals.history_row_sets, row.completedSets, row.completedSets)
    val description = stringResource(Res.string.history_row_cd, date, row.name, duration, volume)
    AppCard(modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget)
                .clickable(role = Role.Button, onClick = onClick)
                .testTag(tag)
                .clearAndSetSemantics { contentDescription = description },
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(date, style = AppTheme.typography.labelTechnical, color = AppTheme.colors.textSecondary)
            Text(
                text = row.name,
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(duration, style = AppTheme.typography.labelNumeric, color = AppTheme.colors.athleticCyan)
                Text(volume, style = AppTheme.typography.labelNumeric, color = AppTheme.colors.athleticCyan)
                Text(sets, style = AppTheme.typography.labelNumeric, color = AppTheme.colors.textSecondary)
            }
        }
    }
}

@Composable
private fun LoadingMore() {
    val description = stringResource(Res.string.history_loading_more)
    Box(Modifier.fillMaxWidth().padding(vertical = Spacing.sm), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(Spacing.xl).semantics { contentDescription = description }
                .testTag("history_loading_more"),
            color = AppTheme.colors.athleticCyan,
        )
    }
}

@Composable
private fun PageError(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().testTag("history_load_more_error"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = stringResource(Res.string.history_load_more_error),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
        )
        GhostButton(
            text = stringResource(Res.string.state_retry),
            onClick = onRetry,
            modifier = Modifier.testTag("history_load_more_retry"),
        )
    }
}

@Composable
private fun HistoryLoading() {
    Column(
        Modifier.fillMaxSize().padding(horizontal = Spacing.screenMargin, vertical = Spacing.lg)
            .testTag("history_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        repeat(SKELETON_ROWS) { SkeletonBox(Modifier.fillMaxWidth().height(SkeletonRowHeight), AppShapes.lg) }
    }
}

@Composable
private fun HistoryEmpty() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(
            message = stringResource(Res.string.history_empty_message),
            title = stringResource(Res.string.history_empty_title),
            testTagPrefix = "history_empty",
        )
    }
}

@Composable
private fun HistoryMessage(onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ErrorState(
            message = stringResource(Res.string.history_error),
            action = AppStateAction(stringResource(Res.string.state_retry), onRetry),
            testTagPrefix = "history_error",
        )
    }
}
