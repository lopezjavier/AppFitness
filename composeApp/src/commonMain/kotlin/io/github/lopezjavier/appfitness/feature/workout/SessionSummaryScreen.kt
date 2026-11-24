package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_done
import io.github.lopezjavier.appfitness.composeapp.resources.ic_live_trophy
import io.github.lopezjavier.appfitness.composeapp.resources.ic_timer
import io.github.lopezjavier.appfitness.composeapp.resources.px_live_summary_records
import io.github.lopezjavier.appfitness.composeapp.resources.px_live_summary_subtitle
import io.github.lopezjavier.appfitness.composeapp.resources.summary_compare_row_cd
import io.github.lopezjavier.appfitness.composeapp.resources.summary_compare_title
import io.github.lopezjavier.appfitness.composeapp.resources.summary_context_line
import io.github.lopezjavier.appfitness.composeapp.resources.summary_context_sets
import io.github.lopezjavier.appfitness.composeapp.resources.summary_title
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_strength_compare
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_strength_load_reps
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppPaint
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.CelebrationBurst
import io.github.lopezjavier.appfitness.designsystem.CelebrationBurstDefaults
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.ProgressGlyph
import io.github.lopezjavier.appfitness.designsystem.ProgressStateIcon
import io.github.lopezjavier.appfitness.designsystem.ProgressStateIconDefaults
import io.github.lopezjavier.appfitness.designsystem.RecordBadge
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.designsystem.semiBold
import io.github.lopezjavier.appfitness.domain.usecase.ExerciseSummary
import io.github.lopezjavier.appfitness.feature.trainer.glyphLabel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The summary of a finished session: the celebration, one context line and, per exercise, "Frente a la última vez". */
@Composable
internal fun SessionSummaryScreen(summary: LiveSummary, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val rows = summary.comparisons.mapNotNull { comparison ->
        val exercise = summary.summary.exercises.firstOrNull { it.position == comparison.position }
        val name = summary.exerciseNames[comparison.position]
        if (exercise == null || name == null) null else Triple(comparison, exercise, name)
    }
    val compared = rows.map { it.second.position }.toSet()
    val recordsOnly = summary.summary.exercises
        .filter { it.records.isNotEmpty() && it.position !in compared }
        .mapNotNull { exercise -> summary.exerciseNames[exercise.position]?.let { exercise to it } }
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
                SummaryContextLine(summary)
                if (rows.isNotEmpty()) {
                    Text(
                        text = stringResource(Res.string.summary_compare_title),
                        style = AppTheme.typography.titleMedium,
                        color = AppTheme.colors.textPrimary,
                        modifier = Modifier.padding(top = Spacing.sm).semantics { heading() },
                    )
                }
                rows.forEach { (comparison, exercise, name) ->
                    ExerciseCompareRow(exercise = exercise, name = name, comparison = comparison, cut = summary.cut)
                }
                recordsOnly.forEach { (exercise, name) -> RecordsOnlyCard(exercise, name) }
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
                text = pluralStringResource(Res.plurals.px_live_summary_records, records, records),
                modifier = Modifier.padding(top = Spacing.sm).testTag("session_summary_records"),
            )
        }
    }
}

/** Duration and completed sets as one quiet context line: they are context, not a result, so no tiles. */
@Composable
private fun SummaryContextLine(summary: LiveSummary) {
    val sets = summary.summary.completedSets
    val text = stringResource(
        Res.string.summary_context_line,
        durationText(summary.summary.durationSeconds),
        pluralStringResource(Res.plurals.summary_context_sets, sets, sets),
    )
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("session_summary_context_line"),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_timer),
            contentDescription = null,
            tint = AppTheme.colors.textSecondary,
            modifier = Modifier.size(Spacing.lg),
        )
        Text(
            text = text,
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.padding(start = Spacing.sm),
        )
    }
}

/**
 * One exercise against its last session, laid out like the weekly report's exercise sheet: the state glyph at the
 * start and top, then the name, "80 kg × 8 → 82,5 kg × 6" and the state word on its own line (the word never rides
 * beside the figures, so a large font only wraps text). With nothing to compare only the current set shows.
 */
@Composable
private fun ExerciseCompareRow(exercise: ExerciseSummary, name: String, comparison: ExerciseComparison, cut: Boolean) {
    val glyph = comparison.glyph
    val state = glyphLabel(glyph, cut)
    val current = comparison.current?.let { setText(it) }
    val previous = comparison.previous?.takeIf { glyph != ProgressGlyph.Uncompared }?.let { setText(it) }
    val values = when {
        current == null -> null
        previous == null -> current
        else -> stringResource(Res.string.week_report_strength_compare, previous, current)
    }
    val description = stringResource(Res.string.summary_compare_row_cd, name, state, values.orEmpty())
    AppCard(Modifier.fillMaxWidth(), glow = if (exercise.records.isNotEmpty()) AppPaint.Gold else null) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(
                Modifier.fillMaxWidth()
                    .semantics(mergeDescendants = true) { contentDescription = description }
                    .testTag("session_summary_compare_${exercise.exerciseId}"),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.Top,
            ) {
                ProgressStateIcon(glyph, Modifier.testTag("session_summary_state_${exercise.exerciseId}"))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        text = name,
                        style = AppTheme.typography.bodyLarge.semiBold(),
                        color = AppTheme.colors.textPrimary,
                    )
                    if (values != null) {
                        Text(values, style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textPrimary)
                    }
                    Text(state, style = AppTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
                }
            }
            PersonalRecordPills(
                exercise.records,
                exercise.position,
                "session_summary",
                Modifier.padding(start = ProgressStateIconDefaults.Size + Spacing.md),
            )
        }
    }
}

/** An exercise with records but no strength comparison (cardio): its name and its record pills. */
@Composable
private fun RecordsOnlyCard(exercise: ExerciseSummary, name: String) {
    AppCard(Modifier.fillMaxWidth(), glow = AppPaint.Gold) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                text = name,
                style = AppTheme.typography.bodyLarge.semiBold(),
                color = AppTheme.colors.textPrimary,
            )
            PersonalRecordPills(exercise.records, exercise.position, "session_summary")
        }
    }
}

@Composable
private fun setText(set: TopSet): String = stringResource(
    Res.string.week_report_strength_load_reps,
    AppNumberFormat.decimal(set.weightKg, maxDecimals = 2),
    set.reps,
)
