package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_chevron_right
import io.github.lopezjavier.appfitness.composeapp.resources.ic_fitness_center
import io.github.lopezjavier.appfitness.composeapp.resources.ic_flag
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
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_strength_empty
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_strength_exercise_cd
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_strength_group_cd
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_strength_help_body
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_strength_help_cd
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_strength_load_reps
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_strength_no_second
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_strength_sessions
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_strength_sets
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_strength_title
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_strength_ups
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_strength_week_one
import io.github.lopezjavier.appfitness.designsystem.AppDateFormat
import io.github.lopezjavier.appfitness.designsystem.AppInfoSheet
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ExerciseStatusRow
import io.github.lopezjavier.appfitness.designsystem.HelpButton
import io.github.lopezjavier.appfitness.designsystem.IconBadge
import io.github.lopezjavier.appfitness.designsystem.ProgressGlyph
import io.github.lopezjavier.appfitness.designsystem.ProgressStateIcon
import io.github.lopezjavier.appfitness.designsystem.ProgressStateIconDefaults
import io.github.lopezjavier.appfitness.designsystem.SectionCard
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.semiBold
import io.github.lopezjavier.appfitness.feature.exercises.label
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val TAG = "week_summary"

/**
 * The Strength card (`07` §3 (1)): one row per muscle group with one state glyph per exercise and "2 de 3 suben".
 * A tap opens the group sheet (R-57, no inline expansion). A paused exercise gets its own row below the group, and
 * on the live report its health note. Week 1 shows the base message instead of states.
 */
@Composable
internal fun WeekReportStrengthCard(strength: StrengthUi, names: Map<String, String>, live: Boolean) {
    var openGroup by rememberSaveable { mutableStateOf<String?>(null) }
    val title = stringResource(Res.string.week_report_strength_title)
    SectionCard(
        title,
        testTag = "${TAG}_section_strength",
        help = {
            HelpButton(
                contentDescription = stringResource(Res.string.week_report_strength_help_cd),
                title = title,
                body = stringResource(Res.string.week_report_strength_help_body),
                testTag = "${TAG}_strength_help",
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            ContextLine(strength.context)
            when {
                strength.blockWeek <= 1 -> BaseMessage(stringResource(Res.string.week_report_strength_week_one))
                strength.groups.isEmpty() -> BaseMessage(stringResource(Res.string.week_report_strength_empty))
            }
            strength.groups.forEach { group ->
                GroupRow(group, strength.cut, names, live) { openGroup = group.group.name }
            }
        }
    }
    val sheetGroup = strength.groups.firstOrNull { it.group.name == openGroup }
    if (sheetGroup != null) GroupSheet(sheetGroup, strength.cut, names) { openGroup = null }
}

/** "3 de 4 entrenos · 18 de 20 series": adherence is only context here (`07` §3 user rule), so it stays text. */
@Composable
private fun ContextLine(context: WeekContextUi) {
    val sessions = pluralStringResource(
        Res.plurals.week_report_strength_sessions,
        context.sessionsPlanned,
        context.sessionsDone,
        context.sessionsPlanned,
    )
    val sets = context.setsPlanned?.let {
        pluralStringResource(Res.plurals.week_report_strength_sets, it, context.setsDone, it)
    }
    val text = listOfNotNull(sessions, sets).joinToString(" · ")
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(Res.drawable.ic_fitness_center),
            contentDescription = null,
            tint = AppTheme.colors.textSecondary,
            modifier = Modifier.size(Spacing.lg),
        )
        Text(
            text = text,
            style = AppTheme.typography.bodySmall,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.testTag("${TAG}_strength_context"),
        )
    }
}

@Composable
private fun BaseMessage(text: String) {
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("${TAG}_strength_base"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(Res.drawable.ic_flag, AppTheme.colors.neonLime)
        Text(
            text,
            style = AppTheme.typography.bodyLarge,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun glyphLabel(glyph: ProgressGlyph, cut: Boolean): String = stringResource(
    when (glyph) {
        ProgressGlyph.Up -> Res.string.week_report_state_up
        ProgressGlyph.Same -> if (cut) Res.string.week_report_state_hold else Res.string.week_report_state_same
        ProgressGlyph.Down -> Res.string.week_report_state_down
        ProgressGlyph.Stalled -> Res.string.week_report_state_stalled
        ProgressGlyph.Paused -> Res.string.week_report_state_paused
        ProgressGlyph.Uncompared -> Res.string.week_report_state_uncompared
    },
)

private fun StrengthGroupUi.ordered() = exercises.sortedByDescending { it.mainLift }

@Composable
private fun GroupRow(
    group: StrengthGroupUi,
    cut: Boolean,
    names: Map<String, String>,
    live: Boolean,
    onOpen: () -> Unit,
) {
    val groupName = stringResource(group.group.label)
    val summary = if (group.compared == 0) {
        stringResource(Res.string.week_report_state_uncompared)
    } else {
        stringResource(Res.string.week_report_strength_ups, group.up, group.compared)
    }
    val groupCd = stringResource(Res.string.week_report_strength_group_cd, groupName, summary)
    val rows = group.ordered().filter { it.exerciseId in names }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.minTouchTarget)
                .clickable(role = Role.Button, onClick = onOpen)
                .semantics(mergeDescendants = true) { contentDescription = groupCd }
                .testTag("${TAG}_strength_group_${group.group.name.lowercase()}"),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = groupName,
                    style = AppTheme.typography.bodyLarge.semiBold(),
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(summary, style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
                Icon(
                    painter = painterResource(Res.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = AppTheme.colors.textSecondary,
                    modifier = Modifier.size(Spacing.xl),
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                rows.forEach { exercise ->
                    ProgressStateIcon(
                        glyph = exercise.state,
                        modifier = Modifier.testTag("${TAG}_strength_exercise_${exercise.exerciseId}"),
                        contentDescription = stringResource(
                            Res.string.week_report_strength_exercise_cd,
                            names.getValue(exercise.exerciseId),
                            glyphLabel(exercise.state, cut),
                        ),
                    )
                }
            }
        }
        rows.filter { it.state == ProgressGlyph.Paused }.forEach { PausedRow(it, names.getValue(it.exerciseId), live) }
    }
}

/** The paused exercise: its own row, plus the `07` §7 note on the live report only (read-only here, R5). */
@Composable
private fun PausedRow(exercise: ExerciseProgressUi, name: String, live: Boolean) {
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
        testTag = "${TAG}_strength_paused_${exercise.exerciseId}",
        supportingTestTag = note?.let { "${TAG}_health_note_${exercise.exerciseId}" },
    )
}

@Composable
private fun GroupSheet(group: StrengthGroupUi, cut: Boolean, names: Map<String, String>, onDismiss: () -> Unit) {
    AppInfoSheet(
        title = stringResource(group.group.label),
        onDismiss = onDismiss,
        testTagPrefix = "${TAG}_strength_sheet",
    ) {
        Column(Modifier.padding(bottom = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            group.ordered().filter { it.exerciseId in names }.forEach { exercise ->
                SheetExercise(exercise, names.getValue(exercise.exerciseId), cut)
            }
        }
    }
}

@Composable
private fun SheetExercise(exercise: ExerciseProgressUi, name: String, cut: Boolean) {
    val label = glyphLabel(exercise.state, cut)
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}
            .testTag("${TAG}_strength_sheet_exercise_${exercise.exerciseId}"),
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
