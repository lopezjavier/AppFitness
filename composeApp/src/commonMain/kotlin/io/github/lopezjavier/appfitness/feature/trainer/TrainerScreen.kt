package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.calorie_change_accept_error
import io.github.lopezjavier.appfitness.composeapp.resources.calorie_change_accept_offline
import io.github.lopezjavier.appfitness.composeapp.resources.calorie_change_discard_error
import io.github.lopezjavier.appfitness.composeapp.resources.calorie_change_discard_offline
import io.github.lopezjavier.appfitness.composeapp.resources.coach_usage_title
import io.github.lopezjavier.appfitness.composeapp.resources.ic_info
import io.github.lopezjavier.appfitness.composeapp.resources.ic_more_vert
import io.github.lopezjavier.appfitness.composeapp.resources.topbar_more_cd
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_confirm_error
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_error_message
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_save_error
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_title
import io.github.lopezjavier.appfitness.designsystem.AppActionSheet
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppSheetAction
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.designsystem.OfflineState
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.offlineHint
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.feature.notifications.NotificationsBell
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val SKELETON_ROWS = 4
private const val TITLE_FRACTION = 0.4f

/** Stateful Trainer tab root: connects [TrainerViewModel] to [TrainerScreen] and plays its one-off effects. */
@Composable
fun TrainerRoute(
    modifier: Modifier = Modifier,
    onOpenCoachUsage: () -> Unit = {},
    onOpenPlan: () -> Unit = {},
    onOpenBlock: (String) -> Unit = {},
    viewModel: TrainerViewModel = koinViewModel(),
    connectivity: ConnectivityObserver = koinInject(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalAppHaptics.current
    val messages = LocalAppMessages.current
    val confirmError = stringResource(Res.string.trainer_confirm_error)
    val saveError = stringResource(Res.string.trainer_save_error)
    val acceptChangeError = stringResource(Res.string.calorie_change_accept_error)
    val discardChangeError = stringResource(Res.string.calorie_change_discard_error)
    val acceptOffline = offlineHint(stringResource(Res.string.calorie_change_accept_offline)) + "."
    val discardOffline = offlineHint(stringResource(Res.string.calorie_change_discard_offline)) + "."
    val latestHaptics by rememberUpdatedState(haptics)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                TrainerEffect.Confirmed -> latestHaptics.confirm()

                is TrainerEffect.ConfirmFailed -> {
                    latestHaptics.reject()
                    messages.show(if (effect.fromSheet) saveError else confirmError)
                }

                is TrainerEffect.CalorieChangeDecided -> latestHaptics.confirm()

                is TrainerEffect.CalorieChangeFailed -> {
                    latestHaptics.reject()
                    messages.show(
                        when {
                            effect.offline -> if (effect.accept) acceptOffline else discardOffline
                            effect.accept -> acceptChangeError
                            else -> discardChangeError
                        },
                    )
                }
            }
        }
    }
    TrainerScreen(
        state = state,
        isOnline = connectivity.isOnline,
        onEvent = viewModel::onEvent,
        modifier = modifier,
        onOpenCoachUsage = onOpenCoachUsage,
        onOpenPlan = onOpenPlan,
        onOpenBlock = onOpenBlock,
    )
}

/**
 * The Trainer tab root (US-68, US-69) on [AppScreen] with the compact header: the pending calorie change, the
 * derived-values card (or its skeleton), the plan entry, the load error with "Reintentar" or the offline state.
 * The activity-level sheet floats over the card.
 */
@Composable
fun TrainerScreen(
    state: TrainerUiState,
    isOnline: Flow<Boolean>,
    onEvent: (TrainerEvent) -> Unit,
    modifier: Modifier = Modifier,
    onOpenCoachUsage: () -> Unit = {},
    onOpenPlan: () -> Unit = {},
    onOpenBlock: (String) -> Unit = {},
) {
    val title = stringResource(Res.string.trainer_title)
    val online by isOnline.collectAsStateWithLifecycle(initialValue = true)
    AppScreen(
        title = title,
        modifier = modifier,
        header = { TrainerHeader(title, onOpenCoachUsage) },
        testTagPrefix = "trainer",
    ) {
        Box(Modifier.fillMaxSize().testTag("trainer_screen")) {
            when (state) {
                TrainerUiState.Loading -> TrainerSkeleton()

                TrainerUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    ErrorState(
                        message = stringResource(Res.string.trainer_error_message),
                        action = AppStateAction(stringResource(Res.string.action_retry)) {
                            onEvent(TrainerEvent.Retry)
                        },
                        testTagPrefix = "trainer_error",
                        actionTestTag = "trainer_retry",
                    )
                }

                TrainerUiState.Offline -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    OfflineState(isOnline = isOnline, onReload = { onEvent(TrainerEvent.Retry) })
                }

                is TrainerUiState.Content -> TrainerContent(state, online, onEvent, onOpenPlan, onOpenBlock)
            }
        }
    }
}

@Composable
private fun TrainerContent(
    state: TrainerUiState.Content,
    online: Boolean,
    onEvent: (TrainerEvent) -> Unit,
    onOpenPlan: () -> Unit,
    onOpenBlock: (String) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
    ) {
        state.calorieChange?.let { CalorieChangeCard(it, state.decidingChange, online, onEvent) }
        DerivedValuesCard(
            values = state.values,
            confirming = state.confirming,
            online = online,
            strategy = state.strategy,
            onEdit = { onEvent(TrainerEvent.OpenEdit) },
            onConfirm = { onEvent(TrainerEvent.Confirm) },
        )
        PlanSection(state.plan, online, onOpenPlan)
        BlockSection(state.plan, state.block, online, onOpenBlock)
    }
    if (state.editSheet != null) {
        ActivityLevelSheet(
            selected = state.editSheet,
            saving = state.confirming,
            online = online,
            onEvent = onEvent,
        )
    }
}

/** The tab heading, equal to the bottom-bar label (R-04). */
@Composable
private fun TrainerHeader(title: String, onOpenCoachUsage: () -> Unit) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.screenMargin, end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = AppTheme.typography.titleLarge,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        NotificationsBell("trainer_action_notifications")
        AppIconButton(
            contentDescription = stringResource(Res.string.topbar_more_cd),
            icon = Res.drawable.ic_more_vert,
            testTag = "trainer_overflow",
            onClick = { menuOpen = true },
        )
        if (menuOpen) {
            val usageTitle = stringResource(Res.string.coach_usage_title)
            AppActionSheet(
                actions = listOf(
                    AppSheetAction(
                        icon = Res.drawable.ic_info,
                        title = usageTitle,
                        testTag = "trainer_action_coach_usage",
                        onClick = onOpenCoachUsage,
                    ),
                ),
                onDismiss = { menuOpen = false },
                testTagPrefix = "trainer_overflow_sheet",
            )
        }
    }
}

/** Card-shaped placeholder while the values load (R-31): a title bar, one bar per value and the button. */
@Composable
private fun TrainerSkeleton() {
    AppCard(Modifier.fillMaxWidth().padding(Spacing.screenMargin).testTag("trainer_loading")) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SkeletonBox(Modifier.fillMaxWidth(TITLE_FRACTION).height(Spacing.xl))
            repeat(SKELETON_ROWS) { SkeletonBox(Modifier.fillMaxWidth().height(Spacing.minTouchTarget)) }
            SkeletonBox(Modifier.fillMaxWidth().height(Spacing.minTouchTarget), AppShapes.lg)
        }
    }
}
