package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.phase_diet_break_help
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_cut_rate_label
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_cut_rate_offline
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_cut_rate_option_cd
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_diet_break_help_cd
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_diet_break_label
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_diet_break_offline
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_options_progress_cd
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppOption
import io.github.lopezjavier.appfitness.designsystem.AppSwitchRow
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.HelpContent
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.SegmentedSelector
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.PhaseOptions
import org.jetbrains.compose.resources.stringResource

/** "0,5 %", "0,75 %", "1 %": the percent of body weight per week a segment shows. */
internal fun cutRateText(pct: Double): String = AppNumberFormat.decimal(pct, maxDecimals = 2) + " %"

/**
 * The options of a cut plan (R-42 live controls): the weekly rate as a segmented selector and the diet-break
 * switch with its "?". Both are inert while [saving] or offline, with the standard hint; [saving] shows a progress
 * indicator in the row.
 */
@Composable
internal fun CutOptions(
    options: PhaseOptions,
    saving: Boolean,
    online: Boolean,
    onRate: (Double) -> Unit,
    onDietBreak: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = online && !saving
    val rateOptions = PhaseText.cutRates.map { pct ->
        AppOption(
            label = cutRateText(pct),
            testTag = "plan_proposal_cut_rate_option_$pct",
            contentDescription = stringResource(Res.string.plan_proposal_cut_rate_option_cd, cutRateText(pct)),
        )
    }
    val selected = PhaseText.cutRates.indexOf(options.cutRatePct).takeIf { it >= 0 }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                text = stringResource(Res.string.plan_proposal_cut_rate_label),
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            if (saving) {
                val description = stringResource(Res.string.plan_proposal_options_progress_cd)
                CircularProgressIndicator(
                    color = AppTheme.colors.neonLime,
                    modifier = Modifier.size(Spacing.xl)
                        .testTag("plan_proposal_options_progress")
                        .semantics { contentDescription = description },
                )
            }
        }
        SegmentedSelector(
            options = rateOptions,
            selectedIndex = selected,
            onSelect = { onRate(PhaseText.cutRates[it]) },
            modifier = Modifier.testTag("plan_proposal_cut_rate_selector"),
            enabled = enabled,
        )
        if (!online) OfflineHint(stringResource(Res.string.plan_proposal_cut_rate_offline))
        AppSwitchRow(
            label = stringResource(Res.string.plan_proposal_diet_break_label),
            checked = options.dietBreak,
            onCheckedChange = onDietBreak,
            modifier = Modifier.testTag("plan_proposal_diet_break_switch"),
            enabled = enabled,
            help = HelpContent.fromBody(stringResource(Res.string.phase_diet_break_help)),
            helpContentDescription = stringResource(Res.string.plan_proposal_diet_break_help_cd),
            helpTestTag = "plan_proposal_diet_break_help",
        )
        if (!online) OfflineHint(stringResource(Res.string.plan_proposal_diet_break_offline))
    }
}
