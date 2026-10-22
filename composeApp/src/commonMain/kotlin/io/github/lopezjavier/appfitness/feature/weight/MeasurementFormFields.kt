package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.weight_field_fat
import io.github.lopezjavier.appfitness.composeapp.resources.weight_field_fat_placeholder
import io.github.lopezjavier.appfitness.composeapp.resources.weight_field_weight
import io.github.lopezjavier.appfitness.composeapp.resources.weight_range_fat
import io.github.lopezjavier.appfitness.composeapp.resources.weight_range_weight
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.NumberInput
import io.github.lopezjavier.appfitness.designsystem.Spacing
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

private const val WEIGHT_INTEGER_DIGITS = 3
private const val FAT_INTEGER_DIGITS = 2

/**
 * The manual weight form: weight and body fat, and the range hint of an invalid field. The typed
 * texts live here too (seeded from [form] when the form appears) because [NumberInput] needs its value updated
 * synchronously; every edit is also sent to the view model.
 */
@Composable
internal fun MeasurementFormFields(day: LocalDate, form: MeasurementForm, onEvent: (WeightEvent) -> Unit) {
    var weight by remember(day, form.existingId) { mutableStateOf(form.weightText) }
    var fat by remember(day, form.existingId) { mutableStateOf(form.fatText) }
    val weightLabel = stringResource(Res.string.weight_field_weight)
    val fatLabel = stringResource(Res.string.weight_field_fat)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            LabeledNumberInput(
                label = weightLabel,
                labelTag = "weight_sheet_weight_label",
                modifier = Modifier.weight(1f),
            ) {
                NumberInput(
                    value = weight,
                    onValueChange = {
                        weight = it
                        onEvent(WeightEvent.SetWeight(it))
                    },
                    label = weightLabel,
                    modifier = Modifier.fillMaxWidth().testTag("weight_sheet_weight_input"),
                    maxDecimals = 1,
                    maxIntegerDigits = WEIGHT_INTEGER_DIGITS,
                    showLabel = false,
                )
            }
            LabeledNumberInput(
                label = fatLabel,
                labelTag = "weight_sheet_fat_label",
                modifier = Modifier.weight(1f),
            ) {
                NumberInput(
                    value = fat,
                    onValueChange = {
                        fat = it
                        onEvent(WeightEvent.SetBodyFat(it))
                    },
                    label = fatLabel,
                    modifier = Modifier.fillMaxWidth().testTag("weight_sheet_fat_input"),
                    placeholder = stringResource(Res.string.weight_field_fat_placeholder),
                    maxDecimals = 1,
                    maxIntegerDigits = FAT_INTEGER_DIGITS,
                    showLabel = false,
                )
            }
        }
        RangeHint(form)
    }
}

/** A one-line label above its field, so side-by-side fields share the same top whatever the font scale. */
@Composable
private fun LabeledNumberInput(
    label: String,
    labelTag: String,
    modifier: Modifier = Modifier,
    input: @Composable () -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = label.uppercase(),
            style = AppTheme.typography.labelTechnical,
            color = AppTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(labelTag),
        )
        input()
    }
}

/** The allowed range of every invalid field, in one block (blank fields report no problem). */
@Composable
private fun RangeHint(form: MeasurementForm) {
    val lines = listOfNotNull(
        if (form.weightProblem != null) stringResource(Res.string.weight_range_weight) else null,
        if (form.fatProblem != null) stringResource(Res.string.weight_range_fat) else null,
    )
    if (lines.isNotEmpty()) {
        Text(
            text = lines.joinToString("\n"),
            style = AppTheme.typography.bodySmall,
            color = AppTheme.colors.error,
            modifier = Modifier.testTag("weight_sheet_range_hint"),
        )
    }
}
