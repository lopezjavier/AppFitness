package io.github.lopezjavier.appfitness.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.activity_high_description
import io.github.lopezjavier.appfitness.composeapp.resources.activity_high_title
import io.github.lopezjavier.appfitness.composeapp.resources.activity_light_description
import io.github.lopezjavier.appfitness.composeapp.resources.activity_light_title
import io.github.lopezjavier.appfitness.composeapp.resources.activity_moderate_description
import io.github.lopezjavier.appfitness.composeapp.resources.activity_moderate_title
import io.github.lopezjavier.appfitness.composeapp.resources.activity_sedentary_description
import io.github.lopezjavier.appfitness.composeapp.resources.activity_sedentary_title
import io.github.lopezjavier.appfitness.composeapp.resources.activity_very_high_description
import io.github.lopezjavier.appfitness.composeapp.resources.activity_very_high_title
import io.github.lopezjavier.appfitness.composeapp.resources.ic_avatar_person
import io.github.lopezjavier.appfitness.composeapp.resources.ic_flag
import io.github.lopezjavier.appfitness.composeapp.resources.ic_flame_outlined
import io.github.lopezjavier.appfitness.composeapp.resources.ic_straighten
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_age
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_label_birth_date
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_label_height
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_label_sex
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_label_weight
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_name_label
import io.github.lopezjavier.appfitness.composeapp.resources.px_start_goal_kcal
import io.github.lopezjavier.appfitness.composeapp.resources.px_start_goal_split
import io.github.lopezjavier.appfitness.composeapp.resources.px_start_grams
import io.github.lopezjavier.appfitness.composeapp.resources.px_start_macro_carbs
import io.github.lopezjavier.appfitness.composeapp.resources.px_start_macro_fat
import io.github.lopezjavier.appfitness.composeapp.resources.px_start_macro_protein
import io.github.lopezjavier.appfitness.composeapp.resources.unit_cm
import io.github.lopezjavier.appfitness.composeapp.resources.unit_kg
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppTextField
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.DonutChart
import io.github.lopezjavier.appfitness.designsystem.DonutChartDefaults
import io.github.lopezjavier.appfitness.designsystem.DonutSlice
import io.github.lopezjavier.appfitness.designsystem.IconBadge
import io.github.lopezjavier.appfitness.designsystem.IconBadgeDefaults
import io.github.lopezjavier.appfitness.designsystem.LegendRow
import io.github.lopezjavier.appfitness.designsystem.SelectableCard
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.StartIntensityBars
import io.github.lopezjavier.appfitness.designsystem.WheelPickerDefaults
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.BiometricBounds
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.Sex
import io.github.lopezjavier.appfitness.domain.usecase.CalculateMacroTargets
import io.github.lopezjavier.appfitness.feature.biometrics.BirthDateWheels
import io.github.lopezjavier.appfitness.feature.biometrics.HeightWheel
import io.github.lopezjavier.appfitness.feature.biometrics.SexSelector
import io.github.lopezjavier.appfitness.feature.biometrics.WeightWheels
import io.github.lopezjavier.appfitness.feature.goals.EnergyStrategyUiState
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Step 1: the name (only when Google gave none), sex and birth date, each under its own label. */
@Composable
internal fun AboutYouStep(
    state: OnboardingUiState,
    onName: (String) -> Unit,
    onSelectSex: (Sex) -> Unit,
    onBirthDate: (LocalDate) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.blockGap)) {
        if (state.needsName) {
            AppTextField(
                value = state.name,
                onValueChange = onName,
                label = stringResource(Res.string.onboarding_name_label),
                maxLength = BiometricBounds.NAME_MAX_LENGTH,
                enabled = state.phase == OnboardingPhase.Content,
                testTagPrefix = "onboarding_about_name_field",
            )
        }
        FieldGroup(Res.string.onboarding_label_sex, "onboarding_label_sex") {
            SexSelector(state.sex, onSelectSex, tagPrefix = "onboarding")
        }
        FieldGroup(Res.string.onboarding_label_birth_date, "onboarding_label_birth_date") {
            BirthDateStep(state, onBirthDate)
        }
    }
}

/** A question label (`titleMedium`) with its control [Spacing.md] below. */
@Composable
private fun FieldGroup(label: StringResource, tag: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        FieldLabel(label, tag)
        content()
    }
}

@Composable
private fun FieldLabel(label: StringResource, tag: String, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(label),
        style = AppTheme.typography.titleMedium,
        color = AppTheme.colors.textPrimary,
        modifier = modifier.testTag(tag),
    )
}

@Composable
private fun BirthDateStep(state: OnboardingUiState, onChange: (LocalDate) -> Unit) {
    val age = BiometricBounds.ageOn(state.birthDate, state.today)
    Column {
        BirthDateWheels(state.birthDate, state.today, onChange, tagPrefix = "onboarding")
        Text(
            text = stringResource(Res.string.onboarding_age, age),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.padding(top = Spacing.md).semantics { liveRegion = LiveRegionMode.Polite }
                .testTag("onboarding_age_label"),
        )
    }
}

/**
 * Step 2: height and current weight side by side, each with its label, wheel(s) and the unit centred under the
 * wheel. When the width cannot hold two columns the two fields stack.
 */
@Composable
internal fun MeasuresStep(heightCm: Int, weightTenths: Int, onHeight: (Int) -> Unit, onWeight: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val height = @Composable { modifier: Modifier ->
            MeasureField(
                label = Res.string.onboarding_label_height,
                labelTag = "onboarding_label_height",
                unit = stringResource(Res.string.unit_cm),
                unitTag = "onboarding_height_unit",
                modifier = modifier,
            ) { HeightWheel(heightCm, onHeight, tagPrefix = "onboarding") }
        }
        val weight = @Composable { modifier: Modifier ->
            MeasureField(
                label = Res.string.onboarding_label_weight,
                labelTag = "onboarding_label_weight",
                unit = stringResource(Res.string.unit_kg),
                unitTag = "onboarding_weight_unit",
                modifier = modifier,
            ) { WeightWheels(weightTenths, onWeight, tagPrefix = "onboarding") }
        }
        if (maxWidth >= WheelPickerDefaults.SideBySideMinWidth) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.screenMargin)) {
                height(Modifier.weight(1f))
                weight(Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.blockGap)) {
                height(Modifier.fillMaxWidth())
                weight(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun MeasureField(
    label: StringResource,
    labelTag: String,
    unit: String,
    unitTag: String,
    modifier: Modifier = Modifier,
    wheels: @Composable () -> Unit,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        FieldLabel(label, labelTag, Modifier.align(Alignment.Start).padding(bottom = Spacing.md))
        wheels()
        Text(
            text = unit,
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(top = Spacing.xs).testTag(unitTag),
        )
    }
}

/** The hero mark before each step title: a lit icon badge, one colour per step. */
@Composable
internal fun StepMark(step: OnboardingStep) {
    val colors = AppTheme.colors
    val (icon, tint) = when (step) {
        OnboardingStep.AboutYou -> Res.drawable.ic_avatar_person to colors.neonLime
        OnboardingStep.Measures -> Res.drawable.ic_straighten to colors.athleticCyan
        OnboardingStep.Activity -> Res.drawable.ic_flame_outlined to colors.energyAmber
        OnboardingStep.Goals -> Res.drawable.ic_flag to colors.neonLime
    }
    IconBadge(icon, tint, size = IconBadgeDefaults.LargeSize, iconSize = IconBadgeDefaults.LargeIconSize)
}

private class ActivityOption(
    val level: ActivityLevel,
    val title: StringResource,
    val description: StringResource,
    val tagName: String,
)

private val ActivityOptions = listOf(
    ActivityOption(
        ActivityLevel.Sedentary,
        Res.string.activity_sedentary_title,
        Res.string.activity_sedentary_description,
        "sedentary",
    ),
    ActivityOption(
        ActivityLevel.Light,
        Res.string.activity_light_title,
        Res.string.activity_light_description,
        "light",
    ),
    ActivityOption(
        ActivityLevel.Moderate,
        Res.string.activity_moderate_title,
        Res.string.activity_moderate_description,
        "moderate",
    ),
    ActivityOption(ActivityLevel.High, Res.string.activity_high_title, Res.string.activity_high_description, "high"),
    ActivityOption(
        ActivityLevel.VeryHigh,
        Res.string.activity_very_high_title,
        Res.string.activity_very_high_description,
        "very_high",
    ),
)

/** The five activity levels as radio cards, each with 1 to 5 growing bars (same tags as the shared cards). */
@Composable
internal fun ActivityIntensityCards(
    selected: ActivityLevel?,
    onSelect: (ActivityLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Column(modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
        ActivityOptions.forEachIndexed { index, option ->
            val isSelected = selected == option.level
            SelectableCard(
                title = stringResource(option.title),
                description = stringResource(option.description),
                selected = isSelected,
                onClick = { onSelect(option.level) },
                modifier = Modifier.testTag("onboarding_activity_${option.tagName}"),
                leading = {
                    StartIntensityBars(index + 1, if (isSelected) colors.neonLime else colors.athleticCyan)
                },
            )
        }
    }
}

/**
 * The calculated goals as a result worth seeing: the daily target as a hero figure inside a donut of the default
 * macro split, with the grams of each macro beside it.
 */
@Composable
internal fun GoalsResult(energy: EnergyStrategyUiState, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val targets = CalculateMacroTargets(energy.targetKcal, MacroSplit.DEFAULT)
    AppCard(modifier.fillMaxWidth().testTag("onboarding_goals_result"), glow = colors.neonLime) {
        Text(
            text = stringResource(Res.string.px_start_goal_split),
            style = AppTheme.typography.titleSection,
            color = colors.textSecondary,
        )
        Row(
            Modifier.padding(top = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DonutChart(
                slices = listOf(
                    DonutSlice(targets.proteinKcal.toFloat(), colors.macroProtein),
                    DonutSlice(targets.carbsKcal.toFloat(), colors.macroCarbs),
                    DonutSlice(targets.fatKcal.toFloat(), colors.macroFat),
                ),
                size = DonutChartDefaults.Size * GOALS_DONUT_SCALE,
                strokeWidth = DonutChartDefaults.StrokeWidth,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = AppNumberFormat.integer(energy.targetKcal),
                        style = AppTheme.typography.headlineMetric,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = stringResource(Res.string.px_start_goal_kcal),
                        style = AppTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                LegendRow(
                    stringResource(Res.string.px_start_macro_protein),
                    stringResource(Res.string.px_start_grams, targets.proteinG),
                    colors.macroProtein,
                )
                LegendRow(
                    stringResource(Res.string.px_start_macro_carbs),
                    stringResource(Res.string.px_start_grams, targets.carbsG),
                    colors.macroCarbs,
                )
                LegendRow(
                    stringResource(Res.string.px_start_macro_fat),
                    stringResource(Res.string.px_start_grams, targets.fatG),
                    colors.macroFat,
                )
            }
        }
    }
}

/** The goals donut is drawn larger than the default one: it is the hero of the step. */
private const val GOALS_DONUT_SCALE = 1.3f
