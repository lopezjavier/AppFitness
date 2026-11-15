package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.designsystem.RowGroupCard
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
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_your_data_row
import io.github.lopezjavier.appfitness.composeapp.resources.your_data_provisional_count
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.AppTopBarDefaults
import io.github.lopezjavier.appfitness.designsystem.NavigationRow
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.WeekPhase
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.usecase.ExerciseNameResolver
import io.github.lopezjavier.appfitness.feature.notifications.NotificationsBell
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private val PlanSkeletonHeight = 218.dp
private val BlockSkeletonHeight = 206.dp
private val WideCardMinHeight = 220.dp
private val YourDataRowHeight = 56.dp
private const val GRID_COLUMNS = 2
private const val WIDE_SETS_LIMIT = 6

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
        BoxWithConstraints(Modifier.fillMaxSize().testTag("trainer_screen")) {
            // Two grid columns fit from about 648 dp (R-91); only then the wide layout adds its extra card (C-128).
            val twoColumns = maxWidth >=
                WindowWidthClassDefaults.HubGridCellMinWidth * GRID_COLUMNS +
                Spacing.lg +
                Spacing.screenMargin * GRID_COLUMNS
            when (state) {
                TrainerUiState.Loading -> TrainerSkeleton()
                is TrainerUiState.Content -> TrainerContent(state, online, onEvent, actions, exerciseNames, twoColumns)
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
    twoColumns: Boolean,
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
        // The two cards of the first row are at least 220 dp high when they sit side by side (C-128).
        val cardMin = if (twoColumns) Modifier.heightIn(min = WideCardMinHeight) else Modifier
        val planEntry: @Composable () -> Unit = {
            PlanEntrySection(
                state.plan,
                online,
                actions.onOpenProposal,
                actions.onOpenBeforeStart,
                actions.onOpenPlan,
                weightPace = state.weightPace,
                weeksInPhase = state.weeksInPhase,
                latestSummary = state.latestWeekSummary,
                currentWeek = state.weeks.firstOrNull { it.phase == WeekPhase.Current },
                modifier = cardMin,
            )
        }
        if (state.plan is PlanSummary.Accepted) item { planEntry() } else fullSpanItem { planEntry() }
        blockBoxes(state, online, actions, onEvent, cardMin)
        if (state.hasWeekSummary) {
            item {
                WeekSummaryBox(
                    summary = state.latestWeekSummary,
                    lastWeek = state.weeks.lastOrNull { it.phase == WeekPhase.Past },
                    onOpen = actions.onOpenWeekSummary,
                )
            }
        }
        // Only two columns have room for it: a phone keeps the root short (US-120).
        val training = (state.block as? BlockSummary.Saved)?.block?.training
        if (twoColumns && training != null && training.weeklySets.isNotEmpty()) {
            item("sets") {
                SetsSection(
                    training.weeklySets,
                    training.sessionsPerMuscle,
                    Modifier,
                    limit = WIDE_SETS_LIMIT,
                    tagPrefix = "trainer_sets",
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
private fun LazyGridScope.blockBoxes(
    state: TrainerUiState.Content,
    online: Boolean,
    actions: TrainerActions,
    onEvent: (TrainerEvent) -> Unit,
    cardModifier: Modifier,
) {
    if (state.blockStatus is BlockStatus.Saved || state.appliedCoachChanges.isNotEmpty()) {
        item {
            BlockSummaryBox(
                state.blockStatus,
                state.weeks,
                appliedChangesCount = state.appliedCoachChanges.size,
                actions = BlockCardActions(
                    online = online,
                    extend = ExtendWeekUi(
                        extension = state.weekExtension,
                        pickerOpen = state.extendWeekPickerOpen,
                        busy = state.extendingWeek,
                        online = online,
                        onOpen = { onEvent(TrainerEvent.OpenExtendWeekPicker) },
                        onDismiss = { onEvent(TrainerEvent.DismissExtendWeekPicker) },
                        onConfirm = { onEvent(TrainerEvent.ExtendWeek(it)) },
                    ),
                    removeConfirm = state.removeBlockConfirm,
                    onOpen = actions.onOpenBlockScreen,
                    onOpenMyTraining = actions.onOpenMyTraining,
                    onEvent = onEvent,
                ),
                modifier = cardModifier,
            )
        }
    }
    val entryShows = state.block is BlockSummary.Proposed || (state.block is BlockSummary.None && !state.blockReviewDue)
    if (entryShows) {
        item("block_entry") {
            BlockEntrySection(
                state.plan,
                state.block,
                online,
                actions.onOpenBlock,
                actions.onOpenBeforeStart,
                state.blockReviewDue,
                cardModifier,
            )
        }
    }
}

/** The "Tus datos" row at the end of the hub (R-03, V-72): a one-row group; its value is how many values are still provisional. */
@Composable
private fun YourDataEntry(provisionalCount: Int?, onOpenYourData: () -> Unit) {
    RowGroupCard(
        rows = listOf {
            NavigationRow(
                label = stringResource(Res.string.trainer_your_data_row),
                onClick = onOpenYourData,
                modifier = Modifier.heightIn(min = YourDataRowHeight).testTag("trainer_your_data"),
                value = provisionalCount?.takeIf { it > 0 }
                    ?.let { pluralStringResource(Res.plurals.your_data_provisional_count, it, it) },
            )
        },
    )
}

/** The tab heading, equal to the bottom-bar label (R-04). */
@Composable
private fun TrainerHeader(title: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Row(
            Modifier
                .widthIn(max = WindowWidthClassDefaults.HubContentMaxWidth)
                .fillMaxWidth()
                .padding(start = Spacing.screenMargin, end = Spacing.xs),
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
}

/** Two card-shaped placeholders and a row while the values load (R-31, C-123): the boxes' own heights. */
@Composable
private fun TrainerSkeleton() {
    Column(Modifier.fillMaxWidth().padding(Spacing.screenMargin).testTag("trainer_loading")) {
        SkeletonBox(Modifier.fillMaxWidth().height(PlanSkeletonHeight), AppShapes.lg)
        Spacer(Modifier.height(Spacing.cardGap))
        SkeletonBox(Modifier.fillMaxWidth().height(BlockSkeletonHeight), AppShapes.lg)
        Spacer(Modifier.height(Spacing.blockGap))
        SkeletonBox(Modifier.fillMaxWidth().height(YourDataRowHeight), AppShapes.lg)
    }
}
