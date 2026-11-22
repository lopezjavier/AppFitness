package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.block_week_paused
import io.github.lopezjavier.appfitness.composeapp.resources.coach_changes_header
import io.github.lopezjavier.appfitness.composeapp.resources.coach_deload_headline
import io.github.lopezjavier.appfitness.composeapp.resources.coach_deload_status_cancelled
import io.github.lopezjavier.appfitness.composeapp.resources.ic_calendar
import io.github.lopezjavier.appfitness.composeapp.resources.ic_pause
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_paused_body
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_empty
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_error
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_subtitle
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_title
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppDateFormat
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.CoachChangeRow
import io.github.lopezjavier.appfitness.designsystem.CoachChangeRowState
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.IconBadge
import io.github.lopezjavier.appfitness.designsystem.LocalAnimationsEnabled
import io.github.lopezjavier.appfitness.designsystem.OfflineState
import io.github.lopezjavier.appfitness.designsystem.SectionCard
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.domain.model.CoachChangeStatus
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val SKELETON_CARDS = 3

/** Stateful "Resumen semanal" (US-98): connects [WeekSummaryViewModel] to the stateless [WeekSummaryScreen]. */
@Composable
fun WeekSummaryRoot(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WeekSummaryViewModel = koinViewModel(),
    connectivity: ConnectivityObserver = koinInject(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WeekSummaryScreen(state, connectivity.isOnline, viewModel::onEvent, onBack, modifier)
}

/**
 * "Resumen semanal" on [AppScreen] with back: the progress-first report (US-141) and the kept coach cards.
 * Loading is a skeleton, no summary an [EmptyState], a failed load an [ErrorState] with "Reintentar" and no
 * connection the offline state (R-31).
 */
@Composable
fun WeekSummaryScreen(
    state: WeekSummaryUiState,
    isOnline: Flow<Boolean>,
    onEvent: (WeekSummaryEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val summary = (state as? WeekSummaryUiState.Content)?.summary
    AppScreen(
        title = stringResource(Res.string.week_summary_title),
        modifier = modifier,
        subtitle = summary?.let {
            val range = AppDateFormat.dayRange(it.startDate, it.endDate)
            if (it.paused) range else stringResource(Res.string.week_summary_subtitle, it.week, range)
        },
        onBack = onBack,
        maxContentWidth = WindowWidthClassDefaults.HubContentMaxWidth,
        testTagPrefix = "week_summary",
    ) {
        Box(Modifier.fillMaxSize().testTag("week_summary_screen")) {
            when (state) {
                WeekSummaryUiState.Loading -> SummarySkeleton()

                WeekSummaryUiState.Empty -> EmptyState(
                    modifier = Modifier.fillMaxSize(),
                    icon = Res.drawable.ic_calendar,
                    message = stringResource(Res.string.week_summary_empty),
                    testTagPrefix = "week_summary_empty",
                )

                WeekSummaryUiState.Error -> ErrorState(
                    modifier = Modifier.fillMaxSize(),
                    message = stringResource(Res.string.week_summary_error),
                    action = AppStateAction(stringResource(Res.string.action_retry)) {
                        onEvent(WeekSummaryEvent.Retry)
                    },
                    testTagPrefix = "week_summary_error",
                    actionTestTag = "week_summary_retry",
                )

                WeekSummaryUiState.Offline -> Box(
                    Modifier.fillMaxSize().testTag("week_summary_offline"),
                    Alignment.Center,
                ) {
                    OfflineState(isOnline = isOnline, onReload = { onEvent(WeekSummaryEvent.Retry) })
                }

                is WeekSummaryUiState.Content -> SummaryContent(state)
            }
        }
    }
}

/**
 * The report body shared by the live and the past screen: the coach card, the progress cards in the order of the goal
 * and the kept cards. The coach card's action scrolls to the diagnosis or stall card when the live report has one
 * (the grid state is saved with the screen, ADR-40); on a past report it is never a button (R-60).
 */
@Composable
internal fun SummaryContent(state: WeekSummaryUiState.Content) {
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val animate = LocalAnimationsEnabled.current
    val report = state.report
    val cards = summaryCards(state)
    if (state.summary.paused) {
        // A paused week (US-106) has nothing to summarise: only its label.
        HubGrid(state = gridState) { fullSpanItem("paused") { PausedWeekCard() } }
        return
    }
    val coach = report?.let { ui ->
        val target = if (ui.live) actionTarget(ui.coach.action) else null
        val index = cards.indexOfFirst { it.key == target }
        // The coach card is item 0, so a card's grid index is its position in [cards] plus one.
        val hasTarget = index >= 0
        val open: (() -> Unit)? = if (hasTarget) {
            {
                scope.launch {
                    // R-97: no animation under "remove animations".
                    if (animate) gridState.animateScrollToItem(index + 1) else gridState.scrollToItem(index + 1)
                }
            }
        } else {
            null
        }
        SummaryItem("coach") { WeekReportCoachCard(ui.coach, open) }
    }
    HubGrid(state = gridState) {
        (listOfNotNull(coach) + cards).forEach { item ->
            if (item.fullSpan) fullSpanItem(item.key, item.content) else item(key = item.key) { item.content() }
        }
    }
}

/** A paused week (US-106): a pause glyph and the label, with the reason there is nothing to compare. */
@Composable
private fun PausedWeekCard() {
    AppCard(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("week_summary_paused")) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Res.drawable.ic_pause, AppTheme.colors.athleticCyan)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = stringResource(Res.string.block_week_paused),
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.colors.textPrimary,
                )
                Text(
                    text = stringResource(Res.string.week_report_paused_body),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
internal fun SummaryLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = AppTheme.typography.bodyLarge,
        color = AppTheme.colors.textPrimary,
        modifier = modifier,
    )
}

internal typealias ChangeRow = Pair<CoachChangeRowUi, String?>

/** The coach's changes that can be drawn: a change waits for its exercise name (R-54); a deload is plan-level. */
internal fun visibleChangeRows(state: WeekSummaryUiState.Content): List<ChangeRow> =
    state.summary.changes.toCoachChangeRows().mapNotNull { row ->
        val name = row.exerciseId?.let { state.exerciseNames[it] }
        if (row.kind.isDeload || name != null) row to name else null
    }

/** The coach's changes of the week, read-only: the shared row without actions, pending flags as plain status text. */
@Composable
internal fun ChangesSection(rows: List<ChangeRow>) {
    SectionCard(stringResource(Res.string.coach_changes_header), testTag = "week_summary_section_changes") {
        rows.forEach { (row, name) ->
            val cancelled = row.status == CoachChangeStatus.Undone
            if (row.kind.isDeload) {
                CoachChangeRow(
                    headline = stringResource(Res.string.coach_deload_headline),
                    reason = coachChangeReasonText(row.kind, row.figures),
                    state = if (cancelled) CoachChangeRowState.Undone else CoachChangeRowState.Applied,
                    testTagPrefix = "week_summary_change_row_${row.id}",
                    statusText = if (cancelled) stringResource(Res.string.coach_deload_status_cancelled) else null,
                )
            } else {
                CoachChangeRow(
                    headline = name.orEmpty(),
                    reason = coachChangeReasonText(row.kind, row.figures),
                    state = rowState(row),
                    testTagPrefix = "week_summary_change_row_${row.id}",
                    detail = undoneLine(row),
                    statusText = statusText(row),
                )
            }
        }
    }
}

/** Card-shaped placeholders while the summary loads (R-31). */
@Composable
private fun SummarySkeleton() {
    Column(
        Modifier.fillMaxSize().padding(Spacing.screenMargin).testTag("week_summary_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
    ) {
        repeat(SKELETON_CARDS) {
            AppCard(Modifier.fillMaxWidth()) { SkeletonBox(Modifier.fillMaxWidth().height(Spacing.xl)) }
        }
    }
}
