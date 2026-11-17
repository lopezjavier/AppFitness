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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_done
import io.github.lopezjavier.appfitness.composeapp.resources.ic_live_trophy
import io.github.lopezjavier.appfitness.composeapp.resources.px_live_summary_record_one
import io.github.lopezjavier.appfitness.composeapp.resources.px_live_summary_records
import io.github.lopezjavier.appfitness.composeapp.resources.px_live_summary_subtitle
import io.github.lopezjavier.appfitness.composeapp.resources.summary_title
import io.github.lopezjavier.appfitness.composeapp.resources.summary_volume_title
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppLinearProgress
import io.github.lopezjavier.appfitness.designsystem.AppPaint
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.CelebrationBurst
import io.github.lopezjavier.appfitness.designsystem.CelebrationBurstDefaults
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.RecordBadge
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
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
            ) {
                SummaryHero(summary)
                WorkoutMetricTiles(
                    durationSeconds = summary.summary.durationSeconds,
                    completedSets = summary.summary.completedSets,
                    volumeKg = null,
                    tagPrefix = "summary",
                )
                Text(
                    text = stringResource(Res.string.summary_volume_title),
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.padding(top = Spacing.sm).semantics { heading() },
                )
                val largest = summary.summary.exercises.maxOfOrNull { it.volume } ?: 0.0
                summary.summary.exercises.forEach { exercise ->
                    ExerciseVolumeRow(exercise, summary.exerciseNames.getValue(exercise.position), largest)
                }
            }
            PrimaryButton(
                text = stringResource(Res.string.action_done),
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.lg).testTag("summary_done"),
            )
        }
    }
}

/** The celebration: the burst, the title and, when the session beat records, how many exercises did. */
@Composable
private fun SummaryHero(summary: LiveSummary) {
    val records = summary.summary.exercises.count { it.records.isNotEmpty() }
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        CelebrationBurst(
            icon = Res.drawable.ic_live_trophy,
            color = if (records > 0) AppPaint.Gold else AppTheme.colors.neonLime,
            size = CelebrationBurstDefaults.Size,
        )
        Text(
            text = stringResource(Res.string.summary_title),
            style = AppTheme.typography.displayHero,
            color = AppTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(Res.string.px_live_summary_subtitle),
            style = AppTheme.typography.bodyLarge,
            color = AppTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (records > 0) {
            RecordBadge(
                text = if (records == 1) {
                    stringResource(Res.string.px_live_summary_record_one)
                } else {
                    stringResource(Res.string.px_live_summary_records, records)
                },
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }
}

@Composable
private fun ExerciseVolumeRow(exercise: ExerciseSummary, name: String, largest: Double) {
    val volume = volumeText(exercise.volume)
    val record = exercise.records.isNotEmpty()
    AppCard(Modifier.fillMaxWidth(), glow = if (record) AppPaint.Gold else null) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                }.testTag("summary_volume_${exercise.position}"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = name,
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (volume != null) {
                    Text(volume, style = AppTheme.typography.labelNumeric, color = AppTheme.colors.athleticCyan)
                }
            }
            if (volume != null && largest > 0.0) {
                AppLinearProgress(
                    fraction = (exercise.volume / largest).toFloat(),
                    stateDescription = volume,
                    fillColor = if (record) AppPaint.Gold else AppTheme.colors.athleticCyan,
                    staggerIndex = exercise.position,
                )
            }
            PersonalRecordPills(exercise.records, exercise.position, "session_summary")
        }
    }
}
