package io.github.lopezjavier.appfitness.feature.analytics.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.chart_date_short
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_unnamed
import io.github.lopezjavier.appfitness.composeapp.resources.stats_chart_cd
import io.github.lopezjavier.appfitness.composeapp.resources.stats_chart_empty
import io.github.lopezjavier.appfitness.composeapp.resources.stats_chart_title_month
import io.github.lopezjavier.appfitness.composeapp.resources.stats_chart_title_week
import io.github.lopezjavier.appfitness.composeapp.resources.stats_exercise_row_detail
import io.github.lopezjavier.appfitness.composeapp.resources.stats_exercises_empty
import io.github.lopezjavier.appfitness.composeapp.resources.stats_exercises_title
import io.github.lopezjavier.appfitness.composeapp.resources.stats_muscle_row_cd
import io.github.lopezjavier.appfitness.composeapp.resources.stats_muscles_title
import io.github.lopezjavier.appfitness.composeapp.resources.stats_tile_duration
import io.github.lopezjavier.appfitness.composeapp.resources.stats_tile_sets
import io.github.lopezjavier.appfitness.composeapp.resources.stats_tile_volume
import io.github.lopezjavier.appfitness.composeapp.resources.stats_tile_workouts
import io.github.lopezjavier.appfitness.composeapp.resources.stats_unit_kg
import io.github.lopezjavier.appfitness.designsystem.AppBarChart
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.MetricTile
import io.github.lopezjavier.appfitness.designsystem.MuscleSplitBar
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.durationText
import io.github.lopezjavier.appfitness.domain.model.MuscleGroupSets
import io.github.lopezjavier.appfitness.domain.model.ProgressRange
import io.github.lopezjavier.appfitness.domain.model.SetCountPoint
import io.github.lopezjavier.appfitness.domain.model.TrainingSummary
import io.github.lopezjavier.appfitness.feature.analytics.formatVolume
import io.github.lopezjavier.appfitness.feature.exercises.label
import io.github.lopezjavier.appfitness.feature.exercises.tagName
import io.github.lopezjavier.appfitness.feature.workout.shortLabel
import org.jetbrains.compose.resources.stringResource

private val TilesHeight = Spacing.xl * 6
private val ChartHeight = Spacing.xl * 8
private val RowsHeight = Spacing.xl * 12
private val ExercisesHeight = Spacing.xl * 6

/** The four key values of the frame as a 2x2 grid of tiles. */
@Composable
internal fun SummaryTiles(summary: SectionState<TrainingSummary>, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    StatsBlock(summary, StatsSection.Summary, TilesHeight, onRetry, modifier) { data ->
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                MetricTile(
                    stringResource(Res.string.stats_tile_workouts),
                    AppNumberFormat.integer(data.workouts),
                    Modifier.weight(1f).testTag("stats_tile_workouts"),
                )
                MetricTile(
                    stringResource(Res.string.stats_tile_sets),
                    AppNumberFormat.integer(data.sets),
                    Modifier.weight(1f).testTag("stats_tile_sets"),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                MetricTile(
                    stringResource(Res.string.stats_tile_volume),
                    formatVolume(data.volume),
                    Modifier.weight(1f).testTag("stats_tile_volume"),
                    unit = stringResource(Res.string.stats_unit_kg),
                )
                MetricTile(
                    stringResource(Res.string.stats_tile_duration),
                    durationText(data.durationSeconds),
                    Modifier.weight(1f).testTag("stats_tile_duration"),
                )
            }
        }
    }
}

/** Set-count bar chart: weekly buckets up to 6M, monthly for 1A and "Todo". */
@Composable
internal fun SetCountCard(
    range: ProgressRange,
    setCounts: SectionState<List<SetCountPoint>>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val monthly = range == ProgressRange.OneYear || range == ProgressRange.AllTime
    val title = stringResource(if (monthly) Res.string.stats_chart_title_month else Res.string.stats_chart_title_week)
    StatsCard(title, modifier) {
        StatsBlock(
            state = setCounts,
            section = StatsSection.Chart,
            skeletonHeight = ChartHeight,
            onRetry = onRetry,
            empty = {
                StatsHint(stringResource(Res.string.stats_chart_empty), Modifier.testTag("stats_set_chart_empty"))
            },
        ) { buckets ->
            val labels = buckets.map {
                if (monthly) {
                    it.weekStart.month.shortLabel()
                } else {
                    stringResource(
                        Res.string.chart_date_short,
                        it.weekStart.day,
                        it.weekStart.month.shortLabel(),
                    )
                }
            }
            val peak = buckets.indices.maxBy { buckets[it].sets }
            val description = stringResource(
                Res.string.stats_chart_cd,
                setsText(buckets.sumOf { it.sets }.toDouble()),
                setsText(buckets[peak].sets.toDouble()),
                labels[peak],
            )
            AppBarChart(
                values = buckets.map { it.sets },
                labels = labels,
                description = description,
                modifier = Modifier.fillMaxWidth().testTag("stats_set_chart"),
                valueLabel = { AppNumberFormat.integer(it) },
            )
        }
    }
}

/** Every muscle group with its sets in the frame; read-only (taps live in the body map). */
@Composable
internal fun MuscleRowsCard(
    muscleSets: SectionState<List<MuscleGroupSets>>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StatsCard(stringResource(Res.string.stats_muscles_title), modifier) {
        StatsBlock(muscleSets, StatsSection.Muscles, RowsHeight, onRetry) { rows ->
            val max = rows.maxOfOrNull { it.sets } ?: 0.0
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                rows.forEach { row ->
                    val name = stringResource(row.muscleGroup.label)
                    val value = setsText(row.sets)
                    MuscleSplitBar(
                        label = name,
                        valueText = value,
                        fraction = if (max > 0.0) (row.sets / max).toFloat() else 0f,
                        description = stringResource(Res.string.stats_muscle_row_cd, name, value),
                        modifier = Modifier.testTag("stats_muscle_row_${row.muscleGroup.tagName()}"),
                    )
                }
            }
        }
    }
}

/** Top exercises of the frame; a row opens the progress of the exercise. */
@Composable
internal fun TopExercisesCard(
    exercises: SectionState<List<StatsExercise>>,
    onRetry: () -> Unit,
    onOpenExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    StatsCard(stringResource(Res.string.stats_exercises_title), modifier) {
        StatsBlock(
            state = exercises,
            section = StatsSection.Exercises,
            skeletonHeight = ExercisesHeight,
            onRetry = onRetry,
            empty = {
                StatsHint(stringResource(Res.string.stats_exercises_empty), Modifier.testTag("stats_exercises_empty"))
            },
        ) { rows ->
            Column {
                rows.forEach {
                    ExerciseRow(it, "stats_exercise_row_${it.exerciseId}", { onOpenExercise(it.exerciseId) })
                }
            }
        }
    }
}

/** Name over "N series · M sesiones"; at least 48 dp, one button. Shared with the muscle sheet. */
@Composable
internal fun ExerciseRow(exercise: StatsExercise, tag: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val name = exercise.name ?: stringResource(Res.string.exercise_unnamed)
    val detail =
        stringResource(Res.string.stats_exercise_row_detail, setsText(exercise.sets), sessionsText(exercise.sessions))
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.minTouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$name, $detail" }
            .testTag(tag),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(name, style = AppTheme.typography.bodyLarge, color = AppTheme.colors.textPrimary)
        Text(detail, style = AppTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
    }
}
