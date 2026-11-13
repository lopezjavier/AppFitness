@file:Suppress("SpreadOperator") // stringResource takes varargs; the argument lists hold at most four short strings.

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_accept
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_decision_error
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_empty
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_error
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_groups_header
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_next_block
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_phase_accepted
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_phase_keep
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_phase_kept
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_phase_offline
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_title
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_trainer
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_create_action
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.CoachProposalCard
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.GhostTone
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.OfflineState
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.PhaseDecision
import io.github.lopezjavier.appfitness.domain.model.PhaseProposal
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val SKELETON_CARDS = 3
private const val PREFIX = "block_review"

/** Stateful "Revisión del bloque" (US-101, US-103): connects [BlockReviewViewModel] and plays its haptic effects. */
@Composable
fun BlockReviewRoot(
    onBack: () -> Unit,
    onOpenBeforeStart: (BeforeStartMode, String?, String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BlockReviewViewModel = koinViewModel(),
    connectivity: ConnectivityObserver = koinInject(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = rememberUpdatedState(LocalAppHaptics.current)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is BlockReviewEffect.PhaseDecided, is BlockReviewEffect.CalorieDecided -> haptics.value.confirm()
                BlockReviewEffect.PhaseDecisionFailed, BlockReviewEffect.CalorieFailed -> haptics.value.reject()
            }
        }
    }
    BlockReviewScreen(state, connectivity.isOnline, viewModel::onEvent, onBack, onOpenBeforeStart, modifier)
}

/**
 * "Revisión del bloque" on [AppScreen] with back and a pinned footer: read-only cards (workouts, main lifts, sets per
 * group, weight, outcomes, waist), the phase proposal and a pending calorie proposal. Nothing starts on its own: the
 * footer "Crear el siguiente bloque" is the only way on. Loading is a skeleton, no review an [EmptyState], a failed
 * load an [ErrorState] with "Reintentar" and no connection the offline state (R-31).
 */
@Composable
fun BlockReviewScreen(
    state: BlockReviewUiState,
    isOnline: Flow<Boolean>,
    onEvent: (BlockReviewEvent) -> Unit,
    onBack: () -> Unit,
    onOpenBeforeStart: (BeforeStartMode, String?, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val online by isOnline.collectAsStateWithLifecycle(initialValue = true)
    val content = state as? BlockReviewUiState.Content
    AppScreen(
        title = stringResource(Res.string.block_review_title),
        modifier = modifier,
        onBack = onBack,
        footer = content?.let { { ReviewFooter(it, online, onOpenBeforeStart) } },
        testTagPrefix = PREFIX,
    ) {
        Box(Modifier.fillMaxSize().testTag("block_review_screen")) {
            when (state) {
                BlockReviewUiState.Loading -> ReviewSkeleton()

                BlockReviewUiState.Empty -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    EmptyState(
                        icon = Res.drawable.ic_nav_trainer,
                        message = stringResource(Res.string.block_review_empty),
                        testTagPrefix = "block_review_empty",
                    )
                }

                BlockReviewUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    ErrorState(
                        message = stringResource(Res.string.block_review_error),
                        action = AppStateAction(stringResource(Res.string.action_retry)) {
                            onEvent(BlockReviewEvent.Retry)
                        },
                        testTagPrefix = "block_review_error",
                        actionTestTag = "block_review_retry",
                    )
                }

                BlockReviewUiState.Offline -> Box(
                    Modifier.fillMaxSize().testTag("block_review_offline"),
                    Alignment.Center,
                ) {
                    OfflineState(isOnline = isOnline, onReload = { onEvent(BlockReviewEvent.Retry) })
                }

                is BlockReviewUiState.Content -> ReviewContent(state, online, onEvent)
            }
        }
    }
}

/** "Crear el siguiente bloque": enabled while the phase proposal waits (nothing is forced), off without connection. */
@Composable
private fun ReviewFooter(
    state: BlockReviewUiState.Content,
    online: Boolean,
    onOpenBeforeStart: (BeforeStartMode, String?, String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        PrimaryButton(
            text = stringResource(Res.string.block_review_next_block),
            onClick = {
                onOpenBeforeStart(BeforeStartMode.Block, state.nextPlanId ?: state.review.planId, state.review.planId)
            },
            modifier = Modifier.fillMaxWidth().testTag("block_review_next_block"),
            enabled = online,
        )
        if (!online) OfflineHint(stringResource(Res.string.trainer_block_create_action))
    }
}

@Composable
internal fun ReviewContent(
    state: BlockReviewUiState.Content,
    online: Boolean,
    onEvent: (BlockReviewEvent) -> Unit,
    readOnly: Boolean = false,
) {
    val review = state.review
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
    ) {
        SessionsCard(state)
        LiftsCard(state)
        StallCard(review.stalls, state.exerciseNames, PREFIX)
        GroupsCard(review.groups, PREFIX, stringResource(Res.string.block_review_groups_header))
        WeightCard(state)
        OutcomesCard(state)
        WaistCard(state)
        review.phaseProposal?.let {
            if (readOnly) StoredPhaseSection(state, it) else PhaseProposalSection(state, it, online, onEvent)
        }
        if (!readOnly) state.calorieProposal?.let { CalorieSection(state, online, onEvent) }
    }
}

/** The phase proposal while it waits; once decided, one status line from the stored decision (never from memory). */
@Composable
private fun PhaseProposalSection(
    state: BlockReviewUiState.Content,
    proposal: PhaseProposal,
    online: Boolean,
    onEvent: (BlockReviewEvent) -> Unit,
) {
    when (state.review.phaseDecision) {
        PhaseDecision.Accepted -> StatusCard(
            stringResource(
                Res.string.block_review_phase_accepted,
                stringResource(PhaseText.label(proposal.toPhase)),
            ),
        )

        PhaseDecision.Kept -> StatusCard(stringResource(Res.string.block_review_phase_kept))

        null -> {
            val reason = BlockReviewText.proposalReason(proposal)
            val reasonText = stringResource(reason.res, *reason.args.toTypedArray())
            CoachProposalCard(
                acceptText = stringResource(Res.string.action_accept),
                discardText = stringResource(Res.string.block_review_phase_keep),
                onAccept = { onEvent(BlockReviewEvent.DecidePhase(PhaseDecision.Accepted)) },
                onDiscard = { onEvent(BlockReviewEvent.DecidePhase(PhaseDecision.Kept)) },
                testTagPrefix = "block_review_phase",
                busy = state.deciding != null,
                acceptEnabled = online,
                footer = if (online && state.decisionFailed == null) {
                    null
                } else {
                    {
                        if (!online) OfflineHint(stringResource(Res.string.block_review_phase_offline))
                        if (state.decisionFailed != null) {
                            DecisionFailure("block_review_phase_retry") { onEvent(BlockReviewEvent.RetryDecision) }
                        }
                    }
                },
                content = { PhaseRowContent(proposal.toPhase, reasonText, PREFIX) },
            )
        }
    }
}

@Composable
internal fun StatusCard(text: String, modifier: Modifier = Modifier) {
    SummaryCard("block_review_phase_status") { SummaryLine(text, modifier) }
}

@Composable
private fun CalorieSection(state: BlockReviewUiState.Content, online: Boolean, onEvent: (BlockReviewEvent) -> Unit) {
    val proposal = state.calorieProposal ?: return
    CalorieProposalCard(
        proposal = proposal,
        deciding = state.decidingCalorie,
        online = online,
        onApply = { onEvent(BlockReviewEvent.ApplyCalorie) },
        onNotNow = { onEvent(BlockReviewEvent.NotNowCalorie) },
        testTagPrefix = "block_review_calorie",
        failure = state.calorieFailed?.let { choice ->
            { DecisionFailure("block_review_calorie_retry") { onEvent(choice.toEvent()) } }
        },
    )
}

/** The event that repeats a failed calorie [CalorieChoice]. */
internal fun CalorieChoice.toEvent(): BlockReviewEvent = when (this) {
    CalorieChoice.Apply -> BlockReviewEvent.ApplyCalorie
    CalorieChoice.NotNow -> BlockReviewEvent.NotNowCalorie
}

/** "No se pudo guardar tu decisión" with "Reintentar" inline in the card (R-37); the choice is kept in state. */
@Composable
private fun DecisionFailure(retryTag: String, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = stringResource(Res.string.block_review_decision_error),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.error,
        )
        GhostButton(
            text = stringResource(Res.string.action_retry),
            onClick = onRetry,
            modifier = Modifier.testTag(retryTag),
        )
    }
}

/** Card-shaped placeholders while the review loads (R-31). */
@Composable
private fun ReviewSkeleton() {
    Column(
        Modifier.fillMaxSize().padding(Spacing.screenMargin).testTag("block_review_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
    ) {
        repeat(SKELETON_CARDS) {
            AppCard(Modifier.fillMaxWidth()) { SkeletonBox(Modifier.fillMaxWidth().height(Spacing.xl)) }
        }
    }
}
