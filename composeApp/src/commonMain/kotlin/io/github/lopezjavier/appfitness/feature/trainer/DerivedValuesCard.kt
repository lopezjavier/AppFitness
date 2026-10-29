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
import io.github.lopezjavier.appfitness.composeapp.resources.ic_edit
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_card_title
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_confirm
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_confirm_action
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_confirmed
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_edit_activity_cd
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_experience_advanced
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_experience_beginner
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_experience_intermediate
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_help_body
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_help_cd
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_kcal_value
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_reask
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_row_activity_level
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_row_experience
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_row_total_expenditure
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_row_weight_trend
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_source_activity_data
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_source_adaptive
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_source_edited
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_source_formula
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_source_profile
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_source_sessions
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_source_weigh_ins
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_value_unknown
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.HelpButton
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.CoachDerivedValues
import io.github.lopezjavier.appfitness.domain.model.DerivedExperience
import io.github.lopezjavier.appfitness.domain.model.DerivedSource
import io.github.lopezjavier.appfitness.domain.model.ValueState
import io.github.lopezjavier.appfitness.feature.biometrics.activityTitle
import org.jetbrains.compose.resources.stringResource

/**
 * The derived-values card (US-68): one [TrainerValueRow] per value, then "Confirmar" (disabled and reading
 * "Confirmado" once everything is confirmed, offline it explains why it is disabled, R-30).
 */
@Composable
internal fun DerivedValuesCard(
    values: CoachDerivedValues,
    confirming: Boolean,
    online: Boolean,
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
            WeightTrendRow(values)
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

@Composable
private fun ActivityLevelRow(values: CoachDerivedValues, onEdit: () -> Unit) {
    val level = values.activityLevel
    val sourceText = when (level.state) {
        ValueState.Reask -> stringResource(Res.string.trainer_reask)

        ValueState.Edited -> stringResource(Res.string.trainer_source_edited)

        else -> stringResource(
            if (level.source == DerivedSource.Profile) {
                Res.string.trainer_source_profile
            } else {
                Res.string.trainer_source_activity_data
            },
        )
    }
    TrainerValueRow(
        key = "activity_level",
        headline = stringResource(Res.string.trainer_row_activity_level),
        value =
        level.value?.let { stringResource(activityTitle(it)) } ?: stringResource(Res.string.trainer_value_unknown),
        supportingText = sourceText,
        provisional = level.provisional,
        missing = level.missing,
        trailing = {
            AppIconButton(
                contentDescription = stringResource(Res.string.trainer_edit_activity_cd),
                icon = Res.drawable.ic_edit,
                testTag = "trainer_edit_activity_button",
                onClick = onEdit,
            )
        },
    )
}

@Composable
private fun ExpenditureRow(values: CoachDerivedValues) {
    val expenditure = values.totalExpenditure
    val sourceText = if (expenditure.state == ValueState.Reask) {
        stringResource(Res.string.trainer_reask)
    } else {
        stringResource(
            if (expenditure.source == DerivedSource.Adaptive) {
                Res.string.trainer_source_adaptive
            } else {
                Res.string.trainer_source_formula
            },
        )
    }
    TrainerValueRow(
        key = "total_expenditure",
        headline = stringResource(Res.string.trainer_row_total_expenditure),
        value =
        expenditure.kcal?.let { stringResource(Res.string.trainer_kcal_value, AppNumberFormat.integer(it.toInt())) }
            ?: stringResource(Res.string.trainer_value_unknown),
        supportingText = sourceText,
        provisional = expenditure.provisional,
        missing = expenditure.missing,
        notes = { UnreliableIntakeNote(values) },
    )
}

@Composable
private fun WeightTrendRow(values: CoachDerivedValues) {
    val trend = values.weightTrend
    TrainerValueRow(
        key = "weight_trend",
        headline = stringResource(Res.string.trainer_row_weight_trend),
        value = trendText(trend),
        supportingText = stringResource(
            if (trend.state == ValueState.Reask) Res.string.trainer_reask else Res.string.trainer_source_weigh_ins,
        ),
        provisional = trend.provisional,
        missing = trend.missing,
        notes = { LowConfidenceTrendNote(trend) },
    )
}

@Composable
private fun ExperienceRow(values: CoachDerivedValues) {
    val experience = values.experience
    TrainerValueRow(
        key = "experience",
        headline = stringResource(Res.string.trainer_row_experience),
        value = experience.value?.let { stringResource(experienceLabel(it)) }
            ?: stringResource(Res.string.trainer_value_unknown),
        supportingText = stringResource(
            if (experience.state == ValueState.Reask) Res.string.trainer_reask else Res.string.trainer_source_sessions,
        ),
        provisional = experience.provisional,
        missing = experience.missing,
    )
}

private fun experienceLabel(experience: DerivedExperience) = when (experience) {
    DerivedExperience.Beginner -> Res.string.trainer_experience_beginner
    DerivedExperience.Intermediate -> Res.string.trainer_experience_intermediate
    DerivedExperience.Advanced -> Res.string.trainer_experience_advanced
}
