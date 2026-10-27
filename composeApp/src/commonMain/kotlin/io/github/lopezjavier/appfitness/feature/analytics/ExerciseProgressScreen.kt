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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.chart_date_short
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_chart_1rm
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_chart_cd
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_chart_distance
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_chart_duration
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_chart_floors
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_chart_max_reps
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_chart_reps
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_chart_steps
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_chart_volume
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_empty_message
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_error_message
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_record_legend
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_records_cd
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_single_session
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_single_value
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_title
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppLineChart
import io.github.lopezjavier.appfitness.designsystem.AppLineChartBlock
import io.github.lopezjavier.appfitness.designsystem.AppLineChartSkeleton
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.TimeRangeSelector
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClass
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.designsystem.windowWidthClassFor
import io.github.lopezjavier.appfitness.domain.model.ChartPoint
import io.github.lopezjavier.appfitness.domain.model.ExerciseProgress
import io.github.lopezjavier.appfitness.domain.model.ProgressMetric
import io.github.lopezjavier.appfitness.feature.workout.shortLabel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt

/** Stateful progress of one exercise: connects [ExerciseProgressViewModel]; [onBack] leaves the screen. */
@Composable
fun ExerciseProgressRoot(
    exerciseId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExerciseProgressViewModel = koinViewModel(key = exerciseId, parameters = { parametersOf(exerciseId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ExerciseProgressScreen(state, viewModel::onEvent, onBack, modifier)
}

/**
 * Progress of one exercise (US-15): the bar carries the exercise name (the generic title while it is unknown), the
 * range selector stays pinned on top, then the 1RM chart and the volume chart (or the max-reps chart for an exercise
 * never logged with load). A single session shows its value and a hint instead of a chart.
 */
@Composable
fun ExerciseProgressScreen(
    state: ExerciseProgressUiState,
    onEvent: (ExerciseProgressEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = when (state) {
        is ExerciseProgressUiState.Content -> state.name
        is ExerciseProgressUiState.Empty -> state.name
        else -> null
    }
    AppScreen(
        title = name ?: stringResource(Res.string.exercise_progress_title),
        modifier = modifier.testTag("exercise_progress_screen"),
        onBack = onBack,
        maxContentWidth = WindowWidthClassDefaults.TwoColumnContentMaxWidth,
        testTagPrefix = "exercise_progress",
    ) {
        when (state) {
            ExerciseProgressUiState.Loading -> ProgressLoading()

            is ExerciseProgressUiState.Empty -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = windowWidthClassFor(maxWidth) != WindowWidthClass.Compact
        Column(Modifier.fillMaxSize()) {
            TimeRangeSelector(
                selected = state.range.toTimeRange(),
                onSelect = { onEvent(ExerciseProgressEvent.SelectRange(it.toProgressRange())) },
                ranges = ProgressRanges,
                tagPrefix = "exercise_progress_range",
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screenMargin, vertical = Spacing.sm),
            )
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Spacing.screenMargin),
                verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
            ) {
                ProgressCharts(state.progress, wide)
            }
        }
    }
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

/** What the primary chart of a [ProgressMetric] shows: title, value format and test tag. */
private class MetricSpec(val title: StringResource, val tag: String, val format: (Double) -> String)

private fun formatCount(value: Double) = AppNumberFormat.integer(value.roundToInt())

private fun ProgressMetric.spec() = when (this) {
    ProgressMetric.OneRepMax ->
        MetricSpec(Res.string.exercise_progress_chart_1rm, "exercise_progress_chart_1rm", ::formatKilos)

    ProgressMetric.MaxReps ->
        MetricSpec(Res.string.exercise_progress_chart_max_reps, "exercise_progress_chart_1rm", ::formatCount)

    ProgressMetric.Reps ->
        MetricSpec(Res.string.exercise_progress_chart_reps, "exercise_progress_chart_reps", ::formatCount)

    ProgressMetric.Duration ->
        MetricSpec(Res.string.exercise_progress_chart_duration, "exercise_progress_chart_duration", ::formatDuration)

    ProgressMetric.Distance ->
        MetricSpec(Res.string.exercise_progress_chart_distance, "exercise_progress_chart_distance", ::formatDistance)

    ProgressMetric.Steps ->
        MetricSpec(Res.string.exercise_progress_chart_steps, "exercise_progress_chart_steps", ::formatCount)

    ProgressMetric.Floors ->
        MetricSpec(Res.string.exercise_progress_chart_floors, "exercise_progress_chart_floors", ::formatCount)
}

@Composable
private fun PrimaryChartCard(progress: ExerciseProgress, modifier: Modifier = Modifier) {
    val spec = progress.mode.spec()
    ChartCard(
        title = stringResource(spec.title),
        points = progress.primary,
        valueLabel = spec.format,
        tag = spec.tag,
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
        legendTag = "exercise_progress_record_legend_volume",
    )
}

@Composable
private fun ChartCard(
    title: String,
    points: List<ChartPoint>,
    valueLabel: (Double) -> String,
    tag: String,
    modifier: Modifier = Modifier,
    legendTag: String = "exercise_progress_record_legend",
) {
    val values = points.map { it.value }
    val labels = points.map { stringResource(Res.string.chart_date_short, it.date.day, it.date.month.shortLabel()) }
    val markers = points.indices.filter { points[it].isRecord }
    val summary = stringResource(
        Res.string.exercise_progress_chart_cd,
        title,
        values.size,
        valueLabel(values.first()),
        valueLabel(values.last()),
    )
    val description = if (markers.isEmpty()) {
        summary
    } else {
        "$summary ${pluralStringResource(Res.plurals.exercise_progress_records_cd, markers.size, markers.size)}"
    }
    AppCard(modifier) {
        AppLineChartBlock(
            title = title,
            recordLegend = if (markers.isEmpty()) null else stringResource(Res.string.exercise_progress_record_legend),
            recordLegendModifier = Modifier.testTag(legendTag),
        ) {
            AppLineChart(
                values = values,
                labels = labels,
                description = description,
                valueLabel = valueLabel,
                modifier = Modifier.fillMaxWidth().testTag(tag),
                markers = markers,
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
                Text(
                    text = stringResource(
                        Res.string.exercise_progress_single_value,
                        progress.mode.spec().format(point.value),
                    ),
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
