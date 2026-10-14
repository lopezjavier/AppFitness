package io.github.lopezjavier.appfitness.feature.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_checkbox_cd
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_checkbox_checked
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_checkbox_unchecked
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_number_grams
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_number_reps
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_number_weight
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_number_input
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_number_stepper
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_set_checkbox
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_set_rows
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_set_previous_1
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_set_previous_2
import io.github.lopezjavier.appfitness.composeapp.resources.energy_adjust_decrease_cd
import io.github.lopezjavier.appfitness.composeapp.resources.energy_adjust_increase_cd
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.NumberInput
import io.github.lopezjavier.appfitness.designsystem.NumberStepper
import io.github.lopezjavier.appfitness.designsystem.SetCompletionCheckbox
import io.github.lopezjavier.appfitness.designsystem.SetRow
import io.github.lopezjavier.appfitness.designsystem.SetRowDefaults
import io.github.lopezjavier.appfitness.designsystem.SetRowValues
import io.github.lopezjavier.appfitness.designsystem.SetTableHeader
import io.github.lopezjavier.appfitness.designsystem.Spacing
import org.jetbrains.compose.resources.stringResource

private const val WEIGHT_PLACEHOLDER = "85"
private const val REPS_PLACEHOLDER = "8"
private const val GRAMS_DEFAULT = "150"
private const val GRAMS_MAX_DECIMALS = 1
private const val GRAMS_MAX_INTEGER_DIGITS = 4
private const val COMPLETED_ROW_WEIGHT = "60"
private const val COMPLETED_ROW_REPS = "12"
private const val THIRD_SET = 3
private const val STEPPER_START = -400
private const val STEPPER_STEP = 50
private const val STEPPER_MIN = -1000
private const val STEPPER_MAX = -50

/** Catalog sections of the workout input components (T-025). */
internal fun LazyListScope.workoutInputItems() {
    item { NumberInputSection() }
    item { NumberStepperSection() }
    item { SetCheckboxSection() }
    item { SetRowsSection() }
    restTimerItems()
}

@Composable
private fun NumberInputSection() {
    var weight by rememberSaveable { mutableStateOf("") }
    var reps by rememberSaveable { mutableStateOf("") }
    var grams by rememberSaveable { mutableStateOf(GRAMS_DEFAULT) }
    CatalogSection(stringResource(Res.string.catalog_section_number_input), "catalog_section_number_input") {
        AppCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                NumberInput(
                    value = weight,
                    onValueChange = { weight = it },
                    label = stringResource(Res.string.catalog_number_weight),
                    modifier = Modifier.testTag("catalog_number_input_weight"),
                    placeholder = WEIGHT_PLACEHOLDER,
                    maxDecimals = SetRowDefaults.WEIGHT_MAX_DECIMALS,
                    maxIntegerDigits = SetRowDefaults.WEIGHT_MAX_INTEGER_DIGITS,
                )
                NumberInput(
                    value = reps,
                    onValueChange = { reps = it },
                    label = stringResource(Res.string.catalog_number_reps),
                    modifier = Modifier.testTag("catalog_number_input_reps"),
                    placeholder = REPS_PLACEHOLDER,
                    maxDecimals = SetRowDefaults.REPS_MAX_DECIMALS,
                    maxIntegerDigits = SetRowDefaults.REPS_MAX_INTEGER_DIGITS,
                )
                NumberInput(
                    value = grams,
                    onValueChange = { grams = it },
                    label = stringResource(Res.string.catalog_number_grams),
                    modifier = Modifier.testTag("catalog_number_input_grams"),
                    maxDecimals = GRAMS_MAX_DECIMALS,
                    maxIntegerDigits = GRAMS_MAX_INTEGER_DIGITS,
                )
            }
        }
    }
}

@Composable
private fun NumberStepperSection() {
    var value by rememberSaveable { mutableStateOf(STEPPER_START) }
    val valueText = AppNumberFormat.signedInteger(value) + " kcal"
    CatalogSection(stringResource(Res.string.catalog_section_number_stepper), "catalog_section_number_stepper") {
        AppCard(Modifier.fillMaxWidth()) {
            NumberStepper(
                valueText = valueText,
                onDecrement = { value = (value - STEPPER_STEP).coerceAtLeast(STEPPER_MIN) },
                onIncrement = { value = (value + STEPPER_STEP).coerceAtMost(STEPPER_MAX) },
                decrementEnabled = value > STEPPER_MIN,
                incrementEnabled = value < STEPPER_MAX,
                decrementContentDescription = stringResource(Res.string.energy_adjust_decrease_cd),
                incrementContentDescription = stringResource(Res.string.energy_adjust_increase_cd),
                stateDescription = valueText,
                testTagPrefix = "catalog_number_stepper",
            )
        }
    }
}

@Composable
private fun SetCheckboxSection() {
    var unchecked by rememberSaveable { mutableStateOf(false) }
    var checked by rememberSaveable { mutableStateOf(true) }
    val description = stringResource(Res.string.catalog_checkbox_cd)
    CatalogSection(stringResource(Res.string.catalog_section_set_checkbox), "catalog_section_set_checkbox") {
        AppCard(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xl),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VariantCaption(stringResource(Res.string.catalog_checkbox_unchecked))
                SetCompletionCheckbox(
                    checked = unchecked,
                    onCheckedChange = { unchecked = it },
                    contentDescription = description,
                    modifier = Modifier.testTag("catalog_set_checkbox_unchecked"),
                )
                VariantCaption(stringResource(Res.string.catalog_checkbox_checked))
                SetCompletionCheckbox(
                    checked = checked,
                    onCheckedChange = { checked = it },
                    contentDescription = description,
                    modifier = Modifier.testTag("catalog_set_checkbox_checked"),
                )
            }
        }
    }
}

@Composable
private fun SetRowsSection() {
    var done1 by rememberSaveable { mutableStateOf(true) }
    var weight1 by rememberSaveable { mutableStateOf(COMPLETED_ROW_WEIGHT) }
    var reps1 by rememberSaveable { mutableStateOf(COMPLETED_ROW_REPS) }
    var done2 by rememberSaveable { mutableStateOf(false) }
    var weight2 by rememberSaveable { mutableStateOf("") }
    var reps2 by rememberSaveable { mutableStateOf("") }
    var done3 by rememberSaveable { mutableStateOf(false) }
    var weight3 by rememberSaveable { mutableStateOf("") }
    var reps3 by rememberSaveable { mutableStateOf("") }
    CatalogSection(stringResource(Res.string.catalog_section_set_rows), "catalog_section_set_rows") {
        AppCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(SetRowDefaults.RowGap)) {
                SetTableHeader(Modifier.testTag("catalog_set_header"))
                SetRow(
                    values =
                    SetRowValues(1, stringResource(Res.string.catalog_set_previous_1), weight1, reps1, done1),
                    onWeightChange = { weight1 = it },
                    onRepsChange = { reps1 = it },
                    onCompletedChange = { done1 = it },
                    testTagPrefix = "catalog_set_row_1",
                )
                SetRow(
                    values =
                    SetRowValues(
                        index = 2,
                        previous = stringResource(Res.string.catalog_set_previous_2),
                        weight = weight2,
                        reps = reps2,
                        completed = done2,
                        weightPlaceholder = WEIGHT_PLACEHOLDER,
                        repsPlaceholder = REPS_PLACEHOLDER,
                    ),
                    onWeightChange = { weight2 = it },
                    onRepsChange = { reps2 = it },
                    onCompletedChange = { done2 = it },
                    onUsePrevious = {
                        weight2 = WEIGHT_PLACEHOLDER
                        reps2 = REPS_PLACEHOLDER
                    },
                    testTagPrefix = "catalog_set_row_2",
                )
                SetRow(
                    values = SetRowValues(
                        index = THIRD_SET,
                        previous = null,
                        weight = weight3,
                        reps = reps3,
                        completed = done3,
                    ),
                    onWeightChange = { weight3 = it },
                    onRepsChange = { reps3 = it },
                    onCompletedChange = { done3 = it },
                    testTagPrefix = "catalog_set_row_3",
                )
            }
        }
    }
}
