package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_calc_bar
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_calc_bar_10
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_calc_bar_15
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_calc_bar_20
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_calc_bar_7_5
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_calc_derived_rounding
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_calc_dumbbell_handle
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_calc_dumbbell_handle_decrease_cd
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_calc_dumbbell_handle_increase_cd
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_calc_dumbbell_handle_state
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_calc_plate_cd
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_calc_plates
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_calc_plates_error
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_calc_rounding_value
import io.github.lopezjavier.appfitness.designsystem.AppOption
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.AppToggleChip
import io.github.lopezjavier.appfitness.designsystem.NumberStepper
import io.github.lopezjavier.appfitness.designsystem.SegmentedSelector
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.CalculatorSettings
import io.github.lopezjavier.appfitness.domain.usecase.GhostData
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private const val ROUNDING_STEP_KG = 0.5

/**
 * content of the "Calculadoras" card (title and "?" are its header, C-168): dumbbell handle, default bar, available plates and the derived rounding
 * (read-only, twice the smallest plate; ADR-56). All of it is a draft until "Guardar"; switching off the last plate
 * is refused and shows [platesError].
 */
@Composable
internal fun CalculatorsBlock(
    settings: CalculatorSettings,
    platesError: Boolean,
    onEvent: (PreferencesEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        RoundingRow(
            label = stringResource(Res.string.preferences_calc_dumbbell_handle),
            kg = settings.dumbbellHandleKg,
            state = Res.string.preferences_calc_dumbbell_handle_state,
            decreaseCd = stringResource(Res.string.preferences_calc_dumbbell_handle_decrease_cd),
            increaseCd = stringResource(Res.string.preferences_calc_dumbbell_handle_increase_cd),
            tag = "preferences_dumbbell_handle",
            onChange = { onEvent(PreferencesEvent.SetDumbbellHandle(it)) },
        )
        BarSelector(settings.barWeightKg) { onEvent(PreferencesEvent.SetBarWeight(it)) }
        PlateChips(settings.plateKg, platesError) { kg, on -> onEvent(PreferencesEvent.SetPlateEnabled(kg, on)) }
        Text(
            text = stringResource(
                Res.string.preferences_calc_derived_rounding,
                GhostData.weightText(settings.roundingKg),
            ),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.testTag("preferences_derived_rounding"),
        )
    }
}

@Composable
@Suppress("LongParameterList") // Label, value, three texts, tag and callback of one row.
private fun RoundingRow(
    label: String,
    kg: Double,
    state: StringResource,
    decreaseCd: String,
    increaseCd: String,
    tag: String,
    onChange: (Double) -> Unit,
) {
    val text = GhostData.weightText(kg)
    // The stepper moves to its own line when the label wraps (R-83).
    var stacked by remember { mutableStateOf(false) }
    val stepper: @Composable () -> Unit = {
        NumberStepper(
            valueText = stringResource(Res.string.preferences_calc_rounding_value, text),
            onDecrement = { onChange(kg - ROUNDING_STEP_KG) },
            onIncrement = { onChange(kg + ROUNDING_STEP_KG) },
            decrementEnabled = kg > MIN_ROUNDING_KG,
            incrementEnabled = kg < MAX_ROUNDING_KG,
            decrementContentDescription = decreaseCd,
            incrementContentDescription = increaseCd,
            stateDescription = stringResource(state, text),
            modifier = Modifier.testTag(tag),
            testTagPrefix = tag,
        )
    }
    val labelText: @Composable (Modifier) -> Unit = { labelModifier ->
        Text(
            text = label,
            style = AppTheme.typography.bodyLarge,
            color = AppTheme.colors.textPrimary,
            modifier = labelModifier,
            onTextLayout = { if (it.lineCount > 1) stacked = true },
        )
    }
    if (stacked) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            labelText(Modifier)
            stepper()
        }
    } else {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            labelText(Modifier.weight(1f))
            stepper()
        }
    }
}

@Composable
private fun BarSelector(barKg: Double, onSelect: (Double) -> Unit) {
    val bars = CalculatorSettings.BARS_KG
    val options = listOf(
        AppOption(stringResource(Res.string.preferences_calc_bar_20), "preferences_bar_default_20"),
        AppOption(stringResource(Res.string.preferences_calc_bar_15), "preferences_bar_default_15"),
        AppOption(stringResource(Res.string.preferences_calc_bar_10), "preferences_bar_default_10"),
        AppOption(stringResource(Res.string.preferences_calc_bar_7_5), "preferences_bar_default_7_5"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = stringResource(Res.string.preferences_calc_bar),
            style = AppTheme.typography.bodyLarge,
            color = AppTheme.colors.textPrimary,
        )
        SegmentedSelector(
            options = options,
            selectedIndex = bars.indexOf(barKg).takeIf { it >= 0 },
            onSelect = { onSelect(bars[it]) },
            modifier = Modifier.testTag("preferences_bar_default"),
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class) // FlowRow wraps the ten chips.
private fun PlateChips(enabled: List<Double>, showError: Boolean, onToggle: (Double, Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = stringResource(Res.string.preferences_calc_plates),
            style = AppTheme.typography.bodyLarge,
            color = AppTheme.colors.textPrimary,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            CalculatorSettings.PLATE_CATALOGUE.forEach { kg ->
                val text = GhostData.weightText(kg)
                AppToggleChip(
                    label = text,
                    checked = kg in enabled,
                    onCheckedChange = { onToggle(kg, it) },
                    contentDescription = stringResource(Res.string.preferences_calc_plate_cd, text),
                    modifier = Modifier.testTag("preferences_plate_${text.replace(',', '_')}"),
                )
            }
        }
        if (showError) {
            Text(
                text = stringResource(Res.string.preferences_calc_plates_error),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.error,
                modifier = Modifier.testTag("preferences_plates_error"),
            )
        }
    }
}
