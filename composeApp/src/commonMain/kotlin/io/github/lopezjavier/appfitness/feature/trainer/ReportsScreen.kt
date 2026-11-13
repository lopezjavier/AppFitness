package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import io.github.lopezjavier.appfitness.composeapp.resources.ic_description
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
import io.github.lopezjavier.appfitness.designsystem.appAnimateItem
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
 * through [listState] (saved with the back stack entry).
 */
@Composable
fun ReportsScreen(
    state: ReportsUiState,
    isOnline: Flow<Boolean>,
    onEvent: (ReportsEvent) -> Unit,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    AppScreen(
        title = stringResource(Res.string.reports_title),
        modifier = modifier,
        onBack = onBack,
        testTagPrefix = PREFIX,
    ) {
        Box(Modifier.fillMaxSize().testTag("reports_screen")) {
            when (state) {
                ReportsUiState.Loading -> ListRowSkeleton(
                    Modifier.fillMaxSize().padding(Spacing.screenMargin),
                    testTag = "reports_loading",
                )

                ReportsUiState.Empty -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    EmptyState(
                        icon = Res.drawable.ic_description,
                        message = stringResource(Res.string.reports_empty_message),
                        title = stringResource(Res.string.reports_empty_title),
                        testTagPrefix = "reports_empty",
                    )
                }

                ReportsUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    ErrorState(
                        message = stringResource(Res.string.reports_error),
                        action = AppStateAction(stringResource(Res.string.action_retry)) {
                            onEvent(ReportsEvent.Retry)
                        },
                        testTagPrefix = "reports_error",
                        actionTestTag = "reports_retry",
                    )
                }

                ReportsUiState.Offline -> Box(Modifier.fillMaxSize().testTag("reports_offline"), Alignment.Center) {
                    OfflineState(isOnline = isOnline, onReload = { onEvent(ReportsEvent.Retry) })
                }

                is ReportsUiState.Content -> LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(Spacing.screenMargin),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(state.items, key = { it.id }) { item -> ReportRow(item, onOpen, appAnimateItem()) }
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
        modifier = modifier.testTag("reports_row_${item.id}"),
        onClick = { onOpen(item.id) },
        onClickLabel = stringResource(
            if (isBlock) Res.string.reports_row_open_block else Res.string.reports_row_open_week,
        ),
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
