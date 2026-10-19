package io.github.lopezjavier.appfitness.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_age
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_decimal_separator
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_label_birth_date
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_label_height
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_label_sex
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_label_weight
import io.github.lopezjavier.appfitness.composeapp.resources.unit_cm
import io.github.lopezjavier.appfitness.composeapp.resources.unit_kg
import io.github.lopezjavier.appfitness.composeapp.resources.wheel_weight_decimal_cd
import io.github.lopezjavier.appfitness.composeapp.resources.wheel_weight_kg_cd
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WheelPicker
import io.github.lopezjavier.appfitness.designsystem.WheelPickerDefaults
import io.github.lopezjavier.appfitness.domain.model.BiometricBounds
import io.github.lopezjavier.appfitness.domain.model.Sex
import io.github.lopezjavier.appfitness.feature.biometrics.BirthDateWheels
import io.github.lopezjavier.appfitness.feature.biometrics.HeightWheel
import io.github.lopezjavier.appfitness.feature.biometrics.SexSelector
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private const val DECIMAL_MAX = 9
private const val TENTHS_PER_KG = 10

/** Step 1: sex and birth date, each under its own label. */
@Composable
internal fun AboutYouStep(state: OnboardingUiState, onSelectSex: (Sex) -> Unit, onBirthDate: (LocalDate) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.blockGap)) {
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
            ) { WeightWheels(weightTenths, onWeight) }
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

@Composable
private fun WeightWheels(weightTenths: Int, onChange: (Int) -> Unit) {
    val kilos = weightTenths / TENTHS_PER_KG
    val decimal = weightTenths % TENTHS_PER_KG
    val maxKilos = BiometricBounds.WEIGHT_TENTHS.last / TENTHS_PER_KG
    val kg = stringResource(Res.string.unit_kg)
    val separator = stringResource(Res.string.onboarding_decimal_separator)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        WheelPicker(
            range = (BiometricBounds.WEIGHT_TENTHS.first / TENTHS_PER_KG)..maxKilos,
            value = kilos,
            onValueChange = { onChange(it * TENTHS_PER_KG + decimal) },
            label = { it.toString() },
            contentDescription = stringResource(Res.string.wheel_weight_kg_cd),
            stateDescription = { "$it $kg" },
            testTagPrefix = "onboarding_weight_kg_picker",
            modifier = Modifier.width(WheelPickerDefaults.MinWidth),
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
            stateDescription = { "$separator$it" },
            testTagPrefix = "onboarding_weight_decimal_picker",
            modifier = Modifier.width(WheelPickerDefaults.CompactWidth),
        )
    }
}
