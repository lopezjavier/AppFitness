package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_edit
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_edit_activity_cd
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_experience_advanced
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_experience_beginner
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_experience_intermediate
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_kcal_value
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_reask
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_row_activity_level
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_row_experience
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_row_total_expenditure
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_row_weight_trend
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_source_activity_data
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_source_adaptive
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_source_adaptive_cd
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_source_adaptive_chip
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_source_edited
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_source_formula
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_source_formula_cd
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_source_formula_chip
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_source_profile
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_source_sessions
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_trend_cd
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_trend_margin
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_trend_source
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_value_unknown
import io.github.lopezjavier.appfitness.composeapp.resources.weight_trend_pill
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.StatusChip
import io.github.lopezjavier.appfitness.designsystem.StatusTone
import io.github.lopezjavier.appfitness.designsystem.TrendPill
import io.github.lopezjavier.appfitness.designsystem.TrendPillDefaults
import io.github.lopezjavier.appfitness.designsystem.TrendTone
import io.github.lopezjavier.appfitness.domain.model.CoachDerivedValues
import io.github.lopezjavier.appfitness.domain.model.DerivedExperience
import io.github.lopezjavier.appfitness.domain.model.DerivedSource
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.ValueState
import io.github.lopezjavier.appfitness.feature.biometrics.activityTitle
import io.github.lopezjavier.appfitness.feature.weight.rateText
import org.jetbrains.compose.resources.stringResource

/** [onEdit] null hides the pencil: the plan screen shows the values it was built from, read-only. */
@Composable
internal fun ActivityLevelRow(values: CoachDerivedValues, onEdit: (() -> Unit)?) {
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
        trailing = onEdit?.let { edit ->
            {
                AppIconButton(
                    contentDescription = stringResource(Res.string.trainer_edit_activity_cd),
                    icon = Res.drawable.ic_edit,
                    testTag = "trainer_edit_activity_button",
                    onClick = edit,
                )
            }
        },
    )
}

@Composable
internal fun ExpenditureRow(values: CoachDerivedValues, source: ExpenditureSource? = null) {
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
        trailing = source?.let { { ExpenditureSourceChip(it) } },
        notes = { UnreliableIntakeNote(values) },
    )
}

@Composable
private fun ExpenditureSourceChip(source: ExpenditureSource) {
    val adaptive = source == ExpenditureSource.Adaptive
    val description = stringResource(
        if (adaptive) Res.string.trainer_source_adaptive_cd else Res.string.trainer_source_formula_cd,
    )
    StatusChip(
        text = stringResource(
            if (adaptive) Res.string.trainer_source_adaptive_chip else Res.string.trainer_source_formula_chip,
        ),
        tone = if (adaptive) StatusTone.Good else StatusTone.Caution,
        modifier = Modifier.testTag("trainer_source_total_expenditure").semantics { contentDescription = description },
    )
}

/**
 * The weight trend: the signed weekly rate as a [TrendPill] in the row's trailing slot (tone from the goal, neutral
 * when unknown) and the margin of the estimate as the only supporting line. No pill without a slope.
 */
@Composable
internal fun WeightTrendRow(values: CoachDerivedValues, strategy: GoalStrategy?) {
    val trend = values.weightTrend
    val reading = trendReading(trend, strategy)
    val margin = reading.margin
    val supporting = when {
        trend.state == ValueState.Reask -> stringResource(Res.string.trainer_reask)
        margin != null -> stringResource(Res.string.trainer_trend_margin, AppNumberFormat.decimal(margin))
        else -> stringResource(Res.string.trainer_trend_source)
    }
    val slope = reading.slope
    TrainerValueRow(
        key = "weight_trend",
        headline = stringResource(Res.string.trainer_row_weight_trend),
        value = "",
        supportingText = supporting,
        provisional = trend.provisional,
        missing = trend.missing,
        trailing = slope?.let { rate -> { TrendRatePill(rate, reading.tone) } },
        notes = { LowConfidenceTrendNote(trend) },
    )
}

@Composable
private fun TrendRatePill(slope: Double, tone: TrendTone) {
    val description = stringResource(Res.string.trainer_trend_cd, rateText(slope))
    TrendPill(
        text = stringResource(Res.string.weight_trend_pill, rateText(slope)),
        tone = tone,
        modifier = Modifier.testTag("trainer_trend_pill").semantics { contentDescription = description },
        direction = TrendPillDefaults.direction(slope),
        containerColor = AppTheme.colors.surfaceRaised,
    )
}

@Composable
internal fun ExperienceRow(values: CoachDerivedValues) {
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
