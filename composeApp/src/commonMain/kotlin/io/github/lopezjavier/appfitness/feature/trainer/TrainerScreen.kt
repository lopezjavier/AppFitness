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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.block_replace_invalid
import io.github.lopezjavier.appfitness.composeapp.resources.calorie_proposal_apply_error
import io.github.lopezjavier.appfitness.composeapp.resources.calorie_proposal_decline_error
import io.github.lopezjavier.appfitness.composeapp.resources.calorie_proposal_offline
import io.github.lopezjavier.appfitness.composeapp.resources.cardio_save_error
import io.github.lopezjavier.appfitness.composeapp.resources.cardio_save_offline
import io.github.lopezjavier.appfitness.composeapp.resources.coach_change_decide_error
import io.github.lopezjavier.appfitness.composeapp.resources.coach_change_decide_not_pending
import io.github.lopezjavier.appfitness.composeapp.resources.coach_change_decide_offline
import io.github.lopezjavier.appfitness.composeapp.resources.coach_change_undo_conflict
import io.github.lopezjavier.appfitness.composeapp.resources.coach_change_undo_error
import io.github.lopezjavier.appfitness.composeapp.resources.coach_change_undo_not_found
import io.github.lopezjavier.appfitness.composeapp.resources.coach_change_undo_offline
import io.github.lopezjavier.appfitness.composeapp.resources.coach_change_undone_skipped
import io.github.lopezjavier.appfitness.composeapp.resources.coach_deload_cancel_error
import io.github.lopezjavier.appfitness.composeapp.resources.coach_deload_cancel_offline
import io.github.lopezjavier.appfitness.composeapp.resources.coach_deload_request_error
import io.github.lopezjavier.appfitness.composeapp.resources.coach_deload_request_not_available
import io.github.lopezjavier.appfitness.composeapp.resources.coach_deload_request_offline
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_diet_break_offline
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_remove_action
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_remove_error
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_discomfort_change_action
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_discomfort_save_action
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_discomfort_save_error
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_free_text_row
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_diet_break_error
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_swap_applied
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_title
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_your_data_row
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_title
import io.github.lopezjavier.appfitness.composeapp.resources.your_data_provisional_count
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.designsystem.NavigationRow
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.offlineHint
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.usecase.ExerciseNameResolver
import io.github.lopezjavier.appfitness.feature.notifications.NotificationsBell
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val SKELETON_ROWS = 4
private const val TITLE_FRACTION = 0.4f

/**
 * Where the Trainer root goes: the pending plan proposal, a block preview ([onOpenBlock] takes the plan
 * id), "Antes de empezar" ([onOpenBeforeStart] takes the mode, the plan id and the plan being replaced) and
 * "Tus datos" and "Lo que el entrenador sabe de ti".
 */
data class TrainerActions(
    val onOpenProposal: () -> Unit = {},
    val onOpenBlock: (String) -> Unit = {},
    val onOpenBeforeStart: (mode: BeforeStartMode, planId: String?, replacingPlanId: String?) -> Unit = { _, _, _ -> },
    val onOpenYourData: () -> Unit = {},
    val onOpenFreeText: () -> Unit = {},
    val onOpenWeekSummary: () -> Unit = {},
    val onOpenBlockReview: () -> Unit = {},
    val onOpenExercise: (exerciseId: String) -> Unit = {},
    val onOpenRoutineRange: (routineId: String, exerciseId: String) -> Unit = { _, _ -> },
    val onOpenMyTraining: () -> Unit = {},
)

/** Stateful Trainer tab root: connects [TrainerViewModel] to [TrainerScreen] and plays its one-off effects. */
@Suppress("LongMethod", "CyclomaticComplexMethod") // One failure message per effect, wired in one place.
@Composable
fun TrainerRoute(
    modifier: Modifier = Modifier,
    actions: TrainerActions = TrainerActions(),
    viewModel: TrainerViewModel = koinViewModel(),
    connectivity: ConnectivityObserver = koinInject(),
    nameResolver: ExerciseNameResolver? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalAppHaptics.current
    val messages = LocalAppMessages.current
    val acceptChangeError = stringResource(Res.string.calorie_proposal_apply_error)
    val discardChangeError = stringResource(Res.string.calorie_proposal_decline_error)
    val acceptOffline = offlineHint(stringResource(Res.string.calorie_proposal_offline)) + "."
    val discardOffline = offlineHint(stringResource(Res.string.calorie_proposal_offline)) + "."
    val cardioError = stringResource(Res.string.cardio_save_error)
    val cardioOffline = offlineHint(stringResource(Res.string.cardio_save_offline)) + "."
    val dietBreakError = stringResource(Res.string.trainer_plan_diet_break_error)
    val dietBreakOffline = offlineHint(stringResource(Res.string.plan_proposal_diet_break_offline)) + "."
    val removeBlock = listOf(
        stringResource(Res.string.trainer_block_remove_error),
        offlineHint(stringResource(Res.string.trainer_block_remove_action)) + ".",
    )
    val undoOffline = offlineHint(stringResource(Res.string.coach_change_undo_offline)) + "."
    val undoConflict = stringResource(Res.string.coach_change_undo_conflict)
    val undoNotFound = stringResource(Res.string.coach_change_undo_not_found)
    val undoError = stringResource(Res.string.coach_change_undo_error)
    val decideOffline = offlineHint(stringResource(Res.string.coach_change_decide_offline)) + "."
    val decideNotPending = stringResource(Res.string.coach_change_decide_not_pending)
    val decideError = stringResource(Res.string.coach_change_decide_error)
    val cancelDeloadOffline = offlineHint(stringResource(Res.string.coach_deload_cancel_offline)) + "."
    val cancelDeloadError = stringResource(Res.string.coach_deload_cancel_error)
    val requestDeloadOffline = offlineHint(stringResource(Res.string.coach_deload_request_offline)) + "."
    val requestDeloadError = stringResource(Res.string.coach_deload_request_error)
    val requestDeloadNotAvailable = stringResource(Res.string.coach_deload_request_not_available)
    val swapApplied = stringResource(Res.string.trainer_swap_applied)
    val swapError = stringResource(Res.string.block_replace_invalid)
    val swapOffline = offlineHint(stringResource(Res.string.trainer_discomfort_change_action)) + "."
    val saveError = stringResource(Res.string.trainer_discomfort_save_error)
    val saveOffline = offlineHint(stringResource(Res.string.trainer_discomfort_save_action)) + "."
    val latestHaptics by rememberUpdatedState(haptics)
    val latestActions by rememberUpdatedState(actions)
    val exerciseNames = rememberExerciseNames(state, nameResolver)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(TrainerEvent.Refresh) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                TrainerEffect.CalorieProposalApplied,
                TrainerEffect.CalorieProposalDeclined,
                TrainerEffect.DeloadCancelled,
                TrainerEffect.DeloadRequested,
                TrainerEffect.CardioSaved,
                TrainerEffect.DietBreakSaved,
                TrainerEffect.BlockRemoved,
                -> latestHaptics.confirm()

                is TrainerEffect.DietBreakFailed -> {
                    latestHaptics.reject()
                    messages.show(if (effect.offline) dietBreakOffline else dietBreakError)
                }

                is TrainerEffect.CalorieProposalFailed -> {
                    latestHaptics.reject()
                    messages.show(
                        when {
                            effect.offline -> if (effect.apply) acceptOffline else discardOffline
                            effect.apply -> acceptChangeError
                            else -> discardChangeError
                        },
                    )
                }

                is TrainerEffect.RemoveBlockFailed -> {
                    latestHaptics.reject()
                    messages.show(removeBlock[if (effect.offline) 1 else 0])
                }

                is TrainerEffect.CardioFailed -> {
                    latestHaptics.reject()
                    messages.show(if (effect.offline) cardioOffline else cardioError)
                }

                is TrainerEffect.FlagDecided -> latestHaptics.confirm()

                // The row turns "Deshecho" by itself; only routines edited since need a message (R-13).
                is TrainerEffect.CoachChangeUndone -> {
                    latestHaptics.confirm()
                    if (effect.skipped > 0) {
                        messages.show(
                            getPluralString(Res.plurals.coach_change_undone_skipped, effect.skipped, effect.skipped),
                        )
                    }
                }

                is TrainerEffect.CoachChangeUndoFailed -> {
                    latestHaptics.reject()
                    messages.show(
                        when {
                            effect.offline -> undoOffline
                            effect.kind == CoachChangeUndoFailure.Conflict -> undoConflict
                            effect.kind == CoachChangeUndoFailure.NotFound -> undoNotFound
                            else -> undoError
                        },
                    )
                }

                is TrainerEffect.FlagDecideFailed -> {
                    latestHaptics.reject()
                    messages.show(
                        when {
                            effect.offline -> decideOffline
                            effect.notPending -> decideNotPending
                            else -> decideError
                        },
                    )
                }

                is TrainerEffect.OpenRoutineRange ->
                    latestActions.onOpenRoutineRange(effect.routineId, effect.exerciseId)

                is TrainerEffect.OpenNewBlock ->
                    latestActions.onOpenBeforeStart(BeforeStartMode.NewBlock, effect.planId, null)

                TrainerEffect.OpenMyTraining -> latestActions.onOpenMyTraining()

                TrainerEffect.SwapApplied -> {
                    latestHaptics.confirm()
                    messages.show(swapApplied)
                }

                is TrainerEffect.SwapFailed -> {
                    latestHaptics.reject()
                    messages.show(if (effect.offline) swapOffline else swapError)
                }

                is TrainerEffect.FollowUpActionFailed -> {
                    latestHaptics.reject()
                    messages.show(if (effect.offline) saveOffline else saveError)
                }

                is TrainerEffect.DeloadCancelFailed -> {
                    latestHaptics.reject()
                    messages.show(if (effect.offline) cancelDeloadOffline else cancelDeloadError)
                }

                is TrainerEffect.DeloadRequestFailed -> {
                    latestHaptics.reject()
                    messages.show(
                        when {
                            effect.offline -> requestDeloadOffline
                            effect.notAvailable -> requestDeloadNotAvailable
                            else -> requestDeloadError
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
        actions = actions,
        exerciseNames = exerciseNames,
    )
}

/**
 * Exercise names by id for the coach-change rows (R-54): the shared resolver, read again when the rows change. Empty
 * when the [resolver] is not available or the read fails (a row then shows its reason without a name).
 */
@Composable
private fun rememberExerciseNames(state: TrainerUiState, resolver: ExerciseNameResolver?): Map<String, String> {
    val rows = (state as? TrainerUiState.Content)?.coachChanges.orEmpty()
    val names by produceState(emptyMap<String, String>(), resolver, rows.map { it.exerciseId }) {
        if (resolver != null && rows.isNotEmpty()) value = resolver.resolve().getOrNull()?.byId.orEmpty()
    }
    return names
}

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
    exerciseNames: Map<String, String> = emptyMap(),
) {
    val title = stringResource(Res.string.trainer_title)
    val online by isOnline.collectAsStateWithLifecycle(initialValue = true)
    AppScreen(
        title = title,
        modifier = modifier,
        header = { TrainerHeader(title) },
        testTagPrefix = "trainer",
    ) {
        Box(Modifier.fillMaxSize().testTag("trainer_screen")) {
            when (state) {
                TrainerUiState.Loading -> TrainerSkeleton()
                is TrainerUiState.Content -> TrainerContent(state, online, onEvent, actions, exerciseNames)
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
    exerciseNames: Map<String, String>,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
    ) {
        state.calorieProposal?.let { CalorieProposalCard(it, state.decidingChange, online, onEvent) }
        CoachChangesSection(state, exerciseNames, online, onEvent, actions.onOpenExercise)
        if (state.blockReviewDue) BlockReviewEntry(actions.onOpenBlockReview)
        if (state.welcomeBack && !state.blockReviewDue) WelcomeBackCard(online, onEvent)
        DiscomfortFollowUpsSection(state, online, onEvent, actions.onOpenExercise)
        PlanSection(
            state.plan,
            online,
            actions.onOpenProposal,
            actions.onOpenBeforeStart,
            state.cardioEdit,
            onEvent,
            state.goalsChecklist,
            state.canStartNewBlock,
            state.weeksInPhase,
            state.blockReviewDue,
            state.dietBreakSaving,
        )
        BlockSection(
            state.plan,
            state.block,
            online,
            actions.onOpenBlock,
            actions.onOpenBeforeStart,
            state.removeBlockConfirm,
            onEvent,
            state.blockReviewDue,
        )
        PlanWeeksSection(
            state.weeks,
            DeloadRequestUi(
                available = state.canRequestDeload,
                busy = state.requestingDeload || state.busyChangeId != null,
                online = online,
                onRequest = { onEvent(TrainerEvent.RequestDeload) },
            ),
            state.pause,
            dietBreakWeek = (state.plan as? PlanSummary.Accepted)?.plan?.nutrition?.dietBreak?.week,
        )
        if (state.hasWeekSummary) {
            NavigationRow(
                label = stringResource(Res.string.week_summary_title),
                onClick = actions.onOpenWeekSummary,
                modifier = Modifier.testTag("trainer_row_week_summary"),
            )
        }
        YourDataEntry(state.provisionalCount, actions.onOpenYourData)
        NavigationRow(
            label = stringResource(Res.string.trainer_free_text_row),
            onClick = actions.onOpenFreeText,
            modifier = Modifier.testTag("trainer_free_text_row"),
        )
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
private fun TrainerHeader(title: String) {
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
