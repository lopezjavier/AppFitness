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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.coach_ping_button
import io.github.lopezjavier.appfitness.composeapp.resources.coach_ping_offline
import io.github.lopezjavier.appfitness.composeapp.resources.coach_ping_running
import io.github.lopezjavier.appfitness.composeapp.resources.coach_ping_success
import io.github.lopezjavier.appfitness.composeapp.resources.coach_usage_error
import io.github.lopezjavier.appfitness.composeapp.resources.coach_usage_explanation
import io.github.lopezjavier.appfitness.composeapp.resources.coach_usage_figure
import io.github.lopezjavier.appfitness.composeapp.resources.coach_usage_progress_cd
import io.github.lopezjavier.appfitness.composeapp.resources.coach_usage_title
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppLinearProgress
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.OfflineState
import io.github.lopezjavier.appfitness.designsystem.SecondaryButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.CoachBudget
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.CoachUsage
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

private const val PERCENT = 100
private const val FIGURE_FRACTION = 0.5f

/** Stateful "Uso de IA": connects [CoachUsageViewModel] to the stateless [CoachUsageScreen]. */
@Composable
fun CoachUsageRoot(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CoachUsageViewModel = koinViewModel(),
    connectivity: ConnectivityObserver = koinInject(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pingState by viewModel.pingState.collectAsStateWithLifecycle()
    CoachUsageScreen(
        state = state,
        isOnline = connectivity.isOnline,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
        showPing = viewModel.showPing,
        pingState = pingState,
    )
}

/**
 * "Uso de IA" (US-76) on [AppScreen]: this month's spending against the monthly limit, read-only. Loading shows a
 * card-shaped skeleton, a failed load offers "Reintentar", no connection shows the offline state.
 */
@Composable
fun CoachUsageScreen(
    state: CoachUsageUiState,
    isOnline: Flow<Boolean>,
    onEvent: (CoachUsageEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    showPing: Boolean = false,
    pingState: CoachPingUiState = CoachPingUiState.Idle,
) {
    AppScreen(
        title = stringResource(Res.string.coach_usage_title),
        modifier = modifier,
        onBack = onBack,
        testTagPrefix = "coach_usage",
    ) {
        Box(Modifier.fillMaxSize().testTag("coach_usage_screen")) {
            when (state) {
                CoachUsageUiState.Loading -> UsageSkeleton()

                CoachUsageUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    ErrorState(
                        message = stringResource(Res.string.coach_usage_error),
                        action = AppStateAction(stringResource(Res.string.action_retry)) {
                            onEvent(CoachUsageEvent.Retry)
                        },
                        testTagPrefix = "coach_usage_error",
                        actionTestTag = "coach_usage_retry",
                    )
                }

                CoachUsageUiState.Offline -> Box(
                    Modifier.fillMaxSize().testTag("coach_usage_offline"),
                    Alignment.Center,
                ) {
                    OfflineState(isOnline = isOnline, onReload = { onEvent(CoachUsageEvent.Retry) })
                }

                is CoachUsageUiState.Content -> UsageContent(
                    usage = state.usage,
                    showPing = showPing,
                    pingState = pingState,
                    onPing = { onEvent(CoachUsageEvent.Ping) },
                )
            }
        }
    }
}

@Composable
private fun UsageContent(usage: CoachUsage, showPing: Boolean, pingState: CoachPingUiState, onPing: () -> Unit) {
    val percent = (usage.fraction * PERCENT).roundToInt()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
    ) {
        AppCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    text = stringResource(
                        Res.string.coach_usage_figure,
                        AppNumberFormat.euro(usage.costEur),
                        AppNumberFormat.euro(CoachBudget.USER_MONTHLY_LIMIT_EUR),
                    ),
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.testTag("coach_usage_figure"),
                )
                AppLinearProgress(
                    fraction = usage.fraction,
                    stateDescription = stringResource(Res.string.coach_usage_progress_cd, percent),
                    modifier = Modifier.testTag("coach_usage_progress"),
                    fillColor = if (usage.limitReached) AppTheme.colors.energyAmber else null,
                )
                Text(
                    text = stringResource(Res.string.coach_usage_explanation),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.testTag("coach_usage_explanation"),
                )
                if (usage.limitReached) {
                    CoachLimitMessage(
                        failure = CoachFailure.LimitReached(CoachFailure.Scope.User),
                        testTag = "coach_usage_limit_message",
                    )
                }
            }
        }
        if (showPing) PingBlock(pingState, onPing)
    }
}

/** Dev-only: one button that calls the `coach` function with `ping`, plus a plain status line. */
@Composable
private fun PingBlock(state: CoachPingUiState, onPing: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SecondaryButton(
            text = stringResource(Res.string.coach_ping_button),
            onClick = onPing,
            modifier = Modifier.fillMaxWidth().testTag("coach_usage_ping"),
            enabled = state != CoachPingUiState.Running,
        )
        when (state) {
            CoachPingUiState.Idle -> Unit

            is CoachPingUiState.Failed -> CoachLimitMessage(state.failure, testTag = "coach_usage_ping_status")

            else -> Text(
                text = stringResource(
                    when (state) {
                        CoachPingUiState.Success -> Res.string.coach_ping_success
                        CoachPingUiState.Offline -> Res.string.coach_ping_offline
                        else -> Res.string.coach_ping_running
                    },
                ),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.testTag("coach_usage_ping_status"),
            )
        }
    }
}

/** Card-shaped placeholder while the usage loads (R-31): the figure, the bar and the explanation. */
@Composable
private fun UsageSkeleton() {
    AppCard(Modifier.fillMaxWidth().padding(Spacing.screenMargin).testTag("coach_usage_loading")) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SkeletonBox(Modifier.fillMaxWidth(FIGURE_FRACTION).height(Spacing.xl))
            SkeletonBox(Modifier.fillMaxWidth().height(Spacing.sm))
            SkeletonBox(Modifier.fillMaxWidth().height(Spacing.xl))
        }
    }
}
