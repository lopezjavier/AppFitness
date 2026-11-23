package io.github.lopezjavier.appfitness.feature.analytics.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_live_trophy
import io.github.lopezjavier.appfitness.composeapp.resources.stats_records_title
import io.github.lopezjavier.appfitness.composeapp.resources.stats_records_total
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_record_amount
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_record_e1rm
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_record_heaviest
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppPaint
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.RecordBadge
import io.github.lopezjavier.appfitness.designsystem.SectionCard
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.semiBold
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private val RecordsHeight = Spacing.xl * 6

/** From this font scale the badge of a record moves under its facts line (C-199). */
private const val RECORD_STACK_FONT_SCALE = 1.3f

/**
 * Records of the range (`07` §3 (D2)): gold badge with the amount per record, "N récords" when there are more.
 * No card for an Empty or failed section (C-203: the failure is shown once, by the progress card); a Loading section
 * keeps a skeleton card.
 */
@Composable
internal fun PeriodRecordsCard(records: SectionState<PeriodRecordsUi>, modifier: Modifier = Modifier) {
    if (records != SectionState.Loading && records !is SectionState.Content) return
    SectionCard(stringResource(Res.string.stats_records_title), modifier, testTag = "stats_records_card") {
        StatsBlock(records, StatsSection.Records, RecordsHeight, onRetry = {}) { data ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                data.items.forEachIndexed { index, record -> PeriodRecordRow(record, index) }
                if (data.total > data.items.size) RecordsTotal(data.total)
            }
        }
    }
}

@Composable
private fun PeriodRecordRow(record: PeriodRecordUi, index: Int) {
    val facts = stringResource(
        if (record.e1rm) Res.string.week_report_record_e1rm else Res.string.week_report_record_heaviest,
        AppNumberFormat.decimal(record.previousKg),
    )
    val amount = stringResource(
        Res.string.week_report_record_amount,
        AppNumberFormat.decimal(record.valueKg),
        AppNumberFormat.decimal(record.valueKg - record.previousKg),
    )
    val stacked = LocalDensity.current.fontScale >= RECORD_STACK_FONT_SCALE
    val tag = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("stats_record_${index + 1}")
    val details: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(record.name, style = AppTheme.typography.bodyLarge.semiBold(), color = AppTheme.colors.textPrimary)
            Text(facts, style = AppTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
            if (stacked) RecordBadge(amount)
        }
    }
    if (stacked) {
        details(tag)
    } else {
        Row(tag, Arrangement.spacedBy(Spacing.md), Alignment.CenterVertically) {
            details(Modifier.weight(1f))
            RecordBadge(amount)
        }
    }
}

@Composable
private fun RecordsTotal(total: Int) {
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("stats_records_total"),
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
            text = pluralStringResource(Res.plurals.stats_records_total, total, total),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
        )
    }
}
