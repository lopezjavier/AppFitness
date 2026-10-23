package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_unnamed
import io.github.lopezjavier.appfitness.composeapp.resources.summary_done
import io.github.lopezjavier.appfitness.composeapp.resources.summary_duration
import io.github.lopezjavier.appfitness.composeapp.resources.summary_sets
import io.github.lopezjavier.appfitness.composeapp.resources.summary_title
import io.github.lopezjavier.appfitness.composeapp.resources.summary_volume_title
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.domain.usecase.ExerciseSummary
import org.jetbrains.compose.resources.stringResource

/** The summary of a finished session: duration, completed sets and, per exercise, the volume and the records beaten. */
@Composable
internal fun SessionSummaryScreen(summary: LiveSummary, onDone: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().testTag("summary_screen"), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = WindowWidthClassDefaults.FormContentMaxWidth).fillMaxSize()
                .padding(horizontal = Spacing.screenMargin),
        ) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
            ) {
                Text(
                    text = stringResource(Res.string.summary_title),
                    style = AppTheme.typography.titleLarge,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                AppCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        StatRow(
                            stringResource(Res.string.summary_duration),
                            formatElapsed(summary.summary.durationSeconds),
                            "summary_duration",
                        )
                        StatRow(
                            stringResource(Res.string.summary_sets),
                            AppNumberFormat.integer(summary.summary.completedSets),
                            "summary_sets",
                        )
                    }
                }
                Text(
                    text = stringResource(Res.string.summary_volume_title),
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.padding(top = Spacing.sm).semantics { heading() },
                )
                summary.summary.exercises.forEach { exercise ->
                    ExerciseVolumeRow(exercise, summary.exerciseNames[exercise.position])
                }
            }
            PrimaryButton(
                text = stringResource(Res.string.summary_done),
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.lg).testTag("summary_done"),
            )
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, tag: String) {
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag(tag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
        Text(value, style = AppTheme.typography.headlineMetric, color = AppTheme.colors.textPrimary)
    }
}

@Composable
private fun ExerciseVolumeRow(exercise: ExerciseSummary, name: String?) {
    val volume = volumeText(exercise.volume)
    AppCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                }.testTag("summary_volume_${exercise.position}"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = name ?: stringResource(Res.string.exercise_unnamed),
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(volume, style = AppTheme.typography.labelNumeric, color = AppTheme.colors.athleticCyan)
            }
            PersonalRecordPills(exercise.records, exercise.position, "session_summary")
        }
    }
}
