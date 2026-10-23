@file:Suppress("MatchingDeclarationName") // The file holds the screen and its actions holder.

package io.github.lopezjavier.appfitness.feature.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_detail_select
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_error_message
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_tab_history
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_tab_how_to
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_tab_summary
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.designsystem.AppOption
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTabRow
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.ExerciseLayoutDefaults
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClass
import io.github.lopezjavier.appfitness.designsystem.windowWidthClassFor
import io.github.lopezjavier.appfitness.domain.port.Clock
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** What the detail can ask of its ViewModel besides the load retry; every action defaults to a no-op. */
class ExerciseDetailActions(
    val onSelectTab: (ExerciseDetailTab) -> Unit = {},
    val onRetryRecords: () -> Unit = {},
    val onLoadMoreHistory: () -> Unit = {},
    val onRetryHistory: () -> Unit = {},
    val onRetryHistoryPage: () -> Unit = {},
)

/** Stateful detail: connects [ExerciseDetailViewModel] to the stateless [ExerciseDetailScreen]. */
@Composable
fun ExerciseDetailRoot(
    exerciseId: String,
    selectMode: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenProgress: () -> Unit = {},
    viewModel: ExerciseDetailViewModel = koinViewModel(key = exerciseId, parameters = { parametersOf(exerciseId) }),
    clock: Clock = koinInject(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = remember(viewModel) {
        ExerciseDetailActions(
            onSelectTab = viewModel::selectTab,
            onRetryRecords = viewModel::retryRecords,
            onLoadMoreHistory = viewModel::loadMoreHistory,
            onRetryHistory = viewModel::retryHistory,
            onRetryHistoryPage = viewModel::retryHistoryPage,
        )
    }
    ExerciseDetailScreen(
        state = state,
        selectMode = selectMode,
        onSelect = onSelect,
        onRetry = viewModel::retry,
        today = clock.today(),
        modifier = modifier,
        onOpenProgress = onOpenProgress,
        actions = actions,
    )
}

/**
 * Exercise detail (US-09, US-28): three tabs, "Resumen" (demonstration, header, progress link, records), "Historial"
 * (sessions that contain the exercise) and "Cómo hacerlo" (steps, tips, RepDB attribution). In [selectMode] a
 * "Seleccionar" button is pinned at the bottom of every tab. [today] dates the history rows and the records.
 */
@Composable
fun ExerciseDetailScreen(
    state: ExerciseDetailUiState,
    selectMode: Boolean,
    onSelect: () -> Unit,
    onRetry: () -> Unit,
    today: LocalDate,
    modifier: Modifier = Modifier,
    onOpenProgress: () -> Unit = {},
    actions: ExerciseDetailActions = ExerciseDetailActions(),
) {
    Column(modifier.fillMaxSize().testTag("exercise_detail_screen")) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            when (state) {
                ExerciseDetailUiState.Loading -> DetailLoading()

                ExerciseDetailUiState.Error ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ErrorState(
                            message = stringResource(Res.string.exercise_error_message),
                            action = AppStateAction(stringResource(Res.string.state_retry), onRetry),
                            testTagPrefix = "exercise_detail_error",
                        )
                    }

                is ExerciseDetailUiState.Loaded -> DetailContent(state, today, onOpenProgress, actions)
            }
        }
        if (selectMode && state is ExerciseDetailUiState.Loaded) {
            PrimaryButton(
                text = stringResource(Res.string.exercise_detail_select),
                onClick = onSelect,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenMargin, vertical = Spacing.sm)
                    .testTag("exercise_detail_select_button"),
            )
        }
    }
}

@Composable
private fun DetailLoading() {
    Column(
        Modifier.fillMaxSize().padding(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        SkeletonBox(Modifier.fillMaxWidth().widthIn(max = ExerciseLayoutDefaults.AnimationMaxWidth).padding(Spacing.xl))
        SkeletonBox(Modifier.fillMaxWidth(NAME_FRACTION).padding(Spacing.lg))
    }
}

private const val NAME_FRACTION = 0.6f

@Composable
private fun DetailContent(
    state: ExerciseDetailUiState.Loaded,
    today: LocalDate,
    onOpenProgress: () -> Unit,
    actions: ExerciseDetailActions,
) {
    // Each tab keeps its own scroll state; they are hoisted here so a tab change does not reset the others.
    val summaryScroll = rememberScrollState()
    val howToScroll = rememberScrollState()
    val historyList = rememberLazyListState()
    val options = tabOptions()
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val wide = windowWidthClassFor(maxWidth) == WindowWidthClass.Wide
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            AppTabRow(
                options = options,
                selectedIndex = state.tab.ordinal,
                onSelect = { actions.onSelectTab(ExerciseDetailTab.entries[it]) },
                modifier = Modifier.widthIn(
                    max = ExerciseLayoutDefaults.ContentMaxWidth,
                ).testTag("exercise_detail_tabs"),
            )
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                when (state.tab) {
                    ExerciseDetailTab.Summary -> ExerciseSummaryTab(
                        detail = state.detail,
                        records = state.records,
                        today = today,
                        wide = wide,
                        onOpenProgress = onOpenProgress,
                        onRetryRecords = actions.onRetryRecords,
                        modifier = Modifier.fillMaxSize(),
                        scrollState = summaryScroll,
                    )

                    ExerciseDetailTab.History -> ExerciseHistoryTab(
                        state = state.history,
                        today = today,
                        actions = actions,
                        modifier = Modifier.fillMaxSize(),
                        listState = historyList,
                    )

                    ExerciseDetailTab.HowTo -> ExerciseHowToTab(
                        detail = state.detail,
                        modifier = Modifier.fillMaxSize(),
                        scrollState = howToScroll,
                    )
                }
            }
        }
    }
}

@Composable
private fun tabOptions(): List<AppOption> {
    val summary = stringResource(Res.string.exercise_tab_summary)
    val history = stringResource(Res.string.exercise_tab_history)
    val howTo = stringResource(Res.string.exercise_tab_how_to)
    return remember(summary, history, howTo) {
        listOf(
            AppOption(summary, "exercise_detail_tab_summary"),
            AppOption(history, "exercise_detail_tab_history"),
            AppOption(howTo, "exercise_detail_tab_how_to"),
        )
    }
}
