package io.github.lopezjavier.appfitness.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.semantics.contentDescription
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
import io.github.lopezjavier.appfitness.composeapp.resources.month_1
import io.github.lopezjavier.appfitness.composeapp.resources.month_10
import io.github.lopezjavier.appfitness.composeapp.resources.month_11
import io.github.lopezjavier.appfitness.composeapp.resources.month_12
import io.github.lopezjavier.appfitness.composeapp.resources.month_2
import io.github.lopezjavier.appfitness.composeapp.resources.month_3
import io.github.lopezjavier.appfitness.composeapp.resources.month_4
import io.github.lopezjavier.appfitness.composeapp.resources.month_5
import io.github.lopezjavier.appfitness.composeapp.resources.month_6
import io.github.lopezjavier.appfitness.composeapp.resources.month_7
import io.github.lopezjavier.appfitness.composeapp.resources.month_8
import io.github.lopezjavier.appfitness.composeapp.resources.month_9
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_age
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_decimal_separator
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_sex_female
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_sex_male
import io.github.lopezjavier.appfitness.composeapp.resources.unit_cm
import io.github.lopezjavier.appfitness.composeapp.resources.unit_kg
import io.github.lopezjavier.appfitness.composeapp.resources.wheel_day_cd
import io.github.lopezjavier.appfitness.composeapp.resources.wheel_height_cd
import io.github.lopezjavier.appfitness.composeapp.resources.wheel_month_cd
import io.github.lopezjavier.appfitness.composeapp.resources.wheel_weight_decimal_cd
import io.github.lopezjavier.appfitness.composeapp.resources.wheel_weight_kg_cd
import io.github.lopezjavier.appfitness.composeapp.resources.wheel_year_cd
import io.github.lopezjavier.appfitness.designsystem.AppOption
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.SegmentedSelector
import io.github.lopezjavier.appfitness.designsystem.SelectableCard
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WheelPicker
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.BiometricBounds
import io.github.lopezjavier.appfitness.domain.model.Sex
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private const val MONTH_COUNT = 12
private const val DECIMAL_MAX = 9
private const val TENTHS_PER_KG = 10
private const val DAY_WHEEL_WEIGHT = 1f
private const val MONTH_WHEEL_WEIGHT = 2f
private const val YEAR_WHEEL_WEIGHT = 1.4f

@Composable
internal fun SexStep(sex: Sex?, onSelect: (Sex) -> Unit) {
    val options =
        listOf(
            AppOption(stringResource(Res.string.onboarding_sex_male), "onboarding_sex_male"),
            AppOption(stringResource(Res.string.onboarding_sex_female), "onboarding_sex_female"),
        )
    SegmentedSelector(
        options = options,
        selectedIndex = sex?.ordinal,
        onSelect = { onSelect(Sex.entries[it]) },
    )
}

@Composable
internal fun BirthDateStep(state: OnboardingUiState, onChange: (LocalDate) -> Unit) {
    val date = state.birthDate
    val range = BiometricBounds.birthDateRange(state.today)
    val monthNames = monthNames()
    val age = BiometricBounds.ageOn(date, state.today)
    fun compose(year: Int = date.year, month: Int = date.month.number, day: Int = date.day) =
        onChange(BiometricBounds.composeBirthDate(year, month, day, state.today))
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            WheelPicker(
                range = 1..BiometricBounds.daysIn(date.year, date.month.number),
                value = date.day,
                onValueChange = { compose(day = it) },
                label = { it.toString() },
                contentDescription = stringResource(Res.string.wheel_day_cd),
                stateDescription = { it.toString() },
                testTagPrefix = "onboarding_birth_day_picker",
                modifier = Modifier.weight(DAY_WHEEL_WEIGHT),
            )
            WheelPicker(
                range = 1..MONTH_COUNT,
                value = date.month.number,
                onValueChange = { compose(month = it) },
                label = { monthNames[it - 1] },
                contentDescription = stringResource(Res.string.wheel_month_cd),
                stateDescription = { monthNames[it - 1] },
                testTagPrefix = "onboarding_birth_month_picker",
                modifier = Modifier.weight(MONTH_WHEEL_WEIGHT),
            )
            WheelPicker(
                range = range.start.year..range.endInclusive.year,
                value = date.year,
                onValueChange = { compose(year = it) },
                label = { it.toString() },
                contentDescription = stringResource(Res.string.wheel_year_cd),
                stateDescription = { it.toString() },
                testTagPrefix = "onboarding_birth_year_picker",
                modifier = Modifier.weight(YEAR_WHEEL_WEIGHT),
            )
        }
        Text(
            text = stringResource(Res.string.onboarding_age, age),
            style = AppTheme.typography.bodyLarge,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.padding(top = Spacing.blockGap).semantics { liveRegion = LiveRegionMode.Polite }
                .testTag("onboarding_age_label"),
        )
    }
}

@Composable
private fun monthNames(): List<String> = listOf(
    stringResource(Res.string.month_1),
    stringResource(Res.string.month_2),
    stringResource(Res.string.month_3),
    stringResource(Res.string.month_4),
    stringResource(Res.string.month_5),
    stringResource(Res.string.month_6),
    stringResource(Res.string.month_7),
    stringResource(Res.string.month_8),
    stringResource(Res.string.month_9),
    stringResource(Res.string.month_10),
    stringResource(Res.string.month_11),
    stringResource(Res.string.month_12),
)

@Composable
internal fun HeightStep(heightCm: Int, onChange: (Int) -> Unit) {
    val spoken = stringResource(Res.string.wheel_state_cm, heightCm)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
        WheelPicker(
            range = BiometricBounds.HEIGHT_CM,
            value = heightCm,
            onValueChange = onChange,
            label = { it.toString() },
            contentDescription = stringResource(Res.string.wheel_height_cd),
            stateDescription = { spoken },
            testTagPrefix = "onboarding_height_picker",
        )
        UnitLabel(stringResource(Res.string.unit_cm))
    }
}

@Composable
internal fun WeightStep(weightTenths: Int, onChange: (Int) -> Unit) {
    val kilos = weightTenths / TENTHS_PER_KG
    val decimal = weightTenths % TENTHS_PER_KG
    val maxKilos = BiometricBounds.WEIGHT_TENTHS.last / TENTHS_PER_KG
    val spokenKilos = stringResource(Res.string.wheel_state_kg, kilos)
    val spokenDecimal = stringResource(Res.string.wheel_state_decimal, decimal)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        WheelPicker(
            range = (BiometricBounds.WEIGHT_TENTHS.first / TENTHS_PER_KG)..maxKilos,
            value = kilos,
            onValueChange = { onChange(it * TENTHS_PER_KG + decimal) },
            label = { it.toString() },
            contentDescription = stringResource(Res.string.wheel_weight_kg_cd),
            stateDescription = { spokenKilos },
            testTagPrefix = "onboarding_weight_kg_picker",
        )
        Text(
            text = stringResource(Res.string.onboarding_decimal_separator),
            style = AppTheme.typography.labelNumeric,
            color = AppTheme.colors.textPrimary,
        )
        WheelPicker(
            range = 0..(if (kilos == maxKilos) 0 else DECIMAL_MAX),
            value = decimal,
            onValueChange = { onChange(kilos * TENTHS_PER_KG + it) },
            label = { it.toString() },
            contentDescription = stringResource(Res.string.wheel_weight_decimal_cd),
            stateDescription = { spokenDecimal },
            testTagPrefix = "onboarding_weight_decimal_picker",
        )
        UnitLabel(stringResource(Res.string.unit_kg))
    }
}

@Composable
private fun UnitLabel(text: String) {
    Text(text = text, style = AppTheme.typography.bodyLarge, color = AppTheme.colors.textSecondary)
}

private class ActivityOption(
    val level: ActivityLevel,
    val title: StringResource,
    val description: StringResource,
    val tag: String,
)

private val ActivityOptions =
    listOf(
        ActivityOption(
            ActivityLevel.Sedentary,
            Res.string.activity_sedentary_title,
            Res.string.activity_sedentary_description,
            "onboarding_activity_sedentary",
        ),
        ActivityOption(
            ActivityLevel.Light,
            Res.string.activity_light_title,
            Res.string.activity_light_description,
            "onboarding_activity_light",
        ),
        ActivityOption(
            ActivityLevel.Moderate,
            Res.string.activity_moderate_title,
            Res.string.activity_moderate_description,
            "onboarding_activity_moderate",
        ),
        ActivityOption(
            ActivityLevel.High,
            Res.string.activity_high_title,
            Res.string.activity_high_description,
            "onboarding_activity_high",
        ),
        ActivityOption(
            ActivityLevel.VeryHigh,
            Res.string.activity_very_high_title,
            Res.string.activity_very_high_description,
            "onboarding_activity_very_high",
        ),
    )

@Composable
internal fun ActivityStep(selected: ActivityLevel?, onSelect: (ActivityLevel) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
        ActivityOptions.forEach { option ->
            SelectableCard(
                title = stringResource(option.title),
                description = stringResource(option.description),
                selected = selected == option.level,
                onClick = { onSelect(option.level) },
                modifier = Modifier.testTag(option.tag),
            )
        }
    }
}
