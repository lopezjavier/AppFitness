package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_change_since
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_site_open_label
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_value_cm
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_waist_margin
import io.github.lopezjavier.appfitness.designsystem.AppDateFormat
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.MetricCard
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.Sparkline
import io.github.lopezjavier.appfitness.designsystem.TrendDirection
import io.github.lopezjavier.appfitness.designsystem.TrendPill
import io.github.lopezjavier.appfitness.designsystem.TrendPillDefaults
import io.github.lopezjavier.appfitness.designsystem.TrendTone
import io.github.lopezjavier.appfitness.domain.usecase.CircumferenceChange
import io.github.lopezjavier.appfitness.domain.usecase.CircumferenceDisplayRule
import io.github.lopezjavier.appfitness.domain.usecase.CircumferenceSiteSummary
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/** Vertical floor of the cell sparkline for a site without a measurement margin, in cm. */
private const val CM_MIN_SPAN = 2.0

/**
 * One site of the "Circunferencias" grid (T-326): a [MetricCard] like the BMI and body-fat cards above it, so the
 * Peso screen has one tile language. The site name is the card label (wraps, never ellipsized: U2 of T-324), the
 * latest value is the headline ("84,5 cm", `headlineMetric`, stepping down once to `titleLarge` in a narrow cell,
 * C-160); under it a full-width [Sparkline] of every measurement day (the latest lit; its vertical span is at least
 * twice the site's margin, so a move inside the noise does not look like a cliff) and the change slot
 * ([CircumferenceChangeLine]), which wraps inside the half-width cell. With one measurement there is no sparkline. The
 * whole card is one `Role.Button` that opens the site's screen ("Ver historial"); no chevron, as on the other tiles.
 * The sparkline is decorative (R-116): the value and the change are written in the same card; every point is written
 * in the site screen. Tags: `circumference_site_cell_<site>` plus `_sparkline`, `_change` and `_margin_note`.
 */
@Composable
internal fun CircumferenceSiteCell(
    summary: CircumferenceSiteSummary,
    today: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tag = "circumference_site_cell_${summary.site.name.lowercase()}"
    MetricCard(
        label = circumferenceSiteLabel(summary.site),
        value = stringResource(Res.string.circumference_value_cm, decimalText(summary.latestCm)),
        modifier = modifier.testTag(tag),
        supporting = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (summary.history.size > 1) {
                    Sparkline(
                        values = summary.history.map { it.cm },
                        modifier = Modifier.fillMaxWidth().testTag("${tag}_sparkline"),
                        hollowPoints = emptySet(),
                        minSpan = CircumferenceDisplayRule.marginCm(summary.site)?.let { it * 2 } ?: CM_MIN_SPAN,
                    )
                }
                CircumferenceChangeLine(summary.change, summary.latestDate, today, tagPrefix = tag)
            }
        },
        onClick = onClick,
        clickLabel = stringResource(Res.string.circumference_site_open_label),
    )
}

/**
 * The change slot of a site (T-324; a grid cell and the site screen hero): with a comparable pair, a neutral
 * [TrendPill] "−1,0 cm" (never a verdict colour: whether a site should go up or down depends on the goal) followed by
 * "desde el 26 sep" and, under the site's margin,
 * "dentro del margen de medida". Inside the margin the pill's arrow is the flat one, so "no real change" has a shape
 * cue as well as the words (R-41); outside it (or with no margin) the arrow follows the sign. Without a pair the slot
 * is the date of the latest measurement only (`07` §5: no pair, no change line). The slot is tagged
 * `<tagPrefix>_change` and the margin note `<tagPrefix>_margin_note`.
 */
@OptIn(ExperimentalLayoutApi::class) // FlowRow is the wrapping row.
@Composable
internal fun CircumferenceChangeLine(
    change: CircumferenceChange,
    latestDate: LocalDate,
    today: LocalDate,
    tagPrefix: String,
    modifier: Modifier = Modifier,
) {
    when (change) {
        is CircumferenceChange.Compared -> FlowRow(
            modifier.testTag("${tagPrefix}_change"),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            val within = change.withinMargin == true
            TrendPill(
                text = stringResource(Res.string.circumference_value_cm, signedTenths(change.deltaCm)),
                tone = TrendTone.Neutral,
                direction = if (within) TrendDirection.Flat else TrendPillDefaults.direction(change.deltaCm),
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            Text(
                text = stringResource(Res.string.circumference_change_since, AppDateFormat.shortDate(change.refDate)),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            if (within) {
                Text(
                    text = stringResource(Res.string.week_report_waist_margin),
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.align(Alignment.CenterVertically).testTag("${tagPrefix}_margin_note"),
                )
            }
        }

        CircumferenceChange.NoPair -> Text(
            text = AppDateFormat.list(latestDate, today),
            style = AppTheme.typography.bodySmall,
            color = AppTheme.colors.textSecondary,
            modifier = modifier.testTag("${tagPrefix}_change"),
        )
    }
}
