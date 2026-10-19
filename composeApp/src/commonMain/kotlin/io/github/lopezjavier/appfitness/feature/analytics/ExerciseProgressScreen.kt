@file:Suppress("TooManyFunctions") // One small composable per part of the screen.

package io.github.lopezjavier.appfitness.feature.analytics

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.chart_date_short
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_chart_1rm
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_chart_cd
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_chart_max_reps
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_chart_volume
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_empty_message
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_error_message
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_range_1m
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_range_1m_cd
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_range_1y
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_range_1y_cd
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_range_3m
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_range_3m_cd
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_range_6m
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_range_6m_cd
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_single_session
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_single_value
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_unnamed
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppLineChart
import io.github.lopezjavier.appfitness.designsystem.AppLineChartBlock
import io.github.lopezjavier.appfitness.designsystem.AppLineChartSkeleton
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppOption
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.SegmentedSelector
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClass
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.designsystem.windowWidthClassFor
import io.github.lopezjavier.appfitness.domain.model.ChartPoint
import io.github.lopezjavier.appfitness.domain.model.ExerciseProgress
import io.github.lopezjavier.appfitness.domain.model.ProgressMode
import io.github.lopezjavier.appfitness.domain.model.ProgressRange
import io.github.lopezjavier.appfitness.feature.workout.shortLabel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt

private val RangeOptions = listOf(
    Triple(ProgressRange.OneMonth, Res.string.exercise_progress_range_1m, Res.string.exercise_progress_range_1m_cd),
    Triple(ProgressRange.ThreeMonths, Res.string.exercise_progress_range_3m, Res.string.exercise_progress_range_3m_cd),
    Triple(ProgressRange.SixMonths, Res.string.exercise_progress_range_6m, Res.string.exercise_progress_range_6m_cd),
    Triple(ProgressRange.OneYear, Res.string.exercise_progress_range_1y, Res.string.exercise_progress_range_1y_cd),
)

private val RangeTags = mapOf(
    ProgressRange.OneMonth to "exercise_progress_range_1m",
    ProgressRange.ThreeMonths to "exercise_progress_range_3m",
    ProgressRange.SixMonths to "exercise_progress_range_6m",
    ProgressRange.OneYear to "exercise_progress_range_1y",
)

/** Stateful progress of one exercise: connects [ExerciseProgressViewModel]. */
@Composable
fun ExerciseProgressRoot(
    exerciseId: String,
    modifier: Modifier = Modifier,
    viewModel: ExerciseProgressViewModel = koinViewModel(key = exerciseId, parameters = { parametersOf(exerciseId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ExerciseProgressScreen(state, viewModel::onEvent, modifier)
}

/**
 * Progress of one exercise (US-15): the range filter on top, then the 1RM chart and the volume chart (or the max-reps
 * chart for an exercise never logged with load). A single session shows its value and a hint instead of a chart.
 */
@Composable
fun ExerciseProgressScreen(
    state: ExerciseProgressUiState,
    onEvent: (ExerciseProgressEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().testTag("exercise_progress_screen"), contentAlignment = Alignment.TopCenter) {
        when (state) {
            ExerciseProgressUiState.Loading -> ProgressLoading()

            ExerciseProgressUiState.Empty -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    message = stringResource(Res.string.exercise_progress_empty_message),
                    testTagPrefix = "exercise_progress_empty",
                )
            }

            ExerciseProgressUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ErrorState(
                    message = stringResource(Res.string.exercise_progress_error_message),
                    action = AppStateAction(stringResource(Res.string.state_retry)) {
                        onEvent(ExerciseProgressEvent.Retry)
                    },
                    testTagPrefix = "exercise_progress_error",
                )
            }

            is ExerciseProgressUiState.Content -> ProgressContent(state, onEvent)
        }
    }
}

@Composable
private fun ProgressContent(state: ExerciseProgressUiState.Content, onEvent: (ExerciseProgressEvent) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val wide = windowWidthClassFor(maxWidth) != WindowWidthClass.Compact
        Column(
            Modifier
                .widthIn(
                    max = if (wide) {
                        WindowWidthClassDefaults.TwoColumnContentMaxWidth
                    } else {
                        WindowWidthClassDefaults.FormContentMaxWidth
                    },
                )
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
        ) {
            ExerciseTitle(state.name)
            RangeSelector(state.range) { onEvent(ExerciseProgressEvent.SelectRange(it)) }
            ProgressCharts(state.progress, wide)
        }
    }
}

@Composable
private fun ExerciseTitle(name: String?) {
    Text(
        text = name ?: stringResource(Res.string.exercise_unnamed),
        style = AppTheme.typography.titleLarge,
        color = AppTheme.colors.textPrimary,
        modifier = Modifier.semantics { heading() }.testTag("exercise_progress_name"),
    )
}

@Composable
private fun RangeSelector(selected: ProgressRange, onSelect: (ProgressRange) -> Unit) {
    val options = RangeOptions.map { (range, label, description) ->
        AppOption(stringResource(label), RangeTags.getValue(range), stringResource(description))
    }
    SegmentedSelector(
        options = options,
        selectedIndex = RangeOptions.indexOfFirst { it.first == selected },
        onSelect = { onSelect(RangeOptions[it].first) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ProgressCharts(progress: ExerciseProgress, wide: Boolean) {
    if (!progress.hasEnoughData) {
        NotEnoughData(progress)
        return
    }
    val primary = @Composable { PrimaryChartCard(progress, Modifier.fillMaxWidth()) }
    val volume = @Composable {
        if (progress.volume.size >= 2) VolumeChartCard(progress.volume, Modifier.fillMaxWidth())
    }
    if (wide) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
            Box(Modifier.weight(1f)) { primary() }
            Box(Modifier.weight(1f)) { volume() }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
            primary()
            volume()
        }
    }
}

@Composable
private fun PrimaryChartCard(progress: ExerciseProgress, modifier: Modifier = Modifier) {
    val maxReps = progress.mode == ProgressMode.MaxReps
    val title = stringResource(
        if (maxReps) Res.string.exercise_progress_chart_max_reps else Res.string.exercise_progress_chart_1rm,
    )
    ChartCard(
        title = title,
        points = progress.primary,
        valueLabel = { if (maxReps) AppNumberFormat.integer(it.roundToInt()) else formatKilos(it) },
        tag = "exercise_progress_chart_1rm",
        modifier = modifier,
    )
}

@Composable
private fun VolumeChartCard(points: List<ChartPoint>, modifier: Modifier = Modifier) {
    ChartCard(
        title = stringResource(Res.string.exercise_progress_chart_volume),
        points = points,
        valueLabel = { formatVolume(it) },
        tag = "exercise_progress_chart_volume",
        modifier = modifier,
    )
}

@Composable
private fun ChartCard(
    title: String,
    points: List<ChartPoint>,
    valueLabel: (Double) -> String,
    tag: String,
    modifier: Modifier = Modifier,
) {
    val values = points.map { it.value }
    val labels = points.map { stringResource(Res.string.chart_date_short, it.date.day, it.date.month.shortLabel()) }
    val description = stringResource(
        Res.string.exercise_progress_chart_cd,
        title,
        valueLabel(values.first()),
        valueLabel(values.last()),
        valueLabel(values.min()),
        valueLabel(values.max()),
        values.size,
    )
    AppCard(modifier) {
        AppLineChartBlock(title) {
            AppLineChart(
                values = values,
                labels = labels,
                description = description,
                valueLabel = valueLabel,
                modifier = Modifier.fillMaxWidth().testTag(tag),
            )
        }
    }
}

/** A single session (or none in the range): its value, if any, and the hint to keep training. */
@Composable
private fun NotEnoughData(progress: ExerciseProgress) {
    AppCard(Modifier.fillMaxWidth().testTag("exercise_progress_single_session")) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            progress.primary.singleOrNull()?.let { point ->
                val maxReps = progress.mode == ProgressMode.MaxReps
                val value = if (maxReps) AppNumberFormat.integer(point.value.roundToInt()) else formatKilos(point.value)
                Text(
                    text = stringResource(Res.string.exercise_progress_single_value, value),
                    style = AppTheme.typography.labelNumeric,
                    color = AppTheme.colors.athleticCyan,
                    modifier = Modifier.testTag("exercise_progress_single_value"),
                )
            }
            Text(
                text = stringResource(Res.string.exercise_progress_single_session),
                style = AppTheme.typography.bodyLarge,
                color = AppTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun ProgressLoading() {
    Column(
        Modifier.fillMaxSize().padding(Spacing.screenMargin).testTag("exercise_progress_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        AppLineChartSkeleton()
        AppLineChartSkeleton()
    }
}
