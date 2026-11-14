package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_trainer
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_title
import io.github.lopezjavier.appfitness.composeapp.resources.ic_delete
import io.github.lopezjavier.appfitness.composeapp.resources.ic_more_vert
import io.github.lopezjavier.appfitness.composeapp.resources.topbar_more_cd
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_remove
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_remove_action
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_box_empty_block
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_box_empty_plan
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_section
import io.github.lopezjavier.appfitness.designsystem.AppActionSheet
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppSheetAction
import io.github.lopezjavier.appfitness.designsystem.AppTopBarAction
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.designsystem.offlineHint
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.usecase.ExerciseNameResolver
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val SKELETON_ROWS = 4

/** The summary boxes of the Trainer root that open a pushed screen with their full content (US-120). */
enum class TrainerBox(
    internal val title: StringResource,
    internal val tagPrefix: String,
    internal val emptyMessage: StringResource,
) {
    Plan(Res.string.trainer_plan_section, "trainer_plan", Res.string.trainer_box_empty_plan),
    Block(Res.string.trainer_block_title, "trainer_block", Res.string.trainer_box_empty_block),
}

/** Stateful "Tu plan" / "Tu bloque": its own [TrainerViewModel] (refreshed on resume) and the shared effects host. */
@Composable
fun TrainerBoxRoot(
    box: TrainerBox,
    modifier: Modifier = Modifier,
    actions: TrainerActions = TrainerActions(),
    onBack: () -> Unit = {},
    viewModel: TrainerViewModel = koinViewModel(),
    connectivity: ConnectivityObserver = koinInject(),
    nameResolver: ExerciseNameResolver? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val exerciseNames = rememberExerciseNames(state, nameResolver)
    // The host's ON_RESUME fires once when this screen composes: with the shared state already loaded that is not a
    // resume of the app, so it must not fetch again. Later resumes (back from another app) still refresh.
    // remember is enough: after a recreation the state is already Content, so the first resume is skipped again.
    var skipEntryRefresh by remember { mutableStateOf(state is TrainerUiState.Content) }
    TrainerEffectsHost(
        viewModel.effects,
        {
            if (skipEntryRefresh) skipEntryRefresh = false else viewModel.onEvent(TrainerEvent.Refresh)
        },
        actions,
    )
    TrainerBoxScreen(box, state, connectivity.isOnline, viewModel::onEvent, onBack, modifier, actions, exerciseNames)
}

/**
 * "Tu plan" or "Tu bloque" (US-120): the full content behind a summary box of the Trainer root. "Tu plan" holds the
 * plan, its cardio and diet-break controls and the goals checklist; "Tu bloque" the applied coach changes first, then
 * the saved block with "Eliminar bloque", and its weeks with the deload request and the pause. Pushed with a back
 * arrow.
 */
@Composable
@Suppress("LongParameterList") // The box, the state, its events and where the entries go.
fun TrainerBoxScreen(
    box: TrainerBox,
    state: TrainerUiState,
    isOnline: Flow<Boolean>,
    onEvent: (TrainerEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: TrainerActions = TrainerActions(),
    exerciseNames: Map<String, String> = emptyMap(),
) {
    val online by isOnline.collectAsStateWithLifecycle(initialValue = true)
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val hasSavedBlock = box == TrainerBox.Block && (state as? TrainerUiState.Content)?.block is BlockSummary.Saved
    val moreLabel = stringResource(Res.string.topbar_more_cd)
    AppScreen(
        title = stringResource(box.title),
        modifier = modifier,
        onBack = onBack,
        // "Eliminar bloque" lives in the ⋮ sheet (V-54); the confirmation dialog is unchanged (R-08).
        actions = if (hasSavedBlock) {
            listOf(AppTopBarAction(Res.drawable.ic_more_vert, moreLabel, { menuOpen = true }, "trainer_block_more"))
        } else {
            emptyList()
        },
        maxContentWidth = WindowWidthClassDefaults.HubContentMaxWidth,
        testTagPrefix = box.tagPrefix,
    ) {
        Box(Modifier.fillMaxSize().testTag("${box.tagPrefix}_screen")) {
            when (state) {
                TrainerUiState.Loading -> BoxSkeleton(box.tagPrefix)

                is TrainerUiState.Content -> if (state.hasContent(box)) {
                    BoxContent(box, state, online, onEvent, actions, exerciseNames)
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon = Res.drawable.ic_nav_trainer,
                            message = stringResource(box.emptyMessage),
                            testTagPrefix = "${box.tagPrefix}_empty",
                        )
                    }
                }
            }
            if (menuOpen && hasSavedBlock) {
                AppActionSheet(
                    actions = listOf(
                        AppSheetAction(
                            icon = Res.drawable.ic_delete,
                            title = stringResource(Res.string.trainer_block_remove),
                            hint = if (online) null else offlineHint(stringResource(Res.string.trainer_block_remove_action)),
                            testTag = "trainer_remove_block",
                            enabled = online,
                            onClick = { onEvent(TrainerEvent.RequestRemoveBlock) },
                        ),
                    ),
                    onDismiss = { menuOpen = false },
                    testTagPrefix = "trainer_block_menu",
                )
            }
        }
    }
}

private fun TrainerUiState.Content.hasContent(box: TrainerBox): Boolean = when (box) {
    TrainerBox.Plan -> plan is PlanSummary.Accepted
    TrainerBox.Block -> block is BlockSummary.Saved || appliedCoachChanges.isNotEmpty()
}

@Composable
@Suppress("LongParameterList") // The box, the state, its events and where the entries go.
private fun BoxContent(
    box: TrainerBox,
    state: TrainerUiState.Content,
    online: Boolean,
    onEvent: (TrainerEvent) -> Unit,
    actions: TrainerActions,
    exerciseNames: Map<String, String>,
) {
    HubGrid {
        when (box) {
            TrainerBox.Plan -> planDetails(
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

            // The applied changes come first so that "Deshacer" is visible without scrolling past the weeks.
            TrainerBox.Block -> {
                if (state.appliedCoachChanges.isNotEmpty()) {
                    fullSpanItem("applied_changes") {
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
                if (state.block is BlockSummary.Saved) fullSpanItem("block_card") {
                    BlockDetailsSection(state.block, state.blockStatus, state.removeBlockConfirm, onEvent)
                }
                planWeeks(
                    state.weeks,
                    DeloadRequestUi(
                        available = state.canRequestDeload,
                        busy = state.requestingDeload || state.busyChangeId != null,
                        online = online,
                        onRequest = { onEvent(TrainerEvent.RequestDeload) },
                    ),
                    state.pause,
                    dietBreakWeek = (state.plan as? PlanSummary.Accepted)?.plan?.nutrition?.dietBreak?.week,
                    extend = ExtendWeekUi(
                        extension = state.weekExtension,
                        pickerOpen = state.extendWeekPickerOpen,
                        busy = state.extendingWeek,
                        online = online,
                        onOpen = { onEvent(TrainerEvent.OpenExtendWeekPicker) },
                        onDismiss = { onEvent(TrainerEvent.DismissExtendWeekPicker) },
                        onConfirm = { onEvent(TrainerEvent.ExtendWeek(it)) },
                    ),
                )
            }
        }
    }
}

/** Card-shaped placeholder while the values load (R-31). */
@Composable
private fun BoxSkeleton(tagPrefix: String) {
    AppCard(Modifier.fillMaxWidth().padding(Spacing.screenMargin).testTag("${tagPrefix}_loading")) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            repeat(SKELETON_ROWS) { SkeletonBox(Modifier.fillMaxWidth().height(Spacing.minTouchTarget)) }
        }
    }
}
