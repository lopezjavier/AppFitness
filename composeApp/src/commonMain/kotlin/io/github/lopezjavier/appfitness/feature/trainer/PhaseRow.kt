package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.phase_help_cd
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.HelpButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.PhaseReason
import org.jetbrains.compose.resources.stringResource

/**
 * The phase archetype (05 section 9): the phase label, the one-sentence reason and a "?" with the explanation.
 * The row is tagged `<testTagPrefix>_phase_row`, its help `<testTagPrefix>_phase_help`. Reused by the review (T-191).
 */
@Composable
internal fun PhaseRow(phase: Phase, reason: PhaseReason, testTagPrefix: String, modifier: Modifier = Modifier) {
    val label = stringResource(PhaseText.label(phase))
    AppCard(modifier.fillMaxWidth().testTag("${testTagPrefix}_phase_row")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = label,
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(PhaseText.reason(phase, reason)),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                )
            }
            HelpButton(
                contentDescription = stringResource(Res.string.phase_help_cd),
                title = label,
                body = stringResource(PhaseText.help(phase)),
                testTag = "${testTagPrefix}_phase_help",
            )
        }
    }
}
