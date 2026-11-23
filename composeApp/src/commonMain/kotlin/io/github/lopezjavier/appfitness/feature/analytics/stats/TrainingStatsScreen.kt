package io.github.lopezjavier.appfitness.feature.analytics.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.ic_bar_chart
import io.github.lopezjavier.appfitness.composeapp.resources.stats_empty_message
import io.github.lopezjavier.appfitness.composeapp.resources.stats_empty_title
import io.github.lopezjavier.appfitness.composeapp.resources.stats_error_message
import io.github.lopezjavier.appfitness.composeapp.resources.stats_title
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.ErrorState
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
    onOpenMuscleVolume: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrainingStatsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TrainingStatsScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenExercise = onOpenExercise,
        onBack = onBack,
        onOpenMuscleVolume = onOpenMuscleVolume,
        modifier = modifier,
    )
}

/**
 * "Estadisticas" (US-40, US-41): the range selector stays pinned; below it the progress and records of the range,
 * one context line, the set-count chart, sets per muscle group, principal exercises, the radar and the 7-day body
 * map. Two columns from 600 dp.
 */
@Composable
fun TrainingStatsScreen(
    state: TrainingStatsUiState,
    onEvent: (TrainingStatsEvent) -> Unit,
    onOpenExercise: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenMuscleVolume: () -> Unit = {},
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
            if (state.allFailed) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ErrorState(
                        message = stringResource(Res.string.stats_error_message),
                        action = AppStateAction(stringResource(Res.string.action_retry)) {
                            onEvent(TrainingStatsEvent.Retry())
                        },
                        testTagPrefix = "stats_error",
                    )
                }
                return@BoxWithConstraints
            }
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
                        WideContent(state, onEvent, onOpenExercise, onOpenMuscleVolume)
                    } else {
                        CompactContent(state, onEvent, onOpenExercise, onOpenMuscleVolume)
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
private fun WideContent(
    state: TrainingStatsUiState,
    onEvent: (TrainingStatsEvent) -> Unit,
    onOpenExercise: (String) -> Unit,
    onOpenMuscleVolume: () -> Unit,
) {
    if (state.isEmpty) {
        FrameEmpty()
        BodySection(state, onEvent)
        return
    }
    Row(
        Modifier.fillMaxWidth().testTag("training_stats_two_column"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        // Progress on the left (it can be long); the effort line, distribution and body map on the right.
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.blockGap)) {
            ProgressSections(state, onEvent, onOpenExercise)
            TopExercisesCard(state.topExercises, retry(onEvent, StatsSection.Exercises), onOpenExercise)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.blockGap)) {
            StatsContextLine(state.summary, retry(onEvent, StatsSection.Summary))
            DistributionTop(state, onEvent, onOpenMuscleVolume)
            RadarCard(state.radar, retry(onEvent, StatsSection.Radar))
            BodySection(state, onEvent)
        }
    }
}

/** Progress of the range and its records: the top of the screen (`07` §3 (D1), (D2)). */
@Composable
private fun ProgressSections(
    state: TrainingStatsUiState,
    onEvent: (TrainingStatsEvent) -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    PeriodProgressCard(state.progress, retry(onEvent, StatsSection.Progress), onOpenExercise)
    // C-203: a failure is shown once, by the progress card; the records card only exists with records.
    PeriodRecordsCard(state.records)
}

@Composable
private fun DistributionTop(
    state: TrainingStatsUiState,
    onEvent: (TrainingStatsEvent) -> Unit,
    onOpenMuscleVolume: () -> Unit,
) {
    SetCountCard(state.range, state.setCounts, retry(onEvent, StatsSection.Chart))
    MuscleRowsCard(
        state.muscleSets,
        retry(onEvent, StatsSection.Muscles),
        onOpenMuscleVolume = onOpenMuscleVolume,
    )
}

/**
 * Compact order (`07` §3 (D)): progress, records, the context line, sets per week, sets per muscle group, principal
 * exercises, radar, body map. An empty range keeps only the empty state and the body map.
 */
@Composable
private fun CompactContent(
    state: TrainingStatsUiState,
    onEvent: (TrainingStatsEvent) -> Unit,
    onOpenExercise: (String) -> Unit,
    onOpenMuscleVolume: () -> Unit,
) {
    if (state.isEmpty) {
        FrameEmpty()
    } else {
        ProgressSections(state, onEvent, onOpenExercise)
        StatsContextLine(state.summary, retry(onEvent, StatsSection.Summary))
        DistributionTop(state, onEvent, onOpenMuscleVolume)
        TopExercisesCard(state.topExercises, retry(onEvent, StatsSection.Exercises), onOpenExercise)
        RadarCard(state.radar, retry(onEvent, StatsSection.Radar))
    }
    BodySection(state, onEvent)
}

@Composable
private fun BodySection(state: TrainingStatsUiState, onEvent: (TrainingStatsEvent) -> Unit) {
    if (!state.isFullyEmpty) {
        BodyMapCard(state.bodyMap, retry(onEvent, StatsSection.Body), { onEvent(TrainingStatsEvent.OpenMuscle(it)) })
    }
}

@Composable
private fun FrameEmpty() {
    EmptyState(
        icon = Res.drawable.ic_bar_chart,
        message = stringResource(Res.string.stats_empty_message),
        title = stringResource(Res.string.stats_empty_title),
        testTagPrefix = "stats_empty",
    )
}
