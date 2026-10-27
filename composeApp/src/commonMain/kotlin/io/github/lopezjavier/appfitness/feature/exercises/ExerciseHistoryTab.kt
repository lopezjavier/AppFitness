package io.github.lopezjavier.appfitness.feature.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_history_empty
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_history_error
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_history_rpe
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_history_set_cd
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_history_set_rpe_cd
import io.github.lopezjavier.appfitness.composeapp.resources.history_load_more_error
import io.github.lopezjavier.appfitness.composeapp.resources.history_loading_more
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.ExerciseLayoutDefaults
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.SetOrdinal
import io.github.lopezjavier.appfitness.designsystem.SetTypeBadge
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.appAnimateItem
import io.github.lopezjavier.appfitness.domain.usecase.RpeScale
import io.github.lopezjavier.appfitness.feature.workout.PersonalRecordPills
import io.github.lopezjavier.appfitness.feature.workout.sessionDateLabel
import io.github.lopezjavier.appfitness.feature.workout.sessionLocalDate
import io.github.lopezjavier.appfitness.feature.workout.text
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

private const val SKELETON_ROWS = 3
private val SkeletonRowHeight = Spacing.xl * 3

/**
 * "Historial" tab (US-28): completed sessions containing the exercise, newest first, with their sets. Asks for the
 * next page once the last row is on screen. [listState] is hoisted so the scroll position survives tab changes.
 */
@Composable
internal fun ExerciseHistoryTab(
    state: HistoryState,
    today: LocalDate,
    actions: ExerciseDetailActions,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = ExerciseLayoutDefaults.ContentMaxWidth).fillMaxSize()) {
            when (state) {
                HistoryState.Loading -> HistoryLoading()

                HistoryState.Empty -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        message = stringResource(Res.string.exercise_history_empty),
                        testTagPrefix = "exercise_history_empty",
                    )
                }

                HistoryState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ErrorState(
                        message = stringResource(Res.string.exercise_history_error),
                        action = AppStateAction(stringResource(Res.string.action_retry), actions.onRetryHistory),
                        testTagPrefix = "exercise_history_error",
                    )
                }

                is HistoryState.Content -> HistoryList(state, today, actions, listState)
            }
        }
    }
}

@Composable
private fun HistoryLoading() {
    Column(
        Modifier.fillMaxSize().padding(Spacing.screenMargin).testTag("exercise_history_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        repeat(SKELETON_ROWS) { SkeletonBox(Modifier.fillMaxWidth().padding(vertical = SkeletonRowHeight / 2)) }
    }
}

@Composable
private fun HistoryList(
    state: HistoryState.Content,
    today: LocalDate,
    actions: ExerciseDetailActions,
    listState: LazyListState,
) {
    val lastRow = state.rows.size - 1
    val currentLoadMore by rememberUpdatedState(actions.onLoadMoreHistory)
    LaunchedEffect(listState, lastRow, state.endReached, state.pageError) {
        if (state.endReached || state.pageError) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { if (it >= lastRow) currentLoadMore() }
    }
    LazyColumn(
        Modifier.fillMaxSize().testTag("exercise_history_list"),
        state = listState,
        contentPadding = PaddingValues(horizontal = Spacing.screenMargin, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        itemsIndexed(state.rows, key = { _, row -> row.sessionId }) { position, row ->
            HistoryRowCard(row, position, today, modifier = appAnimateItem())
        }
        if (state.loadingMore) item(key = "loading_more") { LoadingMore() }
        if (state.pageError) item(key = "page_error") { PageError(actions.onRetryHistoryPage) }
    }
}

@Composable
private fun HistoryRowCard(row: ExerciseHistoryRow, position: Int, today: LocalDate, modifier: Modifier = Modifier) {
    val date = sessionDateLabel(sessionLocalDate(row.startedAt), today).text()
    AppCard(
        modifier.fillMaxWidth().testTag("exercise_history_session_${row.sessionId}")
            .semantics(mergeDescendants = true) {},
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(date, style = AppTheme.typography.labelTechnical, color = AppTheme.colors.textSecondary)
            Text(
                text = row.name,
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            PersonalRecordPills(row.records, position, "exercise_history")
            row.sets.forEach { SetLine(row.sessionId, it) }
        }
    }
}

@Composable
private fun SetLine(sessionId: String, set: HistorySetUi) {
    val number = AppNumberFormat.integer(set.setIndex)
    val description = if (set.rpe == null) {
        stringResource(Res.string.exercise_history_set_cd, set.setIndex, set.text)
    } else {
        stringResource(Res.string.exercise_history_set_rpe_cd, set.setIndex, set.text, RpeScale.text(set.rpe))
    }
    Row(
        Modifier.fillMaxWidth().testTag("exercise_history_set_${sessionId}_${set.setIndex}")
            .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SetOrdinal(number)
        SetTypeBadge(set.setType)
        Text(
            text = set.text,
            style = AppTheme.typography.labelNumeric,
            color = AppTheme.colors.athleticCyan,
            modifier = Modifier.weight(1f),
        )
        if (set.rpe != null) {
            Text(
                text = stringResource(Res.string.exercise_history_rpe, RpeScale.text(set.rpe)),
                style = AppTheme.typography.labelNumeric,
                color = AppTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun LoadingMore() {
    val description = stringResource(Res.string.history_loading_more)
    Box(Modifier.fillMaxWidth().padding(vertical = Spacing.sm), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(Spacing.xl).semantics { contentDescription = description }
                .testTag("exercise_history_loading_more"),
            color = AppTheme.colors.athleticCyan,
        )
    }
}

@Composable
private fun PageError(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().testTag("exercise_history_page_error"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = stringResource(Res.string.history_load_more_error),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
        )
        GhostButton(
            text = stringResource(Res.string.action_retry),
            onClick = onRetry,
            modifier = Modifier.testTag("exercise_history_page_retry"),
        )
    }
}
