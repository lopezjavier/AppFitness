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
import io.github.lopezjavier.appfitness.composeapp.resources.coach_changes_header
import io.github.lopezjavier.appfitness.composeapp.resources.coach_deload_headline
import io.github.lopezjavier.appfitness.composeapp.resources.coach_deload_status_cancelled
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_empty
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_error
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_group_cd_one
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_group_cd_other
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_group_line
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_groups_header
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_missing_records
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_proposal
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_proposal_applied
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_proposal_declined
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_proposal_pending
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_records
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_sessions
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_subtitle
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_title
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_weigh_ins
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_weight
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_weight_avg_only
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_weight_no_target
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_weight_none
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
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.CoachChangeStatus
import io.github.lopezjavier.appfitness.domain.model.ProposalStatus
import io.github.lopezjavier.appfitness.domain.model.WeekSummary
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.feature.exercises.label
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.pluralStringResource
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
 * group, weight, records, the coach changes, the calorie proposal and the deload). Loading is a skeleton, no summary
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
            stringResource(
                Res.string.week_summary_subtitle,
                it.week,
                AppDateFormat.dayRange(it.startDate, it.endDate),
            )
        },
        onBack = onBack,
        testTagPrefix = "week_summary",
    ) {
        Box(Modifier.fillMaxSize().testTag("week_summary_screen")) {
            when (state) {
                WeekSummaryUiState.Loading -> SummarySkeleton()

                WeekSummaryUiState.Empty -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    EmptyState(
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
private fun SummaryContent(state: WeekSummaryUiState.Content) {
    val summary = state.summary
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
    ) {
        SummaryCard("week_summary_section_sessions") {
            SummaryLine(
                stringResource(Res.string.week_summary_sessions, summary.sessionsDone, summary.sessionsPlanned),
            )
        }
        GroupsCard(summary)
        WeightCard(summary)
        SummaryCard("week_summary_section_records") {
            SummaryLine(
                stringResource(
                    Res.string.week_summary_records,
                    summary.completeDays,
                    pluralStringResource(Res.plurals.week_summary_weigh_ins, summary.weighInDays, summary.weighInDays),
                ),
            )
        }
        ChangesSection(state)
        // Reserved position for the "¿Por qué no progreso?" lines (T-232); nothing is drawn here yet.
        ProposalCard(summary)
        if (WeekSummaryText.showsSeparateDeload(summary)) {
            SummaryCard("week_summary_section_deload") {
                SummaryLine(stringResource(Res.string.coach_deload_headline))
            }
        }
    }
}

@Composable
private fun SummaryCard(tag: String, content: @Composable () -> Unit) {
    AppCard(Modifier.fillMaxWidth().testTag(tag)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) { content() }
    }
}

@Composable
private fun SummaryLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = AppTheme.typography.bodyLarge,
        color = AppTheme.colors.textPrimary,
        modifier = modifier,
    )
}

@Composable
private fun GroupsCard(summary: WeekSummary) {
    val groups = WeekSummaryText.visibleGroups(summary.groups)
    if (groups.isEmpty()) return
    val labels = groups.map { stringResource(it.muscleGroup.label) }
    val line = groups.indices.map { i ->
        stringResource(Res.string.week_summary_group_line, labels[i], WeekSummaryText.sets(groups[i].sets))
    }.joinToString(GROUP_SEPARATOR)
    // Each group is spoken as a sentence ("Pecho, 10 series"; half sets as "2,5 series").
    val spoken = groups.indices.map { i ->
        val sets = groups[i].sets
        if (WeekSummaryText.isSingular(sets)) {
            stringResource(Res.string.week_summary_group_cd_one, labels[i])
        } else {
            stringResource(Res.string.week_summary_group_cd_other, labels[i], WeekSummaryText.sets(sets))
        }
    }.joinToString(". ")
    SummaryCard("week_summary_section_groups") {
        ListSectionHeader(stringResource(Res.string.week_summary_groups_header))
        SummaryLine(line, Modifier.testTag("week_summary_groups_line").semantics { contentDescription = spoken })
    }
}

@Composable
private fun WeightCard(summary: WeekSummary) {
    val weight = summary.weight
    val avg = weight?.avgKg
    val slope = weight?.slopeKgWeek
    val target = weight?.targetRateKgWeek
    val text = when {
        avg == null -> stringResource(Res.string.week_summary_weight_none)

        slope == null -> stringResource(Res.string.week_summary_weight_avg_only, WeekSummaryText.kg(avg))

        target == null -> stringResource(
            Res.string.week_summary_weight_no_target,
            WeekSummaryText.kg(avg),
            WeekSummaryText.signedKg(slope),
        )

        else -> stringResource(
            Res.string.week_summary_weight,
            WeekSummaryText.kg(avg),
            WeekSummaryText.signedKg(slope),
            WeekSummaryText.signedKg(target),
        )
    }
    SummaryCard("week_summary_section_weight") { SummaryLine(text) }
}

/** The coach's changes of the week, read-only: the shared row without actions, pending flags as plain status text. */
@Composable
private fun ChangesSection(state: WeekSummaryUiState.Content) {
    // A change waits for its exercise name (R-54); a deload is plan-level and needs none.
    val rows = state.summary.changes.toCoachChangeRows().mapNotNull { row ->
        val name = row.exerciseId?.let { state.exerciseNames[it] }
        if (row.kind.isDeload || name != null) row to name else null
    }
    if (rows.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().testTag("week_summary_section_changes"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        ListSectionHeader(stringResource(Res.string.coach_changes_header))
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

/** The calorie proposal of the week, or the gate message when records are missing (the proposal line is replaced). */
@Composable
private fun ProposalCard(summary: WeekSummary) {
    val proposal = summary.calorieProposal
    val text = when {
        summary.missingRecords -> stringResource(Res.string.week_summary_missing_records)

        proposal != null -> stringResource(
            Res.string.week_summary_proposal,
            WeekSummaryText.kcalDelta(proposal.proposal),
            stringResource(
                when (proposal.status) {
                    ProposalStatus.Pending -> Res.string.week_summary_proposal_pending
                    ProposalStatus.Applied -> Res.string.week_summary_proposal_applied
                    ProposalStatus.Declined -> Res.string.week_summary_proposal_declined
                },
            ),
        )

        else -> null
    }
    if (text != null) SummaryCard("week_summary_section_proposal") { SummaryLine(text) }
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
