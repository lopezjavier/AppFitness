package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.activity_help_adaptive
import io.github.lopezjavier.appfitness.composeapp.resources.activity_help_cd
import io.github.lopezjavier.appfitness.composeapp.resources.activity_help_maintenance
import io.github.lopezjavier.appfitness.composeapp.resources.ic_info
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_provisional
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_sheet_title
import io.github.lopezjavier.appfitness.designsystem.AppListRow
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.HelpButton
import io.github.lopezjavier.appfitness.designsystem.IconBadge
import io.github.lopezjavier.appfitness.designsystem.IconBadgeDefaults
import io.github.lopezjavier.appfitness.designsystem.RowGroupCard
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.StatusChip
import io.github.lopezjavier.appfitness.designsystem.StatusTone
import org.jetbrains.compose.resources.stringResource

/**
 * The activity level when it is still the user's answer but does not block "Aceptar" (ADR-109): the "Provisional"
 * chip stays (it is still an answer) and a "?" says why it does not change the calories (R-57: a help, never an
 * inline note). Tags: `<screen>_activity_row`, `<screen>_activity_chip`, `<screen>_activity_help`.
 */
@Composable
internal fun ActivityNoChangeRow(
    row: PlanInputRow?,
    note: ActivityNoChange?,
    screen: String,
    modifier: Modifier = Modifier,
) {
    if (row == null || note == null) return
    RowGroupCard(
        modifier = modifier,
        rows = listOf {
            AppListRow(
                headline = row.labelText(),
                modifier = Modifier.padding(horizontal = Spacing.lg).testTag("${screen}_activity_row"),
                supportingContent = {
                    StatusChip(
                        text = stringResource(Res.string.trainer_provisional),
                        tone = StatusTone.Caution,
                        modifier = Modifier.testTag("${screen}_activity_chip"),
                    )
                },
                leading = {
                    IconBadge(
                        Res.drawable.ic_info,
                        AppTheme.colors.energyAmber,
                        size = IconBadgeDefaults.SmallSize,
                        iconSize = IconBadgeDefaults.SmallIconSize,
                    )
                },
                trailing = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text(
                            text = row.valueText(),
                            style = AppTheme.typography.bodyMedium,
                            color = AppTheme.colors.textSecondary,
                        )
                        HelpButton(
                            contentDescription = stringResource(Res.string.activity_help_cd),
                            title = stringResource(Res.string.trainer_sheet_title),
                            body = stringResource(
                                if (note == ActivityNoChange.Maintenance) {
                                    Res.string.activity_help_maintenance
                                } else {
                                    Res.string.activity_help_adaptive
                                },
                            ),
                            testTag = "${screen}_activity_help",
                        )
                    }
                },
            )
        },
    )
}
