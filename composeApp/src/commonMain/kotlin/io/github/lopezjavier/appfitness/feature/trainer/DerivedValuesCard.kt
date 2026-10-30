package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_card_title
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_confirm
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_confirm_action
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_confirmed
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_help_body
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_help_cd
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.HelpButton
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.CoachDerivedValues
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import org.jetbrains.compose.resources.stringResource

/**
 * The derived-values card (US-68): one [TrainerValueRow] per value, then "Confirmar" (disabled and reading
 * "Confirmado" once everything is confirmed, offline it explains why it is disabled, R-30).
 */
@Composable
@Suppress("LongParameterList") // The card values, its states and the two actions.
internal fun DerivedValuesCard(
    values: CoachDerivedValues,
    confirming: Boolean,
    online: Boolean,
    strategy: GoalStrategy?,
    onEdit: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier.fillMaxWidth().testTag("trainer_card")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.trainer_card_title),
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            HelpButton(
                contentDescription = stringResource(Res.string.trainer_help_cd),
                title = stringResource(Res.string.trainer_card_title),
                body = stringResource(Res.string.trainer_help_body),
                testTag = "trainer_help_button",
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ActivityLevelRow(values, onEdit)
            ExpenditureRow(values)
            WeightTrendRow(values, strategy)
            ExperienceRow(values)
        }
        val allConfirmed = values.allConfirmed
        PrimaryButton(
            text = stringResource(if (allConfirmed) Res.string.trainer_confirmed else Res.string.trainer_confirm),
            onClick = onConfirm,
            enabled = !allConfirmed && !confirming && online,
            loading = confirming,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg).testTag("trainer_confirm_button"),
        )
        if (!online && !allConfirmed) {
            OfflineHint(stringResource(Res.string.trainer_confirm_action), Modifier.padding(top = Spacing.xs))
        }
    }
}
