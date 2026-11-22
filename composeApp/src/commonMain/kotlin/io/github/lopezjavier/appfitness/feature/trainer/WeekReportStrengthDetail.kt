package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.block_report_ups_cd
import io.github.lopezjavier.appfitness.composeapp.resources.block_report_ups_label
import io.github.lopezjavier.appfitness.composeapp.resources.block_report_ups_of
import io.github.lopezjavier.appfitness.composeapp.resources.ic_trending_up
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_health_note_14_days
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_health_note_3_sessions
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_state_down
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_state_hold
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_state_paused
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_state_same
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_state_stalled
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_state_uncompared
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_state_up
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_strength_carried
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_strength_compare
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_strength_load_reps
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_strength_no_second
import io.github.lopezjavier.appfitness.designsystem.AppDateFormat
import io.github.lopezjavier.appfitness.designsystem.AppInfoSheet
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ExerciseStatusRow
import io.github.lopezjavier.appfitness.designsystem.ProgressGlyph
import io.github.lopezjavier.appfitness.designsystem.ProgressStateIcon
import io.github.lopezjavier.appfitness.designsystem.ProgressStateIconDefaults
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.StatRing
import io.github.lopezjavier.appfitness.designsystem.StatRingDefaults
import io.github.lopezjavier.appfitness.designsystem.semiBold
import io.github.lopezjavier.appfitness.feature.exercises.label
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun glyphLabel(glyph: ProgressGlyph, cut: Boolean): String = stringResource(
    when (glyph) {
        ProgressGlyph.Up -> Res.string.week_report_state_up
        ProgressGlyph.Same -> if (cut) Res.string.week_report_state_hold else Res.string.week_report_state_same
        ProgressGlyph.Down -> Res.string.week_report_state_down
        ProgressGlyph.Stalled -> Res.string.week_report_state_stalled
        ProgressGlyph.Paused -> Res.string.week_report_state_paused
        ProgressGlyph.Uncompared -> Res.string.week_report_state_uncompared
    },
)

internal fun StrengthGroupUi.ordered() = exercises.sortedByDescending { it.mainLift }

/** The paused exercise: its own row, plus the `07` §7 note on the live report only (read-only here, R5). */
@Composable
internal fun PausedRow(exercise: ExerciseProgressUi, name: String, live: Boolean, scope: ReportScope) {
    val note = exercise.healthNote?.takeIf { live }?.let {
        stringResource(
            when (it) {
                HealthNoteKind.FourteenDays -> Res.string.week_report_health_note_14_days
                HealthNoteKind.ThreeSessions -> Res.string.week_report_health_note_3_sessions
            },
            name,
        )
    }
    ExerciseStatusRow(
        text = name,
        marker = { ProgressStateIcon(ProgressGlyph.Paused, size = ProgressStateIconDefaults.SmallSize) },
        trailing = {
            Text(
                text = stringResource(Res.string.week_report_state_paused),
                style = AppTheme.typography.bodySmall.semiBold(),
                color = AppTheme.colors.athleticCyan,
                modifier = Modifier.padding(end = Spacing.xs),
            )
        },
        supporting = note,
        primaryText = true,
        testTag = "${scope.tag}_strength_paused_${exercise.exerciseId}",
        supportingTestTag = note?.let { "${scope.tag}_health_note_${exercise.exerciseId}" },
    )
}

@Composable
internal fun GroupSheet(group: StrengthGroupUi, ctx: StrengthCtx, onDismiss: () -> Unit) {
    val cut = ctx.cut
    val names = ctx.names
    val scope = ctx.scope
    AppInfoSheet(
        title = stringResource(group.group.label),
        onDismiss = onDismiss,
        testTagPrefix = "${scope.tag}_strength_sheet",
    ) {
        Column(Modifier.padding(bottom = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            group.ordered().filter { it.exerciseId in names }.forEach { exercise ->
                SheetExercise(exercise, names.getValue(exercise.exerciseId), cut, scope)
            }
        }
    }
}

@Composable
private fun SheetExercise(exercise: ExerciseProgressUi, name: String, cut: Boolean, scope: ReportScope) {
    val label = glyphLabel(exercise.state, cut)
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}
            .testTag("${scope.tag}_strength_sheet_exercise_${exercise.exerciseId}"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProgressStateIcon(exercise.state)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                name,
                style = AppTheme.typography.bodyLarge.semiBold(),
                color = AppTheme.colors.textPrimary,
            )
            val baseline = exercise.baseline
            val best = exercise.best
            if (baseline != null && best != null && exercise.state != ProgressGlyph.Uncompared) {
                Text(
                    text = stringResource(Res.string.week_report_strength_compare, loadReps(baseline), loadReps(best)),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textPrimary,
                )
            } else {
                Text(
                    text = stringResource(Res.string.week_report_strength_no_second),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                )
            }
            Text(
                text = exercise.carriedOn?.let {
                    "$label · " + stringResource(Res.string.week_report_strength_carried, AppDateFormat.shortDate(it))
                } ?: label,
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun loadReps(value: LoadReps): String = stringResource(
    Res.string.week_report_strength_load_reps,
    AppNumberFormat.decimal(value.loadKg, maxDecimals = 2),
    value.reps,
)

/**
 * The block's headline figure (`07` §3 (A1)): a ring that fills with the exercises that went up, "12 de 15" and the
 * words "ejercicios suben". Shape and numbers carry it; the colour only reinforces. Nothing when nothing compares.
 */
@Composable
internal fun BlockUpsHero(strength: StrengthUi, scope: ReportScope) {
    val total = strength.groups.sumOf { it.compared }
    if (total == 0) return
    val ups = strength.groups.sumOf { it.up }
    val spoken = pluralStringResource(Res.plurals.block_report_ups_cd, total, ups, total)
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = spoken }
            .testTag("${scope.tag}_strength_summary"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatRing(
            fraction = ups.toFloat() / total,
            color = AppTheme.colors.electricGreen,
            size = StatRingDefaults.Size,
        ) {
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
        }
    }
}
