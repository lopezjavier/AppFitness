package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_state_uncompared
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_strength_empty
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_strength_exercise_cd
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_strength_group_cd
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_strength_help_body
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_strength_help_cd
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_strength_sessions
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_strength_sets
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_strength_title
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_strength_ups
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_strength_week_one
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.HelpButton
import io.github.lopezjavier.appfitness.designsystem.IconBadge
import io.github.lopezjavier.appfitness.designsystem.ProgressGlyph
import io.github.lopezjavier.appfitness.designsystem.ProgressStateIcon
import io.github.lopezjavier.appfitness.designsystem.SectionCard
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.semiBold
import io.github.lopezjavier.appfitness.feature.exercises.label
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The Strength card (`07` §3 (1)): one row per muscle group with one state glyph per exercise and "2 de 3 suben".
 * A tap opens the group sheet (R-57, no inline expansion). A paused exercise gets its own row below the group, and
 * on the live report its health note. Week 1 shows the base message instead of states.
 */
@Composable
internal fun WeekReportStrengthCard(
    strength: StrengthUi,
    names: Map<String, String>,
    live: Boolean,
    scope: ReportScope = ReportScope.Week,
) {
    var openGroup by rememberSaveable { mutableStateOf<String?>(null) }
    val title = stringResource(Res.string.week_report_strength_title)
    SectionCard(
        title,
        testTag = "${scope.tag}_section_strength",
        help = {
            HelpButton(
                contentDescription = stringResource(Res.string.week_report_strength_help_cd),
                title = title,
                body = stringResource(Res.string.week_report_strength_help_body),
                testTag = "${scope.tag}_strength_help",
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            if (scope == ReportScope.Block) BlockUpsHero(strength, scope)
            ContextLine(strength.context, scope)
            when {
                // A block review always has its own baseline (`07` §3 (A1)): no week-1 message.
                scope == ReportScope.Week && strength.blockWeek <= 1 -> BaseMessage(
                    stringResource(Res.string.week_report_strength_week_one),
                    scope,
                )

                strength.groups.isEmpty() -> BaseMessage(stringResource(Res.string.week_report_strength_empty), scope)
            }
            strength.groups.forEach { group ->
                GroupRow(group, StrengthCtx(strength.cut, names, live, scope)) { openGroup = group.group.name }
            }
        }
    }
    val sheetGroup = strength.groups.firstOrNull { it.group.name == openGroup }
    if (sheetGroup != null) GroupSheet(sheetGroup, StrengthCtx(strength.cut, names, live, scope)) { openGroup = null }
}

/** "3 de 4 entrenos · 18 de 20 series": adherence is only context here (`07` §3 user rule), so it stays text. */
@Composable
private fun ContextLine(context: WeekContextUi, scope: ReportScope) {
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
            modifier = Modifier.testTag("${scope.tag}_strength_context"),
        )
    }
}

@Composable
private fun BaseMessage(text: String, scope: ReportScope) {
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("${scope.tag}_strength_base"),
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

/** What a Strength group row and its sheet need besides the group itself. */
internal class StrengthCtx(val cut: Boolean, val names: Map<String, String>, val live: Boolean, val scope: ReportScope)

@Composable
private fun GroupRow(group: StrengthGroupUi, ctx: StrengthCtx, onOpen: () -> Unit) {
    val cut = ctx.cut
    val names = ctx.names
    val live = ctx.live
    val scope = ctx.scope
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
                .testTag("${scope.tag}_strength_group_${group.group.name.lowercase()}"),
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
            ExerciseGlyphs(rows, ctx)
        }
        rows.filter {
            it.state == ProgressGlyph.Paused
        }.forEach { PausedRow(it, names.getValue(it.exerciseId), live, scope) }
    }
}

@Composable
private fun ExerciseGlyphs(rows: List<ExerciseProgressUi>, ctx: StrengthCtx) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        rows.forEach { exercise ->
            ProgressStateIcon(
                glyph = exercise.state,
                modifier = Modifier.testTag("${ctx.scope.tag}_strength_exercise_${exercise.exerciseId}"),
                contentDescription = stringResource(
                    Res.string.week_report_strength_exercise_cd,
                    ctx.names.getValue(exercise.exerciseId),
                    glyphLabel(exercise.state, ctx.cut),
                ),
            )
        }
    }
}
