package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_title
import io.github.lopezjavier.appfitness.composeapp.resources.ic_chevron_right
import io.github.lopezjavier.appfitness.composeapp.resources.ic_calendar
import io.github.lopezjavier.appfitness.composeapp.resources.ic_description
import io.github.lopezjavier.appfitness.composeapp.resources.ic_fact_check
import io.github.lopezjavier.appfitness.composeapp.resources.reports_empty_message
import io.github.lopezjavier.appfitness.composeapp.resources.reports_empty_title
import io.github.lopezjavier.appfitness.composeapp.resources.reports_error
import io.github.lopezjavier.appfitness.composeapp.resources.reports_row_open_block
import io.github.lopezjavier.appfitness.composeapp.resources.reports_row_open_week
import io.github.lopezjavier.appfitness.composeapp.resources.reports_row_paused
import io.github.lopezjavier.appfitness.composeapp.resources.reports_title
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_title
import io.github.lopezjavier.appfitness.designsystem.AppDateFormat
import io.github.lopezjavier.appfitness.designsystem.AppListRow
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.ListRowSkeleton
import io.github.lopezjavier.appfitness.designsystem.OfflineState
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.RowGroupCard
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.domain.model.ReportItem
import io.github.lopezjavier.appfitness.domain.model.ReportKind
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val PREFIX = "reports"

/** Stateful "Informes" (US-129): connects [ReportsViewModel]; [onOpen] receives the id of the tapped report. */
@Composable
fun ReportsRoot(
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportsViewModel = koinViewModel(),
    connectivity: ConnectivityObserver = koinInject(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReportsScreen(state, connectivity.isOnline, viewModel::onEvent, onOpen, onBack, modifier)
}

/**
 * "Informes" on a pushed [AppScreen]: the stored weekly summaries and block reviews as rows, newest first as served.
 * One tap opens the report read-only. Loading is a row skeleton, nothing stored an [EmptyState], a failed load an
 * [ErrorState] with "Reintentar" and no connection the offline state (R-31). The list keeps its scroll position
 * through [gridState] (saved with the back stack entry).
 */
@Composable
fun ReportsScreen(
    state: ReportsUiState,
    isOnline: Flow<Boolean>,
    onEvent: (ReportsEvent) -> Unit,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
) {
    AppScreen(
        title = stringResource(Res.string.reports_title),
        modifier = modifier,
        onBack = onBack,
        maxContentWidth = WindowWidthClassDefaults.HubContentMaxWidth,
        testTagPrefix = PREFIX,
    ) {
        Box(Modifier.fillMaxSize().testTag("reports_screen")) {
            when (state) {
                ReportsUiState.Loading -> ListRowSkeleton(
                    Modifier.fillMaxSize().padding(Spacing.screenMargin),
                    testTag = "reports_loading",
                )

                ReportsUiState.Empty -> EmptyState(
                    modifier = Modifier.fillMaxSize(),
                    icon = Res.drawable.ic_description,
                    message = stringResource(Res.string.reports_empty_message),
                    title = stringResource(Res.string.reports_empty_title),
                    testTagPrefix = "reports_empty",
                )

                ReportsUiState.Error -> ErrorState(
                    modifier = Modifier.fillMaxSize(),
                    message = stringResource(Res.string.reports_error),
                    action = AppStateAction(stringResource(Res.string.action_retry)) {
                        onEvent(ReportsEvent.Retry)
                    },
                    testTagPrefix = "reports_error",
                    actionTestTag = "reports_retry",
                )

                ReportsUiState.Offline -> Box(Modifier.fillMaxSize().testTag("reports_offline"), Alignment.Center) {
                    OfflineState(isOnline = isOnline, onReload = { onEvent(ReportsEvent.Retry) })
                }

                is ReportsUiState.Content -> HubGrid(state = gridState) {
                    fullSpanItem("reports") {
                        RowGroupCard(
                            rows = state.items.map { item -> { ReportRow(item, onOpen) } },
                            modifier = Modifier.testTag("reports_group"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportRow(item: ReportItem, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val isBlock = item.kind == ReportKind.BlockReview
    val period = AppDateFormat.dayRange(item.periodStart, item.periodEnd)
    AppListRow(
        headline = stringResource(if (isBlock) Res.string.block_review_title else Res.string.week_summary_title),
        supporting = if (item.paused) stringResource(Res.string.reports_row_paused, period) else period,
        modifier = modifier.padding(horizontal = Spacing.lg).testTag("reports_row_${item.id}"),
        onClick = { onOpen(item.id) },
        onClickLabel = stringResource(
            if (isBlock) Res.string.reports_row_open_block else Res.string.reports_row_open_week,
        ),
        leading = {
            Icon(
                painter = painterResource(if (isBlock) Res.drawable.ic_fact_check else Res.drawable.ic_calendar),
                contentDescription = null,
                tint = AppTheme.colors.textSecondary,
                modifier = Modifier.size(Spacing.xl),
            )
        },
        trailing = {
            Icon(
                painter = painterResource(Res.drawable.ic_chevron_right),
                contentDescription = null,
                tint = AppTheme.colors.textSecondary,
                modifier = Modifier.size(Spacing.xl),
            )
        },
    )
}
