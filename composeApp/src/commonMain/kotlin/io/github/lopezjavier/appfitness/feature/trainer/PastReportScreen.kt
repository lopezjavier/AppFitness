package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_title
import io.github.lopezjavier.appfitness.composeapp.resources.ic_description
import io.github.lopezjavier.appfitness.composeapp.resources.past_report_error
import io.github.lopezjavier.appfitness.composeapp.resources.past_report_not_found
import io.github.lopezjavier.appfitness.composeapp.resources.reports_title
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_subtitle
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_title
import io.github.lopezjavier.appfitness.designsystem.AppDateFormat
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.ListRowSkeleton
import io.github.lopezjavier.appfitness.designsystem.OfflineState
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.ReportItem
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private const val PREFIX = "past_report"

/** Stateful viewer of one stored report (US-129): [reportId] is the route argument. */
@Composable
fun PastReportRoot(
    reportId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PastReportViewModel = koinViewModel(key = reportId, parameters = { parametersOf(reportId) }),
    connectivity: ConnectivityObserver = koinInject(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PastReportScreen(state, connectivity.isOnline, viewModel::onEvent, onBack, modifier)
}

/**
 * A stored weekly summary or block review on a pushed [AppScreen] (R-60): the live content composable read-only, the
 * live title and the period as subtitle. No actions, no footer and no haptics: nothing can be decided here.
 */
@Composable
fun PastReportScreen(
    state: PastReportUiState,
    isOnline: Flow<Boolean>,
    onEvent: (PastReportEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = when (state) {
        is PastReportUiState.Week -> state.item
        is PastReportUiState.Block -> state.item
        else -> null
    }
    AppScreen(
        title = stringResource(
            when (state) {
                is PastReportUiState.Week -> Res.string.week_summary_title
                is PastReportUiState.Block -> Res.string.block_review_title
                else -> Res.string.reports_title
            },
        ),
        modifier = modifier,
        subtitle = item?.let { subtitle(it) },
        onBack = onBack,
        testTagPrefix = PREFIX,
    ) {
        Box(Modifier.fillMaxSize().testTag("past_report_screen")) {
            when (state) {
                PastReportUiState.Loading -> ListRowSkeleton(
                    Modifier.fillMaxSize().padding(Spacing.screenMargin),
                    testTag = "past_report_loading",
                )

                PastReportUiState.NotFound -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    EmptyState(
                        icon = Res.drawable.ic_description,
                        message = stringResource(Res.string.past_report_not_found),
                        testTagPrefix = "past_report_empty",
                    )
                }

                PastReportUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    ErrorState(
                        message = stringResource(Res.string.past_report_error),
                        action = AppStateAction(stringResource(Res.string.action_retry)) {
                            onEvent(PastReportEvent.Retry)
                        },
                        testTagPrefix = "past_report_error",
                        actionTestTag = "past_report_retry",
                    )
                }

                PastReportUiState.Offline -> Box(
                    Modifier.fillMaxSize().testTag("past_report_offline"),
                    Alignment.Center,
                ) {
                    OfflineState(isOnline = isOnline, onReload = { onEvent(PastReportEvent.Retry) })
                }

                is PastReportUiState.Week -> SummaryContent(state.content)

                is PastReportUiState.Block -> ReviewContent(state.content, online = true, onEvent = {}, readOnly = true)
            }
        }
    }
}

@Composable
private fun subtitle(item: ReportItem): String {
    val range = AppDateFormat.dayRange(item.periodStart, item.periodEnd)
    val week = item.week
    return if (week == null || item.paused) range else stringResource(Res.string.week_summary_subtitle, week, range)
}
