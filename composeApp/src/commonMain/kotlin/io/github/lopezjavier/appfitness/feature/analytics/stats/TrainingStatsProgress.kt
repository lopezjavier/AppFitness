package io.github.lopezjavier.appfitness.feature.analytics.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.block_report_ups_cd
import io.github.lopezjavier.appfitness.composeapp.resources.block_report_ups_label
import io.github.lopezjavier.appfitness.composeapp.resources.block_report_ups_of
import io.github.lopezjavier.appfitness.composeapp.resources.ic_chevron_right
import io.github.lopezjavier.appfitness.composeapp.resources.ic_fitness_center
import io.github.lopezjavier.appfitness.composeapp.resources.ic_trending_up
import io.github.lopezjavier.appfitness.composeapp.resources.stats_exercise_row_detail
import io.github.lopezjavier.appfitness.composeapp.resources.stats_principal_change
import io.github.lopezjavier.appfitness.composeapp.resources.stats_progress_compare
import io.github.lopezjavier.appfitness.composeapp.resources.stats_progress_down
import io.github.lopezjavier.appfitness.composeapp.resources.stats_progress_e1rm_caption
import io.github.lopezjavier.appfitness.composeapp.resources.stats_progress_margin
import io.github.lopezjavier.appfitness.composeapp.resources.stats_progress_none
import io.github.lopezjavier.appfitness.composeapp.resources.stats_progress_row_cd
import io.github.lopezjavier.appfitness.composeapp.resources.stats_progress_title
import io.github.lopezjavier.appfitness.composeapp.resources.stats_progress_up
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_state_uncompared
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ExerciseRow
import io.github.lopezjavier.appfitness.designsystem.ProgressGlyph
import io.github.lopezjavier.appfitness.designsystem.ProgressStateIcon
import io.github.lopezjavier.appfitness.designsystem.SectionCard
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.StatRing
import io.github.lopezjavier.appfitness.designsystem.StatRingDefaults
import io.github.lopezjavier.appfitness.designsystem.TrendDirection
import io.github.lopezjavier.appfitness.designsystem.TrendPill
import io.github.lopezjavier.appfitness.designsystem.TrendPillDefaults
import io.github.lopezjavier.appfitness.designsystem.TrendTone
import io.github.lopezjavier.appfitness.designsystem.semiBold
import io.github.lopezjavier.appfitness.domain.model.TrainingSummary
import io.github.lopezjavier.appfitness.feature.exercises.LocalExerciseMediaUris
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

private val ProgressHeight = Spacing.xl * 8
private val ContextHeight = Spacing.xl * 2

/** "+5" / "−2,5" with the sign always written (colour is never the only cue); a zero change reads "0". */
internal fun signedKg(kg: Double): String {
    val magnitude = AppNumberFormat.decimal(abs(kg), maxDecimals = 1)
    return when {
        magnitude == "0" -> magnitude
        kg > 0 -> "+$magnitude"
        else -> "−$magnitude"
    }
}

/**
 * "Tu progreso en este periodo" (`07` §3 (D1)): a ring that fills with the exercises that went up and "8 de 11
 * ejercicios suben", then one row per exercise: the state glyph of the weekly report, the name, a pill with the change
 * (arrow plus words, never colour alone) and the first and last estimated 1RM. A row opens "Progreso del ejercicio".
 */
@Composable
internal fun PeriodProgressCard(
    progress: SectionState<PeriodProgressUi>,
    onRetry: () -> Unit,
    onOpenExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(stringResource(Res.string.stats_progress_title), modifier, testTag = "stats_progress_card") {
        StatsBlock(
            state = progress,
            section = StatsSection.Progress,
            skeletonHeight = ProgressHeight,
            onRetry = onRetry,
            empty = {
                StatsHint(stringResource(Res.string.stats_progress_none), Modifier.testTag("stats_progress_none"))
            },
        ) { data ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                if (data.compared > 0) {
                    ProgressHero(data.ups, data.compared)
                } else {
                    StatsHint(stringResource(Res.string.stats_progress_none), Modifier.testTag("stats_progress_none"))
                }
                data.exercises.forEach { PeriodExerciseRow(it) { onOpenExercise(it.exerciseId) } }
            }
        }
    }
}

@Composable
private fun ProgressHero(ups: Int, total: Int) {
    val spoken = pluralStringResource(Res.plurals.block_report_ups_cd, total, ups, total)
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = spoken }
            .testTag("stats_progress_summary"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatRing(ups.toFloat() / total, color = AppTheme.colors.electricGreen, size = StatRingDefaults.Size) {
            Icon(
                painter = painterResource(Res.drawable.ic_trending_up),
                contentDescription = null,
                tint = AppTheme.colors.electricGreen,
                modifier = Modifier.size(Spacing.xl),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = stringResource(Res.string.block_report_ups_of, ups, total),
                style = AppTheme.typography.displayHero,
                color = AppTheme.colors.textPrimary,
            )
            Text(
                text = stringResource(Res.string.block_report_ups_label),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
            )
            Text(
                text = stringResource(Res.string.stats_progress_e1rm_caption),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun periodStateLabel(exercise: PeriodExerciseUi): String = when (exercise.state) {
    ProgressGlyph.Up -> stringResource(Res.string.stats_progress_up, AppNumberFormat.decimal(exercise.changePct ?: 0.0))

    ProgressGlyph.Down ->
        stringResource(Res.string.stats_progress_down, AppNumberFormat.decimal(abs(exercise.changePct ?: 0.0)))

    ProgressGlyph.Uncompared -> stringResource(Res.string.week_report_state_uncompared)

    else -> stringResource(Res.string.stats_progress_margin)
}

@Composable
private fun PeriodExerciseRow(exercise: PeriodExerciseUi, onOpen: () -> Unit) {
    val label = periodStateLabel(exercise)
    val spoken = stringResource(Res.string.stats_progress_row_cd, exercise.name, label)
    Row(
        Modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget)
            .clickable(role = Role.Button, onClick = onOpen)
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .testTag("stats_progress_row_${exercise.exerciseId}"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProgressStateIcon(exercise.state)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(exercise.name, style = AppTheme.typography.bodyLarge.semiBold(), color = AppTheme.colors.textPrimary)
            StateLine(exercise, label)
            val last = exercise.lastKg
            if (last != null) {
                Text(
                    text = stringResource(
                        Res.string.stats_progress_compare,
                        AppNumberFormat.decimal(exercise.firstKg),
                        AppNumberFormat.decimal(last),
                    ),
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary,
                )
            }
        }
        Icon(
            painter = painterResource(Res.drawable.ic_chevron_right),
            contentDescription = null,
            tint = AppTheme.colors.textSecondary,
            modifier = Modifier.size(Spacing.xl),
        )
    }
}

@Composable
private fun StateLine(exercise: PeriodExerciseUi, label: String) {
    when (exercise.state) {
        ProgressGlyph.Up -> TrendPill(label, TrendTone.Favorable, direction = TrendDirection.Up, showToneIcon = false)

        ProgressGlyph.Down -> TrendPill(label, TrendTone.Neutral, direction = TrendDirection.Down)

        ProgressGlyph.Uncompared -> Text(
            text = stringResource(Res.string.stats_exercise_row_detail, label, sessionsText(exercise.sessions)),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
        )

        else -> Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(requireNotNull(TrendPillDefaults.directionIcon(TrendDirection.Flat))),
                contentDescription = null,
                tint = AppTheme.colors.textSecondary,
                modifier = Modifier.size(TrendPillDefaults.IconSize),
            )
            Text(label, style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
        }
    }
}

/**
 * The effort of the range as one line, "24 entrenos · 412 series" (`07` §3 (D3)): adherence is only context here, so
 * it stays text (as the context line of the weekly report) beside a small icon; there is no figure worth a graphic.
 */
@Composable
internal fun StatsContextLine(
    summary: SectionState<TrainingSummary>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StatsBlock(summary, StatsSection.Summary, ContextHeight, onRetry, modifier) { data ->
        Row(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("stats_context_line"),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_fitness_center),
                contentDescription = null,
                tint = AppTheme.colors.textSecondary,
                modifier = Modifier.size(Spacing.lg),
            )
            Text(
                text = stringResource(
                    Res.string.stats_exercise_row_detail,
                    sessionsText(data.workouts),
                    setsText(data.sets.toDouble()),
                ),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
            )
        }
    }
}

/** "Ejercicios principales" row (`07` §3 (D4)): "+5 kg · 6 entrenos", or "Sin comparar · 1 entreno". */
@Composable
internal fun PrincipalExerciseRow(exercise: StatsExercise, onClick: () -> Unit) {
    val change = exercise.changeKg?.takeIf { exercise.compared }?.let {
        stringResource(Res.string.stats_principal_change, signedKg(it))
    } ?: stringResource(Res.string.week_report_state_uncompared)
    ExerciseRow(
        name = exercise.name,
        supporting = stringResource(Res.string.stats_exercise_row_detail, change, sessionsText(exercise.sessions)),
        onOpen = onClick,
        tagPrefix = "stats_principal_${exercise.exerciseId}",
        imageUri = exercise.imageKey?.let(LocalExerciseMediaUris.current::start),
    )
}
