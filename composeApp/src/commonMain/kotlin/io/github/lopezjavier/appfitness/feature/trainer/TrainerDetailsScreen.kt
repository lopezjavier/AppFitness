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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_details_empty
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_details_title
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.usecase.ExerciseNameResolver
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val SKELETON_ROWS = 4

/** Stateful "Detalles": its own [TrainerViewModel] (refreshed on resume) and the shared effects host. */
@Composable
fun TrainerDetailsRoot(
    modifier: Modifier = Modifier,
    actions: TrainerActions = TrainerActions(),
    onBack: () -> Unit = {},
    viewModel: TrainerViewModel = koinViewModel(),
    connectivity: ConnectivityObserver = koinInject(),
    nameResolver: ExerciseNameResolver? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val exerciseNames = rememberExerciseNames(state, nameResolver)
    TrainerEffectsHost(viewModel.effects, { viewModel.onEvent(TrainerEvent.Refresh) }, actions)
    TrainerDetailsScreen(
        state = state,
        isOnline = connectivity.isOnline,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
        actions = actions,
        exerciseNames = exerciseNames,
    )
}

/**
 * "Detalles" (US-120): the applied coach changes, the plan and the block with their full content and actions, the
 * content the Trainer root leaves out. Pushed with a back header.
 */
@Composable
fun TrainerDetailsScreen(
    state: TrainerUiState,
    isOnline: Flow<Boolean>,
    onEvent: (TrainerEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: TrainerActions = TrainerActions(),
    exerciseNames: Map<String, String> = emptyMap(),
) {
    val online by isOnline.collectAsStateWithLifecycle(initialValue = true)
    AppScreen(
        title = stringResource(Res.string.trainer_details_title),
        modifier = modifier,
        onBack = onBack,
        testTagPrefix = "trainer_details",
    ) {
        Box(Modifier.fillMaxSize().testTag("trainer_details_screen")) {
            when (state) {
                TrainerUiState.Loading -> DetailsSkeleton()

                is TrainerUiState.Content -> if (state.hasDetails) {
                    DetailsContent(state, online, onEvent, actions, exerciseNames)
                } else {
                    EmptyState(
                        message = stringResource(Res.string.trainer_details_empty),
                        testTagPrefix = "trainer_details_empty",
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailsContent(
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
        PlanDetailsSection(
            plan = state.plan,
            online = online,
            onOpenBeforeStart = actions.onOpenBeforeStart,
            cardioEdit = state.cardioEdit,
            onEvent = onEvent,
            goalsChecklist = state.goalsChecklist,
            canStartNewBlock = state.canStartNewBlock,
            weeksInPhase = state.weeksInPhase,
            reviewDue = state.blockReviewDue,
            dietBreakSaving = state.dietBreakSaving,
        )
        BlockDetailsSection(state.block, online, state.removeBlockConfirm, onEvent)
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
        CoachChangesSection(
            state,
            state.appliedCoachChanges,
            exerciseNames,
            online,
            onEvent,
            actions.onOpenExercise,
        )
    }
}

/** Card-shaped placeholder while the values load (R-31). */
@Composable
private fun DetailsSkeleton() {
    AppCard(Modifier.fillMaxWidth().padding(Spacing.screenMargin).testTag("trainer_details_loading")) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            repeat(SKELETON_ROWS) { SkeletonBox(Modifier.fillMaxWidth().height(Spacing.minTouchTarget)) }
        }
    }
}
