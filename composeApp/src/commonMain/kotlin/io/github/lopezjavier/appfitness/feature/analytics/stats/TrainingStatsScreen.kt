package io.github.lopezjavier.appfitness.feature.analytics.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_range_1m
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_range_1m_cd
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_range_1y
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_range_1y_cd
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_range_3m
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_range_3m_cd
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_range_6m
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_range_6m_cd
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_range_all
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_range_all_cd
import io.github.lopezjavier.appfitness.composeapp.resources.stats_empty_message
import io.github.lopezjavier.appfitness.composeapp.resources.stats_empty_title
import io.github.lopezjavier.appfitness.designsystem.AppOption
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.FilterChipRow
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClass
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.designsystem.windowWidthClassFor
import io.github.lopezjavier.appfitness.domain.model.ProgressRange
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private val RangeOptions = listOf(
    Triple(ProgressRange.OneMonth, Res.string.exercise_progress_range_1m, Res.string.exercise_progress_range_1m_cd),
    Triple(ProgressRange.ThreeMonths, Res.string.exercise_progress_range_3m, Res.string.exercise_progress_range_3m_cd),
    Triple(ProgressRange.SixMonths, Res.string.exercise_progress_range_6m, Res.string.exercise_progress_range_6m_cd),
    Triple(ProgressRange.OneYear, Res.string.exercise_progress_range_1y, Res.string.exercise_progress_range_1y_cd),
    Triple(ProgressRange.AllTime, Res.string.exercise_progress_range_all, Res.string.exercise_progress_range_all_cd),
)

private val RangeTags = mapOf(
    ProgressRange.OneMonth to "stats_frame_1m",
    ProgressRange.ThreeMonths to "stats_frame_3m",
    ProgressRange.SixMonths to "stats_frame_6m",
    ProgressRange.OneYear to "stats_frame_1y",
    ProgressRange.AllTime to "stats_frame_all",
)

/** Stateful "Estadisticas": connects [TrainingStatsViewModel]; [onOpenExercise] opens the progress of an exercise. */
@Composable
fun TrainingStatsRoot(
    onOpenExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrainingStatsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TrainingStatsScreen(state, viewModel::onEvent, onOpenExercise, modifier)
}

/**
 * "Estadisticas" (US-40, US-41): the frame chips stay pinned; below them the key values, set-count chart, sets per
 * muscle group and top exercises of the frame, the radar and the 7-day body map. Two columns from 600 dp.
 */
@Composable
fun TrainingStatsScreen(
    state: TrainingStatsUiState,
    onEvent: (TrainingStatsEvent) -> Unit,
    onOpenExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().testTag("stats_screen"), contentAlignment = Alignment.TopCenter) {
        BoxWithConstraints(Modifier.widthIn(max = WindowWidthClassDefaults.TwoColumnContentMaxWidth).fillMaxSize()) {
            val wide = windowWidthClassFor(maxWidth) == WindowWidthClass.Wide
            Column(Modifier.fillMaxSize()) {
                FrameChips(state.range, onEvent)
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Spacing.screenMargin),
                    verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
                ) {
                    if (wide) {
                        WideContent(
                            state,
                            onEvent,
                            onOpenExercise,
                        )
                    } else {
                        CompactContent(state, onEvent, onOpenExercise)
                    }
                }
            }
        }
    }
    state.openMuscle?.let { group ->
        MuscleDetailSheet(
            group = group,
            detail = state.muscleDetail,
            onRetry = { onEvent(TrainingStatsEvent.Retry(StatsSection.Sheet)) },
            onClose = { onEvent(TrainingStatsEvent.CloseMuscle) },
            onOpenExercise = {
                onEvent(TrainingStatsEvent.CloseMuscle)
                onOpenExercise(it)
            },
        )
    }
}

@Composable
private fun FrameChips(range: ProgressRange, onEvent: (TrainingStatsEvent) -> Unit) {
    val options = RangeOptions.map { (r, label, description) ->
        AppOption(stringResource(label), RangeTags.getValue(r), stringResource(description))
    }
    FilterChipRow(
        options = options,
        selectedIndex = RangeOptions.indexOfFirst { it.first == range },
        onSelect = { onEvent(TrainingStatsEvent.SelectRange(RangeOptions[it].first)) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screenMargin),
        scrollToSelected = true,
    )
}

private fun retry(onEvent: (TrainingStatsEvent) -> Unit, section: StatsSection): () -> Unit =
    { onEvent(TrainingStatsEvent.Retry(section)) }

@Composable
private fun CompactContent(
    state: TrainingStatsUiState,
    onEvent: (TrainingStatsEvent) -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    FrameSections(state, onEvent, onOpenExercise)
    DistributionSections(state, onEvent)
}

@Composable
private fun WideContent(
    state: TrainingStatsUiState,
    onEvent: (TrainingStatsEvent) -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.blockGap)) {
            LeftSections(state, onEvent)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.blockGap)) {
            if (!state.isEmpty) {
                TopExercisesCard(state.topExercises, retry(onEvent, StatsSection.Exercises), onOpenExercise)
                RadarCard(state.radar, retry(onEvent, StatsSection.Radar))
            }
            BodyMapCard(state.bodyMap, retry(onEvent, StatsSection.Body), {
                onEvent(TrainingStatsEvent.OpenMuscle(it))
            })
        }
    }
}

@Composable
private fun LeftSections(state: TrainingStatsUiState, onEvent: (TrainingStatsEvent) -> Unit) {
    if (state.isEmpty) {
        FrameEmpty()
        return
    }
    SummaryTiles(state.summary, retry(onEvent, StatsSection.Summary))
    SetCountCard(state.range, state.setCounts, retry(onEvent, StatsSection.Chart))
    MuscleRowsCard(state.muscleSets, retry(onEvent, StatsSection.Muscles))
}

/** Compact order: tiles, chart, groups, exercises, then the distribution. An empty frame keeps only the body map. */
@Composable
private fun FrameSections(
    state: TrainingStatsUiState,
    onEvent: (TrainingStatsEvent) -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    LeftSections(state, onEvent)
    if (!state.isEmpty) TopExercisesCard(state.topExercises, retry(onEvent, StatsSection.Exercises), onOpenExercise)
}

@Composable
private fun DistributionSections(state: TrainingStatsUiState, onEvent: (TrainingStatsEvent) -> Unit) {
    if (!state.isEmpty) RadarCard(state.radar, retry(onEvent, StatsSection.Radar))
    BodyMapCard(state.bodyMap, retry(onEvent, StatsSection.Body), { onEvent(TrainingStatsEvent.OpenMuscle(it)) })
}

@Composable
private fun FrameEmpty() {
    EmptyState(
        message = stringResource(Res.string.stats_empty_message),
        title = stringResource(Res.string.stats_empty_title),
        testTagPrefix = "stats_empty",
    )
}
