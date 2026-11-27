package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_change_since
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_site_open_label
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_value_cm
import io.github.lopezjavier.appfitness.composeapp.resources.ic_chevron_right
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_waist_margin
import io.github.lopezjavier.appfitness.designsystem.AppDateFormat
import io.github.lopezjavier.appfitness.designsystem.AppTheme
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
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** From this font scale the value takes its own line under the name (C-160). */
private const val STACK_FONT_SCALE = 1.3f

/** The site name wraps to at most this many lines and is never ellipsized (R-110). */
private const val NAME_MAX_LINES = 2

/** Vertical floor of the row sparkline for a site without a measurement margin, in cm. */
private const val CM_MIN_SPAN = 2.0

/**
 * One site of "Circunferencias" (T-324): the name, the latest value and a chevron on the first line (from font scale
 * 1.3 the value goes on its own line under the name), a full-width [Sparkline] of every measurement day (the latest
 * lit; its vertical span is at least twice the site's margin, so a move inside the noise does not look like a cliff),
 * then the change slot ([CircumferenceChangeLine]). With one measurement there is no sparkline. The whole row is one
 * `Role.Button` that opens the site's screen ("Ver historial"). The sparkline is decorative (R-116): the figures it
 * summarises are the value and the change written in the same row; every point is written in the site screen.
 */
@Composable
internal fun CircumferenceSiteRow(
    summary: CircumferenceSiteSummary,
    today: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tag = "circumference_site_row_${summary.site.name.lowercase()}"
    Column(
        modifier
            .fillMaxWidth()
            .testTag(tag)
            .clickable(
                onClickLabel = stringResource(Res.string.circumference_site_open_label),
                role = Role.Button,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {}
            .heightIn(min = Spacing.minTouchTarget)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SiteRowHeadline(summary, tag)
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
}

/** The name, the value and the chevron; from [STACK_FONT_SCALE] the value goes on its own line under the name. */
@Composable
private fun SiteRowHeadline(summary: CircumferenceSiteSummary, tag: String) {
    val stacked = LocalDensity.current.fontScale >= STACK_FONT_SCALE
    val value: @Composable () -> Unit = {
        Text(
            text = stringResource(Res.string.circumference_value_cm, decimalText(summary.latestCm)),
            style = AppTheme.typography.titleLarge,
            color = AppTheme.colors.textPrimary,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.testTag("${tag}_value"),
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = circumferenceSiteLabel(summary.site),
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
                maxLines = NAME_MAX_LINES,
                modifier = Modifier.weight(1f),
            )
            if (!stacked) value()
            Icon(
                painter = painterResource(Res.drawable.ic_chevron_right),
                contentDescription = null,
                tint = AppTheme.colors.textSecondary,
                modifier = Modifier.size(Spacing.xl),
            )
        }
        if (stacked) value()
    }
}

/**
 * The change slot of a site (T-324): with a comparable pair, a neutral [TrendPill] "−1,0 cm" (never a verdict colour:
 * whether a site should go up or down depends on the goal) followed by "desde el 26 sep" and, under the site's margin,
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
