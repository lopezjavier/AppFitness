package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_trainer
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_create
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_create_action
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_empty_body
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_empty_title
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_title
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_week_progress
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_your_data_row
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_title
import io.github.lopezjavier.appfitness.composeapp.resources.your_data_provisional_count
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.domain.model.PlanWeek
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.AppTopBarDefaults
import io.github.lopezjavier.appfitness.designsystem.NavigationRow
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.SummaryCard
import io.github.lopezjavier.appfitness.domain.model.WeekPhase
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.usecase.ExerciseNameResolver
import io.github.lopezjavier.appfitness.feature.notifications.NotificationsBell
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val SKELETON_ROWS = 4
private const val TITLE_FRACTION = 0.4f

/**
 * Where the Trainer root goes: the pending plan proposal, a block preview ([onOpenBlock] takes the plan
 * id), "Antes de empezar" ([onOpenBeforeStart] takes the mode, the plan id and the plan being replaced) and
 * the "Tu plan" and "Tu bloque" boxes and "Tus datos".
 */
data class TrainerActions(
    val onOpenProposal: () -> Unit = {},
    val onOpenBlock: (String) -> Unit = {},
    val onOpenBeforeStart: (mode: BeforeStartMode, planId: String?, replacingPlanId: String?) -> Unit = { _, _, _ -> },
    val onOpenYourData: () -> Unit = {},
    val onOpenPlan: () -> Unit = {},
    val onOpenBlockScreen: () -> Unit = {},
    val onOpenWeekSummary: () -> Unit = {},
    val onOpenBlockReview: () -> Unit = {},
    val onOpenExercise: (exerciseId: String) -> Unit = {},
    val onOpenRoutineRange: (routineId: String, exerciseId: String) -> Unit = { _, _ -> },
    val onOpenMyTraining: () -> Unit = {},
)

/** Stateful Trainer tab root: connects [TrainerViewModel] to [TrainerScreen]; effects play in [TrainerEffectsHost]. */
@Composable
fun TrainerRoute(
    modifier: Modifier = Modifier,
    actions: TrainerActions = TrainerActions(),
    viewModel: TrainerViewModel = koinViewModel(),
    connectivity: ConnectivityObserver = koinInject(),
    nameResolver: ExerciseNameResolver? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val exerciseNames = rememberExerciseNames(state, nameResolver)
    TrainerEffectsHost(viewModel.effects, { viewModel.onEvent(TrainerEvent.Refresh) }, actions)
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
internal fun rememberExerciseNames(state: TrainerUiState, resolver: ExerciseNameResolver?): Map<String, String> {
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
        maxContentWidth = WindowWidthClassDefaults.HubContentMaxWidth,
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
    if (state.plan is PlanSummary.None) {
        NoPlanContent(online, state.provisionalCount, actions)
        return
    }
    HubGrid {
        if (state.hasDecisionCards()) {
            fullSpanItem("decisions") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
                    state.calorieProposal?.let { CalorieProposalCard(it, state.decidingChange, online, onEvent) }
                    CoachChangesSection(
                        state,
                        state.pendingCoachChanges,
                        exerciseNames,
                        online,
                        onEvent,
                        actions.onOpenExercise,
                    )
                    if (state.blockReviewDue) BlockReviewEntry(actions.onOpenBlockReview)
                    if (state.welcomeBack && !state.blockReviewDue) WelcomeBackCard(online, onEvent)
                    DiscomfortFollowUpsSection(state, online, onEvent, actions.onOpenExercise)
                }
            }
        }
        val planEntry: @Composable () -> Unit = {
            PlanEntrySection(state.plan, online, actions.onOpenProposal, actions.onOpenBeforeStart, actions.onOpenPlan)
        }
        if (state.plan is PlanSummary.Accepted) item { planEntry() } else fullSpanItem { planEntry() }
        blockBoxes(state, online, actions)
        if (state.hasWeekSummary) {
            item {
                SummaryCard(
                    title = stringResource(Res.string.week_summary_title),
                    lines = weekSummaryFigureLines(state.weeks),
                    onClick = actions.onOpenWeekSummary,
                    testTag = "trainer_box_week_summary",
                )
            }
        }
        fullSpanItem("your_data") {
            // 12 dp from the grid plus 12 dp here: 24 dp before "Tus datos" (C-101).
            Column(Modifier.padding(top = Spacing.md)) { YourDataEntry(state.provisionalCount, actions.onOpenYourData) }
        }
    }
}

private fun TrainerUiState.Content.hasDecisionCards(): Boolean = calorieProposal != null ||
    pendingCoachChanges.isNotEmpty() ||
    blockReviewDue ||
    welcomeBack ||
    discomfortFollowUps.isNotEmpty() ||
    swapPicker != null

/** The figure line of the "Resumen semanal" box (V-50): the last finished week's workouts, when there is one. */
@Composable
private fun weekSummaryFigureLines(weeks: List<PlanWeek>): List<String> {
    val week = weeks.lastOrNull { it.phase == WeekPhase.Past } ?: return emptyList()
    return listOf(stringResource(Res.string.trainer_week_progress, week.completed, week.planned, week.adherencePct))
}

/** The root without a plan (C-111): a centred empty state with "Crear plan" and "Tus datos" below. */
@Composable
private fun NoPlanContent(online: Boolean, provisionalCount: Int?, actions: TrainerActions) {
    Column(Modifier.fillMaxSize().padding(Spacing.screenMargin)) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                EmptyState(
                    icon = Res.drawable.ic_nav_trainer,
                    title = stringResource(Res.string.trainer_plan_empty_title),
                    message = stringResource(Res.string.trainer_plan_empty_body),
                    action = if (online) {
                        AppStateAction(stringResource(Res.string.trainer_plan_create)) {
                            actions.onOpenBeforeStart(BeforeStartMode.Plan, null, null)
                        }
                    } else {
                        null
                    },
                    testTagPrefix = "trainer_empty",
                )
                if (!online) OfflineHint(stringResource(Res.string.trainer_plan_create_action))
            }
        }
        YourDataEntry(provisionalCount, actions.onOpenYourData)
    }
}

/** The block entry ("Crear bloque" or the proposal) and, with a saved block, its box. */
private fun LazyGridScope.blockBoxes(state: TrainerUiState.Content, online: Boolean, actions: TrainerActions) {
    if (state.blockStatus is BlockStatus.Saved || state.appliedCoachChanges.isNotEmpty()) {
        item {
            BlockSummaryBox(
                state.blockStatus,
                state.weeks.firstOrNull { it.phase == WeekPhase.Current },
                deloadPlanned = state.weeks.any { it.deload },
                appliedChangesCount = state.appliedCoachChanges.size,
                onOpen = actions.onOpenBlockScreen,
            )
        }
    }
    val entryShows = state.block is BlockSummary.Proposed || (state.block is BlockSummary.None && !state.blockReviewDue)
    if (entryShows) {
        fullSpanItem("block_entry") {
            BlockEntrySection(
                state.plan,
                state.block,
                online,
                actions.onOpenBlock,
                actions.onOpenBeforeStart,
                state.blockReviewDue,
            )
        }
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
            style = AppTopBarDefaults.titleStyle,
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
