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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.calorie_change_accept_error
import io.github.lopezjavier.appfitness.composeapp.resources.calorie_change_accept_offline
import io.github.lopezjavier.appfitness.composeapp.resources.calorie_change_discard_error
import io.github.lopezjavier.appfitness.composeapp.resources.calorie_change_discard_offline
import io.github.lopezjavier.appfitness.composeapp.resources.cardio_save_error
import io.github.lopezjavier.appfitness.composeapp.resources.cardio_save_offline
import io.github.lopezjavier.appfitness.composeapp.resources.coach_usage_title
import io.github.lopezjavier.appfitness.composeapp.resources.deload_accept_error
import io.github.lopezjavier.appfitness.composeapp.resources.deload_accept_offline
import io.github.lopezjavier.appfitness.composeapp.resources.deload_discard_error
import io.github.lopezjavier.appfitness.composeapp.resources.deload_discard_offline
import io.github.lopezjavier.appfitness.composeapp.resources.ic_info
import io.github.lopezjavier.appfitness.composeapp.resources.ic_more_vert
import io.github.lopezjavier.appfitness.composeapp.resources.topbar_more_cd
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_title
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_your_data_row
import io.github.lopezjavier.appfitness.composeapp.resources.your_data_provisional_count
import io.github.lopezjavier.appfitness.designsystem.AppActionSheet
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppSheetAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.designsystem.NavigationRow
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.offlineHint
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.feature.notifications.NotificationsBell
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val SKELETON_ROWS = 4
private const val TITLE_FRACTION = 0.4f

/**
 * Where the Trainer root goes: "Uso de IA", the pending plan proposal, a block preview ([onOpenBlock] takes the plan
 * id), "Antes de empezar" ([onOpenBeforeStart] takes the mode, the plan id and the plan being replaced) and
 * "Tus datos".
 */
data class TrainerActions(
    val onOpenCoachUsage: () -> Unit = {},
    val onOpenProposal: () -> Unit = {},
    val onOpenBlock: (String) -> Unit = {},
    val onOpenBeforeStart: (mode: BeforeStartMode, planId: String?, replacingPlanId: String?) -> Unit = { _, _, _ -> },
    val onOpenYourData: () -> Unit = {},
)

/** Stateful Trainer tab root: connects [TrainerViewModel] to [TrainerScreen] and plays its one-off effects. */
@Composable
fun TrainerRoute(
    modifier: Modifier = Modifier,
    actions: TrainerActions = TrainerActions(),
    viewModel: TrainerViewModel = koinViewModel(),
    connectivity: ConnectivityObserver = koinInject(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalAppHaptics.current
    val messages = LocalAppMessages.current
    val acceptChangeError = stringResource(Res.string.calorie_change_accept_error)
    val discardChangeError = stringResource(Res.string.calorie_change_discard_error)
    val acceptOffline = offlineHint(stringResource(Res.string.calorie_change_accept_offline)) + "."
    val discardOffline = offlineHint(stringResource(Res.string.calorie_change_discard_offline)) + "."
    val acceptDeloadError = stringResource(Res.string.deload_accept_error)
    val discardDeloadError = stringResource(Res.string.deload_discard_error)
    val deloadAcceptOffline = offlineHint(stringResource(Res.string.deload_accept_offline)) + "."
    val deloadDiscardOffline = offlineHint(stringResource(Res.string.deload_discard_offline)) + "."
    val cardioError = stringResource(Res.string.cardio_save_error)
    val cardioOffline = offlineHint(stringResource(Res.string.cardio_save_offline)) + "."
    val latestHaptics by rememberUpdatedState(haptics)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(TrainerEvent.Refresh) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
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

                is TrainerEffect.DeloadDecided -> latestHaptics.confirm()

                TrainerEffect.CardioSaved -> latestHaptics.confirm()

                is TrainerEffect.CardioFailed -> {
                    latestHaptics.reject()
                    messages.show(if (effect.offline) cardioOffline else cardioError)
                }

                is TrainerEffect.DeloadFailed -> {
                    latestHaptics.reject()
                    messages.show(
                        decisionMessage(
                            effect.accept,
                            effect.offline,
                            listOf(acceptDeloadError, discardDeloadError, deloadAcceptOffline, deloadDiscardOffline),
                        ),
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
        actions = actions,
    )
}

/** Picks the failure message from `[acceptError, discardError, acceptOffline, discardOffline]`. */
private fun decisionMessage(accept: Boolean, offline: Boolean, texts: List<String>): String =
    texts[(if (offline) 2 else 0) + (if (accept) 0 else 1)]

/**
 * The Trainer tab root (US-68, US-69, US-78) on [AppScreen] with the compact header: the pending suggestions, "Tu
 * plan", the block entry, the weeks and the "Tus datos" row (or the skeleton while it loads).
 */
@Composable
fun TrainerScreen(
    state: TrainerUiState,
    isOnline: Flow<Boolean>,
    onEvent: (TrainerEvent) -> Unit,
    modifier: Modifier = Modifier,
    actions: TrainerActions = TrainerActions(),
) {
    val title = stringResource(Res.string.trainer_title)
    val online by isOnline.collectAsStateWithLifecycle(initialValue = true)
    AppScreen(
        title = title,
        modifier = modifier,
        header = { TrainerHeader(title, actions.onOpenCoachUsage) },
        testTagPrefix = "trainer",
    ) {
        Box(Modifier.fillMaxSize().testTag("trainer_screen")) {
            when (state) {
                TrainerUiState.Loading -> TrainerSkeleton()
                is TrainerUiState.Content -> TrainerContent(state, online, onEvent, actions)
            }
        }
    }
}

@Composable
private fun TrainerContent(
    state: TrainerUiState.Content,
    online: Boolean,
    onEvent: (TrainerEvent) -> Unit,
    actions: TrainerActions,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
    ) {
        state.deload?.let { DeloadCard(it, state.decidingDeload, online, onEvent) }
        state.calorieChange?.let { CalorieChangeCard(it, state.decidingChange, online, onEvent) }
        PlanSection(state.plan, online, actions.onOpenProposal, actions.onOpenBeforeStart, state.cardioEdit, onEvent)
        BlockSection(state.plan, state.block, online, actions.onOpenBlock, actions.onOpenBeforeStart)
        PlanWeeksSection(state.weeks)
        YourDataEntry(state.provisionalCount, actions.onOpenYourData)
    }
}

/** The "Tus datos" row at the end of the hub (R-03): its value is how many values are still provisional. */
@Composable
private fun YourDataEntry(provisionalCount: Int?, onOpenYourData: () -> Unit) {
    NavigationRow(
        label = stringResource(Res.string.trainer_your_data_row),
        onClick = onOpenYourData,
        modifier = Modifier.testTag("trainer_your_data"),
        value = provisionalCount?.takeIf { it > 0 }
            ?.let { pluralStringResource(Res.plurals.your_data_provisional_count, it, it) },
    )
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
