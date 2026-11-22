@file:Suppress("SpreadOperator") // stringResource takes varargs; the argument lists hold at most four short strings.

package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_empty
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_error
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_next_block
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_title
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_trainer
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_create_action
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.OfflineState
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val SKELETON_LIFT_ROWS = 3
private const val SKELETON_TITLE_FRACTION = 0.5f
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
 * "Revisión del bloque" on [AppScreen] with back and a pinned footer: progress-first read-only cards (strength, main
 * lifts, records, weight, waist, objectives), the phase proposal and a pending calorie proposal. Nothing starts on
 * its own: the footer "Crear el siguiente bloque" is the only way on. Loading is a skeleton, no review an
 * [EmptyState], a failed load an [ErrorState] with "Reintentar" and no connection the offline state (R-31).
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
        maxContentWidth = WindowWidthClassDefaults.HubContentMaxWidth,
        footer = content?.let { { ReviewFooter(it, online, onOpenBeforeStart) } },
        testTagPrefix = PREFIX,
    ) {
        Box(Modifier.fillMaxSize().testTag("block_review_screen")) {
            when (state) {
                BlockReviewUiState.Loading -> ReviewSkeleton()

                BlockReviewUiState.Empty -> EmptyState(
                    modifier = Modifier.fillMaxSize(),
                    icon = Res.drawable.ic_nav_trainer,
                    message = stringResource(Res.string.block_review_empty),
                    testTagPrefix = "block_review_empty",
                )

                BlockReviewUiState.Error -> ErrorState(
                    modifier = Modifier.fillMaxSize(),
                    message = stringResource(Res.string.block_review_error),
                    action = AppStateAction(stringResource(Res.string.action_retry)) {
                        onEvent(BlockReviewEvent.Retry)
                    },
                    testTagPrefix = "block_review_error",
                    actionTestTag = "block_review_retry",
                )

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
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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

/**
 * Placeholders shaped like the loaded review (R-31): the coach card (sentence plus action), the Strength card with its
 * ring and the main-lifts rows.
 */
@Composable
private fun ReviewSkeleton() {
    Column(
        Modifier.fillMaxSize().padding(Spacing.screenMargin).testTag("block_review_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        AppCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                SkeletonBox(Modifier.fillMaxWidth().height(Spacing.xl * 2), AppShapes.md)
                SkeletonBox(Modifier.fillMaxWidth().height(Spacing.minTouchTarget), AppShapes.md)
            }
        }
        AppCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                SkeletonBox(Modifier.fillMaxWidth(SKELETON_TITLE_FRACTION).height(Spacing.lg), AppShapes.sm)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SkeletonBox(Modifier.size(Spacing.minTouchTarget * 2), AppShapes.full)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        SkeletonBox(Modifier.fillMaxWidth().height(Spacing.lg), AppShapes.sm)
                        SkeletonBox(Modifier.fillMaxWidth(SKELETON_TITLE_FRACTION).height(Spacing.lg), AppShapes.sm)
                    }
                }
            }
        }
        AppCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SkeletonBox(Modifier.fillMaxWidth(SKELETON_TITLE_FRACTION).height(Spacing.lg), AppShapes.sm)
                repeat(SKELETON_LIFT_ROWS) {
                    SkeletonBox(Modifier.fillMaxWidth().height(Spacing.minTouchTarget), AppShapes.sm)
                }
            }
        }
    }
}
