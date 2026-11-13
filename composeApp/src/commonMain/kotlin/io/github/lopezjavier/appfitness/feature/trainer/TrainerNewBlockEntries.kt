package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_my_training
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_new_block
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_new_block_action
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_weeks_in_phase
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_welcome_back_line
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_welcome_back_title
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.PhaseWeeks
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** "Bloque nuevo" and "Mi entrenamiento" under the active plan while its block is saved (T-240). */
@Composable
internal fun NewBlockEntries(online: Boolean, onEvent: (TrainerEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        GhostButton(
            text = stringResource(Res.string.trainer_new_block),
            onClick = { onEvent(TrainerEvent.OpenNewBlock) },
            modifier = Modifier.fillMaxWidth().testTag("trainer_new_block"),
            enabled = online,
        )
        if (!online) OfflineHint(stringResource(Res.string.trainer_new_block_action))
        GhostButton(
            text = stringResource(Res.string.trainer_my_training),
            onClick = { onEvent(TrainerEvent.OpenMyTraining) },
            modifier = Modifier.fillMaxWidth().testTag("trainer_my_training"),
            enabled = online,
        )
    }
}

/** "Bienvenido de nuevo" after a break of 14+ days (US-107): one line and the "Bloque nuevo" action. */
@Composable
internal fun WelcomeBackCard(online: Boolean, onEvent: (TrainerEvent) -> Unit) {
    AppCard(Modifier.fillMaxWidth().testTag("trainer_welcome_back_card")) {
        Text(
            text = stringResource(Res.string.trainer_welcome_back_title),
            style = AppTheme.typography.titleMedium,
            color = AppTheme.colors.textPrimary,
        )
        Text(
            text = stringResource(Res.string.trainer_welcome_back_line),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
        )
        GhostButton(
            text = stringResource(Res.string.trainer_new_block),
            onClick = { onEvent(TrainerEvent.OpenNewBlock) },
            modifier = Modifier.fillMaxWidth().testTag("trainer_welcome_back_new_block"),
            enabled = online,
        )
        if (!online) OfflineHint(stringResource(Res.string.trainer_new_block_action))
    }
}

/** "Llevas n semanas en esta fase" under the phase line: plain secondary text, no colour or icon (US-111). */
@Composable
internal fun WeeksInPhaseLine(weeks: PhaseWeeks) {
    Text(
        text = pluralStringResource(Res.plurals.trainer_plan_weeks_in_phase, weeks.weeks, weeks.weeks),
        style = AppTheme.typography.bodyMedium,
        color = AppTheme.colors.textSecondary,
        modifier = Modifier.testTag("trainer_plan_weeks_in_phase"),
    )
}
