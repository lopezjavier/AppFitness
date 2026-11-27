package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.block_report_weight_avg_label
import io.github.lopezjavier.appfitness.composeapp.resources.block_report_weight_change
import io.github.lopezjavier.appfitness.composeapp.resources.block_report_weight_gate
import io.github.lopezjavier.appfitness.composeapp.resources.block_report_weight_help_body
import io.github.lopezjavier.appfitness.composeapp.resources.block_report_weight_same
import io.github.lopezjavier.appfitness.composeapp.resources.ic_flag
import io.github.lopezjavier.appfitness.composeapp.resources.ic_monitor_weight
import io.github.lopezjavier.appfitness.composeapp.resources.ic_straighten
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_action_missing_weigh_ins
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_action_missing_weigh_ins_past
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_waist_cd
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_waist_change
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_waist_margin
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_waist_since
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_waist_title
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_weight_avg_label
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_weight_gate
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_weight_gate_cd
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_weight_goal
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_weight_help_body
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_weight_help_cd
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_weight_none
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_weight_same_as_last
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_weight_since_cut
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_weight_since_gain
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_weight_since_maintenance
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_weight_title
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_weight_vs_last
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_weigh_ins
import io.github.lopezjavier.appfitness.designsystem.AppDateFormat
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.HelpButton
import io.github.lopezjavier.appfitness.designsystem.IconBadge
import io.github.lopezjavier.appfitness.designsystem.MeasurementTrack
import io.github.lopezjavier.appfitness.designsystem.ReportMarks
import io.github.lopezjavier.appfitness.designsystem.SectionCard
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.TrendDirection
import io.github.lopezjavier.appfitness.designsystem.TrendPill
import io.github.lopezjavier.appfitness.designsystem.TrendPillDefaults
import io.github.lopezjavier.appfitness.designsystem.TrendTone
import io.github.lopezjavier.appfitness.designsystem.WeighInGateDots
import io.github.lopezjavier.appfitness.designsystem.WeightPaceChart
import io.github.lopezjavier.appfitness.designsystem.WeightPaceChartData
import io.github.lopezjavier.appfitness.designsystem.WeightPaceLabel
import io.github.lopezjavier.appfitness.designsystem.WeightPacePlanLine
import io.github.lopezjavier.appfitness.designsystem.WeightPacePoint
import io.github.lopezjavier.appfitness.domain.model.CircumferenceSite
import io.github.lopezjavier.appfitness.domain.usecase.CircumferenceDisplayRule
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val DAYS_PER_WEEK = 7

/**
 * The Weight card (`07` §3 (4)): the average large, the change as a neutral pill (never a verdict or colour), the
 * plan-week averages as the compact [WeightPaceChart] against the dashed goal line, and the "Tu plan" verdict from the
 * shared pace. Under the weigh-in gate the change is replaced by three gate dots: how many weigh-ins there are.
 */
@Composable
internal fun WeekReportWeightCard(
    weight: WeightUi,
    reportEnd: LocalDate,
    live: Boolean,
    scope: ReportScope = ReportScope.Week,
) {
    val block = scope == ReportScope.Block
    val title = stringResource(Res.string.week_report_weight_title)
    SectionCard(
        title,
        testTag = "${scope.tag}_section_weight",
        help = {
            HelpButton(
                contentDescription = stringResource(Res.string.week_report_weight_help_cd),
                title = title,
                body = stringResource(
                    if (block) Res.string.block_report_weight_help_body else Res.string.week_report_weight_help_body,
                ),
                testTag = "${scope.tag}_weight_help",
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            val avg = weight.avgKg
            when {
                avg == null || weight.days == 0 -> NoWeighIns(live, scope)
                weight.changeKg == null -> GateState(avg, weight, scope)
                else -> AverageAndChange(weight, avg, scope)
            }
            weight.sinceStartKg?.let { kg ->
                Text(
                    text = pluralStringResource(
                        when (weight.phase) {
                            WeightPhaseUi.Cut -> Res.plurals.week_report_weight_since_cut
                            WeightPhaseUi.Gain -> Res.plurals.week_report_weight_since_gain
                            WeightPhaseUi.Maintenance -> Res.plurals.week_report_weight_since_maintenance
                        },
                        weight.sinceStartWeeks,
                        WeekSummaryText.signedKg(kg),
                        weight.sinceStartWeeks,
                    ),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.testTag("${scope.tag}_weight_since_start"),
                )
            }
            if (weight.series.isNotEmpty()) {
                WeightPaceChart(
                    data = chartData(weight, reportEnd),
                    height = ReportMarks.CompactChartHeight,
                    modifier = Modifier.testTag("${scope.tag}_weight_chart"),
                    valueLabel = { AppNumberFormat.decimal(it) },
                )
            }
            weight.goal?.let { GoalLegend(it, scope) }
            weight.pace?.let { PaceVerdict(it, testTag = "${scope.tag}_weight_verdict") }
        }
    }
}

@Composable
private fun AverageAndChange(weight: WeightUi, avg: Double, scope: ReportScope) {
    val block = scope == ReportScope.Block
    val change = weight.changeKg ?: return
    val text = WeekSummaryText.signedKg(change)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    if (block) Res.string.block_report_weight_avg_label else Res.string.week_report_weight_avg_label,
                ).uppercase(),
                style = AppTheme.typography.labelTechnical,
                color = AppTheme.colors.textSecondary,
            )
            Text(
                text = "${WeekSummaryText.kg(avg)} kg",
                style = AppTheme.typography.headlineMetric,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.testTag("${scope.tag}_weight_avg"),
            )
        }
        if (text == "0") {
            TrendPill(
                stringResource(
                    if (block) Res.string.block_report_weight_same else Res.string.week_report_weight_same_as_last,
                ),
                TrendTone.Neutral,
                Modifier.testTag("${scope.tag}_weight_change"),
                direction = TrendDirection.Flat,
            )
        } else {
            TrendPill(
                stringResource(
                    if (block) Res.string.block_report_weight_change else Res.string.week_report_weight_vs_last,
                    text,
                ),
                TrendTone.Neutral,
                Modifier.testTag("${scope.tag}_weight_change"),
                direction = TrendPillDefaults.direction(change),
            )
        }
    }
}

/** Below the gate: dots for the weigh-in days there are against the three needed, and the plain sentence. */
@Composable
private fun GateState(avg: Double, weight: WeightUi, scope: ReportScope) {
    // Block: the gate needs 3 days in the first AND the last plan week, so the dots show the scarcer one.
    val block = scope == ReportScope.Block
    val days = if (block) minOf(weight.days, weight.prevDays) else weight.days
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("${scope.tag}_weight_gate"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GateDots(days, scope)
        Text(
            text = stringResource(
                if (block) Res.string.block_report_weight_gate else Res.string.week_report_weight_gate,
                WeekSummaryText.kg(avg),
                pluralStringResource(Res.plurals.week_summary_weigh_ins, days, days),
            ),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun NoWeighIns(live: Boolean, scope: ReportScope) {
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("${scope.tag}_weight_none"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(Res.drawable.ic_monitor_weight, AppTheme.colors.athleticCyan)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                stringResource(Res.string.week_report_weight_none),
                style = AppTheme.typography.bodyLarge,
                color = AppTheme.colors.textPrimary,
            )
            Text(
                stringResource(
                    if (live) {
                        Res.string.week_report_action_missing_weigh_ins
                    } else {
                        Res.string.week_report_action_missing_weigh_ins_past
                    },
                ),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
            )
        }
        GateDots(0, scope)
    }
}

/** Three slots, one per weigh-in day the gate needs: lit when logged, hollow when missing (shape and count). */
@Composable
private fun GateDots(have: Int, scope: ReportScope) {
    WeighInGateDots(
        have = have,
        total = WEIGH_IN_GATE,
        description = stringResource(
            Res.string.week_report_weight_gate_cd,
            have.coerceAtMost(WEIGH_IN_GATE),
            WEIGH_IN_GATE,
        ),
        modifier = Modifier.testTag("${scope.tag}_weight_gate_dots"),
    )
}

@Composable
private fun GoalLegend(goal: WeightGoalUi, scope: ReportScope) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(Res.drawable.ic_flag, AppTheme.colors.textSecondary, size = Spacing.xl, iconSize = Spacing.lg)
        Text(
            text = stringResource(
                Res.string.week_report_weight_goal,
                WeekSummaryText.kg(goal.kg),
                AppDateFormat.shortDate(goal.deadline),
            ),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.testTag("${scope.tag}_weight_goal"),
        )
    }
}

/** Plan-week averages as the points, the goal line to the deadline (`07` §5 (6)); the pace figure is in the verdict. */
private fun chartData(weight: WeightUi, reportEnd: LocalDate): WeightPaceChartData {
    val weekly = weight.series.mapIndexed { index, point -> WeightPacePoint(index.toFloat(), point.avgKg) }
    val lastX = weekly.lastIndex.toFloat()
    val plan = weight.goal?.let {
        val weeksLeft = (reportEnd.daysUntil(it.deadline).coerceAtLeast(0)).toFloat() / DAYS_PER_WEEK
        WeightPacePlanLine(0f, weekly.first().kg, lastX + weeksLeft, it.kg)
    }
    val last = weekly.last()
    return WeightPaceChartData(
        weekly = weekly,
        plan = plan,
        xLabels = listOf(WeightPaceLabel(last.x, "${AppNumberFormat.decimal(last.kg)} kg", emphasized = true)),
    )
}

/**
 * The Waist card (`07` §3 (5), only in a week with a new measurement): the latest value, a two-point track from the
 * older measurement (hollow) to the new one (filled), the change as a neutral pill and, under 3,0 cm, "dentro del
 * margen de medida".
 */
@Composable
internal fun WeekReportWaistCard(waist: WaistUi, scope: ReportScope = ReportScope.Week) {
    val description = stringResource(
        Res.string.week_report_waist_cd,
        WeekSummaryText.kg(waist.cm),
        AppDateFormat.shortDate(waist.date),
        WeekSummaryText.kg(waist.refCm),
        AppDateFormat.shortDate(waist.refDate),
    )
    SectionCard(stringResource(Res.string.week_report_waist_title), testTag = "${scope.tag}_section_waist") {
        Column(
            Modifier.semantics(mergeDescendants = true) { contentDescription = description },
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(Res.drawable.ic_straighten, AppTheme.colors.athleticCyan)
                Text(
                    text = "${WeekSummaryText.kg(waist.cm)} cm",
                    style = AppTheme.typography.headlineMetric,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f).testTag("${scope.tag}_waist_value"),
                )
                TrendPill(
                    stringResource(Res.string.week_report_waist_change, WeekSummaryText.signedKg(waist.changeCm)),
                    TrendTone.Neutral,
                    direction = TrendPillDefaults.direction(waist.changeCm),
                )
            }
            MeasurementTrack()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    stringResource(Res.string.week_report_waist_since, AppDateFormat.shortDate(waist.refDate)) +
                        " · ${WeekSummaryText.kg(waist.refCm)} cm",
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary,
                )
                Text(
                    AppDateFormat.shortDate(waist.date),
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary,
                )
            }
            val margin = CircumferenceDisplayRule.marginCm(CircumferenceSite.Waist)
            if (margin != null && kotlin.math.abs(waist.changeCm) < margin) {
                Text(
                    text = stringResource(Res.string.week_report_waist_margin),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.testTag("${scope.tag}_waist_margin"),
                )
            }
        }
    }
}
