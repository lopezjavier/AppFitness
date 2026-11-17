@file:Suppress("TooManyFunctions") // The root: its grid items, states and skeleton are private pieces of it.

package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.ic_fact_check
import io.github.lopezjavier.appfitness.composeapp.resources.ic_insights
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_trainer
import io.github.lopezjavier.appfitness.composeapp.resources.px_trainer_plan_empty_bullet_1
import io.github.lopezjavier.appfitness.composeapp.resources.px_trainer_plan_empty_bullet_2
import io.github.lopezjavier.appfitness.composeapp.resources.px_trainer_plan_empty_bullet_3
import io.github.lopezjavier.appfitness.composeapp.resources.root_error_message
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_create
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_create_action
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_empty_body
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_empty_title
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_reports_row
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_title
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_your_data_row
import io.github.lopezjavier.appfitness.composeapp.resources.your_data_provisional_count
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.AppTopBarDefaults
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.HubDimensions
import io.github.lopezjavier.appfitness.designsystem.IconBadge
import io.github.lopezjavier.appfitness.designsystem.IconBadgeDefaults
import io.github.lopezjavier.appfitness.designsystem.NavigationRow
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.RowGroupCard
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.usecase.ExerciseNameResolver
import io.github.lopezjavier.appfitness.feature.notifications.NotificationsBell
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private val PlanSkeletonHeight = HubDimensions.PlanCardSkeleton
private val BlockSkeletonHeight = HubDimensions.BlockCardSkeleton
private val WideCardMinHeight = HubDimensions.WideCardMin
private val RootRowHeight = HubDimensions.RootRow
private const val GRID_COLUMNS = 2

/**
 * Where the Trainer root goes: the pending plan proposal, a block preview ([onOpenBlock] takes the plan
 * id), "Antes de empezar" ([onOpenBeforeStart] takes the mode, the plan id and the plan being replaced),
 * "Informes y resumen semanal" ([onOpenReports]) and "Tus datos".
 */
data class TrainerActions(
    val onOpenProposal: () -> Unit = {},
    val onOpenBlock: (String) -> Unit = {},
    val onOpenBeforeStart: (mode: BeforeStartMode, planId: String?, replacingPlanId: String?) -> Unit = { _, _, _ -> },
    val onOpenYourData: () -> Unit = {},
    val onOpenReports: () -> Unit = {},
    val onOpenBlockReview: () -> Unit = {},
    val onOpenExercise: (exerciseId: String) -> Unit = {},
    val onOpenRoutineRange: (routineId: String, exerciseId: String) -> Unit = { _, _ -> },
    val onOpenMyTraining: () -> Unit = {},
    val onOpenLiveSession: () -> Unit = {},
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
 * plan" and "Tu bloque" (each with its own ⋮), the applied coach changes, the links group and "Mi entrenamiento" (or
 * the skeleton while it loads). [preview] opens a card menu or preselects a week (the gallery only).
 */
@Composable
@Suppress("LongParameterList") // The state, its events, the navigation, the names and the gallery hook.
fun TrainerScreen(
    state: TrainerUiState,
    isOnline: Flow<Boolean>,
    onEvent: (TrainerEvent) -> Unit,
    modifier: Modifier = Modifier,
    actions: TrainerActions = TrainerActions(),
    exerciseNames: Map<String, String> = emptyMap(),
    preview: TrainerRootPreview = TrainerRootPreview(),
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

                TrainerUiState.Error -> TrainerLoadError("trainer", onEvent)

                is TrainerUiState.Content ->
                    TrainerContent(state, online, onEvent, actions, exerciseNames, twoColumns, preview)
            }
        }
    }
}

/** Gallery hooks of the root: the card whose ⋮ sheet starts open and the week the "Tu bloque" picker starts on. */
data class TrainerRootPreview(val openMenu: TrainerRootMenu? = null, val week: Int? = null)

@Composable
@Suppress("LongParameterList", "LongMethod") // The root's state, events and layout; one grid of fixed items.
private fun TrainerContent(
    state: TrainerUiState.Content,
    online: Boolean,
    onEvent: (TrainerEvent) -> Unit,
    actions: TrainerActions,
    exerciseNames: Map<String, String>,
    twoColumns: Boolean,
    preview: TrainerRootPreview,
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
            val accepted = (state.plan as? PlanSummary.Accepted)?.plan
            PlanEntrySection(
                state.plan,
                online,
                actions.onOpenProposal,
                actions.onOpenBeforeStart,
                weightPace = state.weightPace,
                weeksInPhase = state.weeksInPhase,
                diaryCalories = state.diaryCalories,
                modifier = cardMin,
                menu = accepted?.let { plan ->
                    {
                        PlanCardMenu(
                            plan,
                            online,
                            state.dietBreakSaving,
                            actions.onOpenBeforeStart,
                            onEvent,
                            initiallyOpen = preview.openMenu == TrainerRootMenu.Plan,
                        )
                    }
                },
            )
            state.cardioEdit?.let {
                CardioMinutesSheet(
                    edit = it,
                    online = online,
                    onChange = { minutes -> onEvent(TrainerEvent.CardioMinutesChanged(minutes)) },
                    onSave = { onEvent(TrainerEvent.SaveCardio) },
                    onDismiss = { onEvent(TrainerEvent.DismissCardioEdit) },
                )
            }
        }
        if (state.plan is PlanSummary.Accepted) item { planEntry() } else fullSpanItem { planEntry() }
        blockBoxes(state, online, onEvent, exerciseNames, cardMin, preview)
        blockEntry(state, online, actions, cardMin)
        fullSpanItem("your_data") {
            // 12 dp from the grid plus 12 dp here: 24 dp before the group (C-101).
            Column(Modifier.padding(top = Spacing.md)) {
                RootLinksGroup(state.provisionalCount, actions.onOpenReports, actions.onOpenYourData)
                MyTrainingLink(actions.onOpenMyTraining, Modifier.padding(top = Spacing.sm))
            }
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
                TrainerEmptyHero(
                    icon = Res.drawable.ic_nav_trainer,
                    title = stringResource(Res.string.trainer_plan_empty_title),
                    message = stringResource(Res.string.trainer_plan_empty_body),
                    bullets = listOf(
                        stringResource(Res.string.px_trainer_plan_empty_bullet_1),
                        stringResource(Res.string.px_trainer_plan_empty_bullet_2),
                        stringResource(Res.string.px_trainer_plan_empty_bullet_3),
                    ),
                    tagPrefix = "trainer_empty",
                )
                if (online) {
                    PrimaryButton(
                        text = stringResource(Res.string.trainer_plan_create),
                        onClick = { actions.onOpenBeforeStart(BeforeStartMode.Plan, null, null) },
                        modifier = Modifier.padding(top = Spacing.lg).testTag("trainer_empty_action"),
                    )
                }
                if (!online) OfflineHint(stringResource(Res.string.trainer_plan_create_action))
            }
        }
        RootLinksGroup(provisionalCount, actions.onOpenReports, actions.onOpenYourData)
        MyTrainingLink(actions.onOpenMyTraining, Modifier.padding(top = Spacing.sm))
    }
}

/**
 * The saved block's card with its ⋮ and, right under it in the same grid cell, "Cambios del entrenador" while
 * applied changes exist (also without a saved block). The dialogs the block's ⋮ opens are hosted here.
 */
@Suppress("LongParameterList") // The state, its events, the names, the cell size and the gallery hook.
private fun LazyGridScope.blockBoxes(
    state: TrainerUiState.Content,
    online: Boolean,
    onEvent: (TrainerEvent) -> Unit,
    exerciseNames: Map<String, String>,
    cardModifier: Modifier,
    preview: TrainerRootPreview,
) {
    if (state.blockStatus !is BlockStatus.Saved && state.appliedCoachChanges.isEmpty()) return
    item("block_card") {
        BlockSummaryBox(
            state.blockStatus,
            state.weeks,
            actions = BlockCardActions(onEvent = onEvent),
            modifier = cardModifier,
            today = state.today,
            pause = state.pause,
            dietBreakWeek = (state.plan as? PlanSummary.Accepted)?.plan?.nutrition?.dietBreak?.week,
            menu = {
                BlockCardMenu(state, online, onEvent, initiallyOpen = preview.openMenu == TrainerRootMenu.Block)
            },
            initialWeek = preview.week,
            online = online,
            starting = state.startingRoutine,
        )
        BlockMenuTargets(state, online, onEvent)
    }
    // Its own full-width row: the changes read at the full width, never squeezed into the block's cell (R-107).
    if (state.appliedCoachChanges.isNotEmpty()) {
        fullSpanItem("coach_changes") { CoachChangesCard(state, exerciseNames, online, onEvent) }
    }
}

/** "Crear bloque" or the proposed block, while there is no saved block. */
private fun LazyGridScope.blockEntry(
    state: TrainerUiState.Content,
    online: Boolean,
    actions: TrainerActions,
    cardModifier: Modifier,
) {
    val entryShows = state.block is BlockSummary.Proposed || (state.block is BlockSummary.None && !state.blockReviewDue)
    if (entryShows) {
        item("block_entry") {
            BlockEntrySection(
                state.plan,
                state.block,
                online,
                actions.onOpenBlock,
                actions.onOpenBeforeStart,
                cardModifier,
                state.blockReviewDue,
            )
        }
    }
}

/**
 * The group at the end of the hub (R-03, V-72, C-132): "Informes y resumen semanal" (opens the reports list) then "Tus
 * datos", whose value is how many values are still provisional.
 */
@Composable
private fun RootLinksGroup(provisionalCount: Int?, onOpenReports: () -> Unit, onOpenYourData: () -> Unit) {
    RowGroupCard(
        rows = listOf(
            {
                NavigationRow(
                    label = stringResource(Res.string.trainer_reports_row),
                    onClick = onOpenReports,
                    modifier = Modifier.padding(
                        horizontal = Spacing.lg,
                    ).heightIn(min = RootRowHeight).testTag("trainer_reports"),
                    leading = { LinkBadge(Res.drawable.ic_insights, AppTheme.colors.athleticCyan) },
                )
            },
            {
                NavigationRow(
                    label = stringResource(Res.string.trainer_your_data_row),
                    onClick = onOpenYourData,
                    modifier = Modifier.padding(
                        horizontal = Spacing.lg,
                    ).heightIn(min = RootRowHeight).testTag("trainer_your_data"),
                    value = provisionalCount?.takeIf { it > 0 }
                        ?.let { pluralStringResource(Res.plurals.your_data_provisional_count, it, it) },
                    leading = { LinkBadge(Res.drawable.ic_fact_check, AppTheme.colors.energyAmber) },
                )
            },
        ),
    )
}

@Composable
private fun LinkBadge(icon: DrawableResource, tint: Color) {
    IconBadge(icon, tint, size = IconBadgeDefaults.SmallSize, iconSize = IconBadgeDefaults.SmallIconSize)
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

/** The plan read failed after its retries: the existing error state with "Reintentar" (a new load, not no-plan). */
@Composable
internal fun TrainerLoadError(tagPrefix: String, onEvent: (TrainerEvent) -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ErrorState(
            message = stringResource(Res.string.root_error_message),
            action = AppStateAction(stringResource(Res.string.action_retry)) { onEvent(TrainerEvent.Refresh) },
            testTagPrefix = "${tagPrefix}_error",
        )
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
        SkeletonBox(Modifier.fillMaxWidth().height(RootRowHeight * 2), AppShapes.lg)
    }
}
