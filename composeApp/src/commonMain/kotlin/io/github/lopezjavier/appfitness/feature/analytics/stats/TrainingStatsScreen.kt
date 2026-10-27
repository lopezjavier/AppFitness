package io.github.lopezjavier.appfitness.feature.analytics.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.stats_empty_message
import io.github.lopezjavier.appfitness.composeapp.resources.stats_empty_title
import io.github.lopezjavier.appfitness.composeapp.resources.stats_title
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.TimeRangeSelector
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClass
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.designsystem.windowWidthClassFor
import io.github.lopezjavier.appfitness.feature.analytics.ProgressRanges
import io.github.lopezjavier.appfitness.feature.analytics.toProgressRange
import io.github.lopezjavier.appfitness.feature.analytics.toTimeRange
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Stateful "Estadisticas": connects [TrainingStatsViewModel]; [onOpenExercise] opens the progress of an exercise and
 * [onBack] leaves the screen.
 */
@Composable
fun TrainingStatsRoot(
    onOpenExercise: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrainingStatsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TrainingStatsScreen(state, viewModel::onEvent, onOpenExercise, onBack, modifier)
}

/**
 * "Estadisticas" (US-40, US-41): the range selector stays pinned; below it the key values, set-count chart, sets per
 * muscle group and top exercises of the range, the radar and the 7-day body map. Two columns from 600 dp.
 */
@Composable
fun TrainingStatsScreen(
    state: TrainingStatsUiState,
    onEvent: (TrainingStatsEvent) -> Unit,
    onOpenExercise: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppScreen(
        title = stringResource(Res.string.stats_title),
        modifier = modifier.testTag("stats_screen"),
        onBack = onBack,
        maxContentWidth = WindowWidthClassDefaults.TwoColumnContentMaxWidth,
        testTagPrefix = "stats",
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = windowWidthClassFor(maxWidth) == WindowWidthClass.Wide
            Column(Modifier.fillMaxSize()) {
                TimeRangeSelector(
                    selected = state.range.toTimeRange(),
                    onSelect = { onEvent(TrainingStatsEvent.SelectRange(it.toProgressRange())) },
                    ranges = ProgressRanges,
                    tagPrefix = "stats_range",
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = Spacing.screenMargin,
                        vertical = Spacing.sm,
                    ),
                )
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Spacing.screenMargin),
                    verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
                ) {
                    if (wide) {
                        WideContent(state, onEvent, onOpenExercise)
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
    Row(
        Modifier.fillMaxWidth().testTag("training_stats_two_column"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
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
