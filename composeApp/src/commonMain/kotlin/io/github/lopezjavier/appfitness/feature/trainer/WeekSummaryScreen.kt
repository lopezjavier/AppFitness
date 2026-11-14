package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.block_week_paused
import io.github.lopezjavier.appfitness.composeapp.resources.coach_changes_header
import io.github.lopezjavier.appfitness.composeapp.resources.coach_deload_headline
import io.github.lopezjavier.appfitness.composeapp.resources.coach_deload_status_cancelled
import io.github.lopezjavier.appfitness.composeapp.resources.ic_calendar
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_empty
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_error
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_group_cd_one
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_group_cd_other
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_group_line
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_groups_header
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_sessions
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
import io.github.lopezjavier.appfitness.designsystem.ListSectionHeader
import io.github.lopezjavier.appfitness.designsystem.OfflineState
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.SectionCard
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.domain.model.CoachChangeStatus
import io.github.lopezjavier.appfitness.domain.model.GroupSets
import io.github.lopezjavier.appfitness.domain.model.ProposalStatus
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.feature.exercises.label
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val SKELETON_CARDS = 3
private const val GROUP_SEPARATOR = " · "

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
 * "Resumen semanal" on [AppScreen] with back: read-only [AppCard] sections in one column (workouts, sets per muscle
 * group, nutrition, the coach changes and the deload). Loading is a skeleton, no summary
 * an [EmptyState], a failed load an [ErrorState] with "Reintentar" and no connection the offline state (R-31).
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

                WeekSummaryUiState.Empty -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    EmptyState(
                        icon = Res.drawable.ic_calendar,
                        message = stringResource(Res.string.week_summary_empty),
                        testTagPrefix = "week_summary_empty",
                    )
                }

                WeekSummaryUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    ErrorState(
                        message = stringResource(Res.string.week_summary_error),
                        action = AppStateAction(stringResource(Res.string.action_retry)) {
                            onEvent(WeekSummaryEvent.Retry)
                        },
                        testTagPrefix = "week_summary_error",
                        actionTestTag = "week_summary_retry",
                    )
                }

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

@Composable
internal fun SummaryContent(state: WeekSummaryUiState.Content) {
    val summary = state.summary
    val changeRows = visibleChangeRows(state)
    HubGrid {
        if (summary.paused) {
            // A paused week (US-106) has nothing to summarise: only its label.
            fullSpanItem("paused") {
                Text(
                    text = stringResource(Res.string.block_week_paused),
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.testTag("week_summary_paused"),
                )
            }
            return@HubGrid
        }
        item("sessions") {
            SessionsCard("week_summary_section_sessions", summary.sessionsDone, summary.sessionsPlanned)
        }
        if (WeekSummaryText.visibleGroups(summary.groups).isNotEmpty()) {
            item("groups") {
                GroupsCard(summary.groups, "week_summary", stringResource(Res.string.week_summary_groups_header))
            }
        }
        if (summary.nutrition != null) fullSpanItem("nutrition") { NutritionSection(summary) }
        summary.nutrition?.diagnosis?.let { diagnosis ->
            fullSpanItem("diagnosis") {
                DiagnosisCard(
                    diagnosis,
                    hasProposal = summary.nutrition?.decisions?.any { it.status == ProposalStatus.Pending } == true,
                )
            }
        }
        if (changeRows.isNotEmpty()) fullSpanItem("changes") { ChangesSection(changeRows) }
        if (state.summary.stalls.any { it.exerciseId in state.exerciseNames }) {
            fullSpanItem("stalls") { StallCard(state.summary.stalls, state.exerciseNames) }
        }
        if (WeekSummaryText.showsSeparateDeload(summary)) {
            fullSpanItem("deload") {
                ReportNoteCard("week_summary_section_deload") {
                    SummaryLine(stringResource(Res.string.coach_deload_headline))
                }
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

private typealias ChangeRow = Pair<CoachChangeRowUi, String?>

/** The coach's changes that can be drawn: a change waits for its exercise name (R-54); a deload is plan-level. */
private fun visibleChangeRows(state: WeekSummaryUiState.Content): List<ChangeRow> =
    state.summary.changes.toCoachChangeRows().mapNotNull { row ->
        val name = row.exerciseId?.let { state.exerciseNames[it] }
        if (row.kind.isDeload || name != null) row to name else null
    }

/** The coach's changes of the week, read-only: the shared row without actions, pending flags as plain status text. */
@Composable
private fun ChangesSection(rows: List<ChangeRow>) {
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
