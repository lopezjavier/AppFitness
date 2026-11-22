package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.block_report_records_title
import io.github.lopezjavier.appfitness.composeapp.resources.block_report_records_total
import io.github.lopezjavier.appfitness.composeapp.resources.ic_live_trophy
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_lift_e1rm
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_lift_margin
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_lift_provisional
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_lift_stalled
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_lift_up
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_lifts_title
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_record_amount
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_record_e1rm
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_record_heaviest
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_records_title
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppPaint
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.RecordBadge
import io.github.lopezjavier.appfitness.designsystem.ReportMarks
import io.github.lopezjavier.appfitness.designsystem.SectionCard
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.Sparkline
import io.github.lopezjavier.appfitness.designsystem.TrendDirection
import io.github.lopezjavier.appfitness.designsystem.TrendPill
import io.github.lopezjavier.appfitness.designsystem.TrendTone
import io.github.lopezjavier.appfitness.designsystem.semiBold
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Main lifts (`07` §3 (2)): per lift its name, the state as a pill (arrow plus words, never colour alone) and a
 * sparkline of the weekly best e1RM with the deload weeks drawn hollow. At most 6 rows come from the snapshot.
 */
@Composable
internal fun WeekReportLiftsCard(
    lifts: List<LiftUi>,
    names: Map<String, String>,
    scope: ReportScope = ReportScope.Week,
) {
    val rows = lifts.filter { it.exerciseId in names }
    if (rows.isEmpty()) return
    SectionCard(stringResource(Res.string.week_report_lifts_title), testTag = "${scope.tag}_section_lifts") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            rows.forEach { LiftRow(it, names.getValue(it.exerciseId), scope) }
        }
    }
}

@Composable
private fun LiftRow(lift: LiftUi, name: String, scope: ReportScope) {
    val latest = lift.points.lastOrNull()
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("${scope.tag}_lift_${lift.exerciseId}"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                name,
                style = AppTheme.typography.bodyLarge.semiBold(),
                color = AppTheme.colors.textPrimary,
            )
            latest?.let {
                Text(
                    stringResource(Res.string.week_report_lift_e1rm, AppNumberFormat.decimal(it.e1rmKg)),
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary,
                )
            }
            LiftStatePill(lift)
        }
        Sparkline(
            values = lift.points.map { it.e1rmKg },
            modifier = Modifier.width(ReportMarks.SparklineWidth),
            hollowPoints = lift.points.mapIndexedNotNull { index, point -> index.takeIf { point.deload } }.toSet(),
        )
    }
}

@Composable
private fun LiftStatePill(lift: LiftUi) {
    val change = lift.changeKg?.let { AppNumberFormat.decimal(it) }.orEmpty()
    val pct = lift.changePct?.let { AppNumberFormat.decimal(it) }.orEmpty()
    when (lift.state) {
        LiftState.Up -> TrendPill(
            stringResource(Res.string.week_report_lift_up, change, pct),
            TrendTone.Favorable,
            direction = TrendDirection.Up,
        )

        LiftState.Margin -> TrendPill(
            stringResource(Res.string.week_report_lift_margin, change),
            TrendTone.Neutral,
            direction = TrendDirection.Flat,
        )

        LiftState.Stalled -> TrendPill(
            stringResource(Res.string.week_report_lift_stalled),
            TrendTone.Neutral,
            direction = TrendDirection.Flat,
        )

        LiftState.Provisional -> TrendPill(
            stringResource(Res.string.week_report_lift_provisional),
            TrendTone.Neutral,
        )
    }
}

/** Records of the week (`07` §3 (3)): one row per record, the amount in a gold [RecordBadge]. No card without one. */
@Composable
internal fun WeekReportRecordsCard(
    records: List<RecordUi>,
    names: Map<String, String>,
    scope: ReportScope = ReportScope.Week,
    /** The block's record count (`07` §3 (A3)); when above the badges shown, a trophy line says how many there are. */
    total: Int? = null,
) {
    val rows = records.filter { it.exerciseId in names }
    if (rows.isEmpty()) return
    val title = stringResource(
        if (scope == ReportScope.Block) Res.string.block_report_records_title else Res.string.week_report_records_title,
    )
    SectionCard(title, testTag = "${scope.tag}_section_records") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            rows.forEachIndexed { index, record -> RecordRow(record, names.getValue(record.exerciseId), scope, index) }
            if (total != null && total > rows.size) RecordsTotalLine(total, scope)
        }
    }
}

@Composable
private fun RecordRow(record: RecordUi, name: String, scope: ReportScope, index: Int) {
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("${scope.tag}_record_${index + 1}"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(name, style = AppTheme.typography.bodyLarge.semiBold(), color = AppTheme.colors.textPrimary)
            Text(
                text = stringResource(
                    if (record.kind == RecordKind.E1rm) {
                        Res.string.week_report_record_e1rm
                    } else {
                        Res.string.week_report_record_heaviest
                    },
                    AppNumberFormat.decimal(record.previousKg),
                ),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
            )
        }
        RecordBadge(
            stringResource(
                Res.string.week_report_record_amount,
                AppNumberFormat.decimal(record.valueKg),
                AppNumberFormat.decimal(record.valueKg - record.previousKg),
            ),
        )
    }
}

@Composable
private fun RecordsTotalLine(total: Int, scope: ReportScope) {
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("${scope.tag}_records_total"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_live_trophy),
            contentDescription = null,
            tint = AppPaint.Gold,
            modifier = Modifier.size(Spacing.xl),
        )
        Text(
            text = pluralStringResource(Res.plurals.block_report_records_total, total, total),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
        )
    }
}
