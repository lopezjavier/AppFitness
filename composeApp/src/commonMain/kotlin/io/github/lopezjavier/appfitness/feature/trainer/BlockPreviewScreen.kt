package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import io.github.lopezjavier.appfitness.composeapp.resources.block_days_week
import io.github.lopezjavier.appfitness.composeapp.resources.block_exercise_fallback
import io.github.lopezjavier.appfitness.composeapp.resources.block_frequency
import io.github.lopezjavier.appfitness.composeapp.resources.block_frequency_chosen
import io.github.lopezjavier.appfitness.composeapp.resources.block_preview_error
import io.github.lopezjavier.appfitness.composeapp.resources.block_preview_interrupted
import io.github.lopezjavier.appfitness.composeapp.resources.block_preview_progress
import io.github.lopezjavier.appfitness.composeapp.resources.block_preview_progress_cd
import io.github.lopezjavier.appfitness.composeapp.resources.block_preview_title
import io.github.lopezjavier.appfitness.composeapp.resources.block_save
import io.github.lopezjavier.appfitness.composeapp.resources.block_save_action
import io.github.lopezjavier.appfitness.composeapp.resources.block_save_error
import io.github.lopezjavier.appfitness.composeapp.resources.block_saved
import io.github.lopezjavier.appfitness.composeapp.resources.block_sets_count
import io.github.lopezjavier.appfitness.composeapp.resources.block_sets_header
import io.github.lopezjavier.appfitness.composeapp.resources.block_sets_row
import io.github.lopezjavier.appfitness.composeapp.resources.block_week_tab
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppListRow
import io.github.lopezjavier.appfitness.designsystem.AppOption
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppTabRow
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ListSectionHeader
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.StreamProgressState
import io.github.lopezjavier.appfitness.designsystem.offlineHint
import io.github.lopezjavier.appfitness.domain.model.BlockTraining
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private const val TITLE_FRACTION = 0.4f
private const val SKELETON_ROWS = 3

private val BlockFailureCopy = CoachFailureCopy(
    error = Res.string.block_preview_error,
    interrupted = Res.string.block_preview_interrupted,
)

/**
 * Stateful block preview (US-72): asks the coach once when it opens with no stored block, plays the one-off effects
 * (haptics, the saved message, leaving the screen) and connects [BlockPreviewViewModel] to [BlockPreviewScreen].
 */
@Composable
fun BlockPreviewRoot(
    planId: String,
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BlockPreviewViewModel = koinViewModel { parametersOf(planId) },
    connectivity: ConnectivityObserver = koinInject(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalAppHaptics.current
    val messages = LocalAppMessages.current
    val latestHaptics by rememberUpdatedState(haptics)
    val latestOnBack by rememberUpdatedState(onBack)
    val saved = stringResource(Res.string.block_saved)
    val saveError = stringResource(Res.string.block_save_error)
    val saveOffline = offlineHint(stringResource(Res.string.block_save_action)) + "."
    LaunchedEffect(state) {
        if (state == BlockPreviewUiState.Idle) viewModel.onEvent(BlockPreviewEvent.Request)
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                BlockPreviewEffect.Saved -> {
                    latestHaptics.confirm()
                    messages.show(saved)
                    latestOnBack()
                }

                is BlockPreviewEffect.ActionFailed -> {
                    latestHaptics.reject()
                    messages.show(if (effect.offline) saveOffline else saveError)
                }
            }
        }
    }
    BlockPreviewScreen(
        state = state,
        isOnline = connectivity.isOnline,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onOpenExercise = onOpenExercise,
        modifier = modifier,
    )
}

/**
 * The four-week training block on [AppScreen] with back: the chosen frequency and the weekly sets per muscle group
 * (they do not change by week, so they sit above the tabs), the week tabs with a card per workout, and a pinned
 * "Guardar bloque" footer. Leaving saves nothing. Loading the stored block is a skeleton, waiting for the model is a
 * [StreamProgressState] (R-31).
 */
@Composable
fun BlockPreviewScreen(
    state: BlockPreviewUiState,
    isOnline: Flow<Boolean>,
    onEvent: (BlockPreviewEvent) -> Unit,
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val online by isOnline.collectAsStateWithLifecycle(initialValue = true)
    AppScreen(
        title = stringResource(Res.string.block_preview_title),
        modifier = modifier,
        onBack = onBack,
        footer = (state as? BlockPreviewUiState.Preview)?.let { { BlockFooter(it, online, onEvent) } },
        testTagPrefix = "block_preview",
    ) {
        Box(Modifier.fillMaxSize().testTag("block_preview_screen")) {
            when (state) {
                BlockPreviewUiState.Loading, BlockPreviewUiState.Idle, BlockPreviewUiState.Closed -> BlockSkeleton()

                BlockPreviewUiState.Requesting -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    StreamProgressState(
                        message = stringResource(Res.string.block_preview_progress),
                        stateDescription = stringResource(Res.string.block_preview_progress_cd),
                        testTag = "block_preview_progress",
                    )
                }

                is BlockPreviewUiState.Failed -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CoachFailureContent(state.kind, isOnline, BlockFailureCopy, "block_preview") {
                        onEvent(BlockPreviewEvent.Retry)
                    }
                }

                is BlockPreviewUiState.Preview -> PreviewContent(state, onEvent, onOpenExercise)
            }
        }
    }
}

@Composable
private fun PreviewContent(
    state: BlockPreviewUiState.Preview,
    onEvent: (BlockPreviewEvent) -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    val training = state.block.training
    val weeks = state.weeks
    val fallback = stringResource(Res.string.block_exercise_fallback)
    LazyColumn(
        Modifier.fillMaxSize(),
        state = rememberLazyListState(),
        contentPadding = PaddingValues(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
    ) {
        item(key = "frequency") { FrequencyLine(training) }
        if (training.weeklySets.isNotEmpty()) {
            item(key = "sets") { SetsSection(training.weeklySets, training.sessionsPerMuscle) }
        }
        if (weeks.size > 1) {
            item(key = "tabs") {
                val options = weeks.map {
                    AppOption(stringResource(Res.string.block_week_tab, it.week), "block_preview_tab_${it.week}")
                }
                AppTabRow(
                    options = options,
                    selectedIndex = weeks.indexOfFirst { it.week == state.selectedWeek }.coerceAtLeast(0),
                    onSelect = { onEvent(BlockPreviewEvent.SelectWeek(weeks[it].week)) },
                )
            }
        }
        item(key = "week_${state.selectedWeek}") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.blockGap)) {
                BlockWeekTab(
                    sessions = state.sessions,
                    exerciseNames = state.exerciseNames,
                    onOpenExercise = onOpenExercise,
                    onOpenReason = { onEvent(BlockPreviewEvent.OpenReason(it)) },
                )
            }
        }
    }
    state.reasonExercise?.let { exercise ->
        ExerciseReasonSheet(
            title = state.exerciseNames[exercise.exerciseId] ?: fallback,
            reason = exercise.reason,
            onDismiss = { onEvent(BlockPreviewEvent.CloseReason) },
        )
    }
}

@Composable
private fun FrequencyLine(training: BlockTraining) {
    val text = if (training.daysChosen) {
        pluralStringResource(
            Res.plurals.block_frequency_chosen,
            training.daysPerWeek,
            training.daysPerWeek,
            training.sessionMinutes,
        )
    } else {
        pluralStringResource(Res.plurals.block_frequency, training.daysPerWeek, training.daysPerWeek)
    }
    Text(
        text = text,
        style = AppTheme.typography.bodyLarge,
        color = AppTheme.colors.textPrimary,
        modifier = Modifier.testTag("block_preview_frequency"),
    )
}

@Composable
private fun SetsSection(weeklySets: Map<String, Int>, sessionsPerMuscle: Map<String, Int>) {
    Column(Modifier.testTag("block_preview_sets_section")) {
        ListSectionHeader(stringResource(Res.string.block_sets_header))
        weeklySets.forEach { (muscle, sets) ->
            val setsText = pluralStringResource(Res.plurals.block_sets_count, sets, sets)
            val days = sessionsPerMuscle[muscle]
            AppListRow(
                headline = blockMuscleName(muscle),
                modifier = Modifier.testTag("block_preview_sets_$muscle"),
                trailing = {
                    Text(
                        text = if (days == null) {
                            setsText
                        } else {
                            stringResource(
                                Res.string.block_sets_row,
                                setsText,
                                pluralStringResource(Res.plurals.block_days_week, days, days),
                            )
                        },
                        style = AppTheme.typography.bodyLarge,
                        color = AppTheme.colors.textPrimary,
                    )
                },
            )
        }
    }
}

/** Pinned footer: "Guardar bloque", disabled offline with the reason (R-30). */
@Composable
private fun BlockFooter(state: BlockPreviewUiState.Preview, online: Boolean, onEvent: (BlockPreviewEvent) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        PrimaryButton(
            text = stringResource(Res.string.block_save),
            onClick = { onEvent(BlockPreviewEvent.Save) },
            modifier = Modifier.fillMaxWidth().testTag("block_preview_save"),
            enabled = online,
            loading = state.saving,
        )
        if (!online) OfflineHint(stringResource(Res.string.block_save_action))
    }
}

/** Card-shaped placeholder while the stored block loads (R-31). */
@Composable
private fun BlockSkeleton() {
    Column(
        Modifier.fillMaxWidth().padding(Spacing.screenMargin).testTag("block_preview_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
    ) {
        repeat(2) {
            AppCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    SkeletonBox(Modifier.fillMaxWidth(TITLE_FRACTION).height(Spacing.xl))
                    repeat(SKELETON_ROWS) { SkeletonBox(Modifier.fillMaxWidth().height(Spacing.minTouchTarget)) }
                }
            }
        }
    }
}
